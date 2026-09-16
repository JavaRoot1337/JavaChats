package ru.javaroot.javachats.aihelper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.javaroot.JavaChat;
import ru.javaroot.javachats.api.ModerationResult;
import ru.javaroot.javachats.api.ModerationService;
import ru.javaroot.javachats.config.RuntimeConfig;
import ru.javaroot.javachats.runtime.ServerScheduler;
import ru.javaroot.javachats.utils.TextUtil;
import ru.javaroot.javachats.utils.LogVars;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AiMod implements ModerationService {
    private static final int MAX_MESSAGE_LENGTH = 4096;
    private final JavaChat plugin;
    private volatile AiRules rules;
    private final ServerScheduler scheduler;
    private volatile Semaphore moderationQueue = new Semaphore(1);
    private volatile RuntimeConfig.Ai aiConfig;
    private volatile MistralApi mistralApi;
    private volatile GroqApi groqApi;
    private final ScheduledExecutorService timeoutExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "JavaChats-ai-timeout");
        thread.setDaemon(true);
        return thread;
    });

    public AiMod(JavaChat plugin, ServerScheduler scheduler) {
        this.plugin = plugin;
        this.rules = new AiRules(plugin, plugin.getDataFolder());
        this.scheduler = scheduler;
    }

    public boolean reload(RuntimeConfig config, File profileDirectory) {
        AiRules nextRules = new AiRules(plugin, profileDirectory);
        try {
            nextRules.load(config.ai().systemPrompt());
        } catch (RuntimeException error) {
            plugin.getLogs().warning("config-validation", LogVars.of("error", String.valueOf(error.getMessage())));
            return false;
        }

        RuntimeConfig.Provider mistral = config.ai().mistral();
        MistralApi nextMistral = null;
        String mistralKey = mistral.apiKey();
        if (isConfigured(mistral, mistralKey)) {
            try {
                nextMistral = new MistralApi(plugin, mistral, mistralKey);
            } catch (RuntimeException error) {
                plugin.getLogs().warning("mistral-config", LogVars.of("error", String.valueOf(error.getMessage())));
                return false;
            }
        }

        RuntimeConfig.Provider groq = config.ai().groq();
        GroqApi nextGroq = null;
        String groqKey = groq.apiKey();
        if (isConfigured(groq, groqKey)) {
            try {
                nextGroq = new GroqApi(plugin, groq, groqKey);
            } catch (RuntimeException error) {
                if (nextMistral != null) {
                    nextMistral.close();
                }
                plugin.getLogs().warning("groq-config", LogVars.of("error", String.valueOf(error.getMessage())));
                return false;
            }
        }
        MistralApi oldMistral = mistralApi;
        GroqApi oldGroq = groqApi;
        rules = nextRules;
        aiConfig = config.ai();
        mistralApi = nextMistral;
        groqApi = nextGroq;
        moderationQueue = new Semaphore(config.ai().blockMaxQueueSize());
        if (oldMistral != null) {
            oldMistral.close();
        }
        if (oldGroq != null) {
            oldGroq.close();
        }
        return true;
    }

    @Override
    public CompletableFuture<ModerationResult> moderate(UUID playerId, String msg) {
        if (playerId == null || msg == null) {
            CompletableFuture<ModerationResult> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalArgumentException("playerId and message are required"));
            return failed;
        }
        if (msg.length() > MAX_MESSAGE_LENGTH) {
            CompletableFuture<ModerationResult> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalArgumentException("message is too long"));
            return failed;
        }
        RuntimeConfig.Ai cfg = aiConfig;
        if (cfg == null) {
            return CompletableFuture.completedFuture(ModerationResult.clean());
        }

        Semaphore queue = moderationQueue;
        if (!queue.tryAcquire()) {
            plugin.getLogs().warning("moderation-request", LogVars.of("error", "AI request queue is full"));
            return CompletableFuture.completedFuture(failureResult(cfg));
        }
        boolean useMistral = isModerationProvider(cfg.mistral(), mistralApi != null);
        boolean useGroq = isModerationProvider(cfg.groq(), groqApi != null);
        if (!useMistral && !useGroq) {
            queue.release();
            return CompletableFuture.completedFuture(ModerationResult.clean());
        }

        List<String> plus = rules.getTrainingPlus();
        List<String> minus = rules.getTrainingMinus();
        String systemPrompt = rules.getSystemPrompt();
        CompletableFuture<AiProviderClient.Result> mistral = useMistral
                ? delayed(cfg.blockInitialDelayTicks(),
                        () -> mistralApi.check(msg, rules.getRules(), plus, minus, cfg, systemPrompt))
                : CompletableFuture.completedFuture(null);
        CompletableFuture<AiProviderClient.Result> groq = useGroq
                ? delayed(cfg.blockInitialDelayTicks(),
                        () -> groqApi.check(msg, rules.getRules(), plus, minus, cfg, systemPrompt))
                : CompletableFuture.completedFuture(null);

        int activeProviders = (useMistral ? 1 : 0) + (useGroq ? 1 : 0);
        AtomicInteger failedProviders = new AtomicInteger();
        if (useMistral) {
            mistral = tolerateProviderFailure(mistral, "Mistral", failedProviders);
        }
        if (useGroq) {
            groq = tolerateProviderFailure(groq, "Groq", failedProviders);
        }
        final CompletableFuture<AiProviderClient.Result> mistralResult = mistral;
        final CompletableFuture<AiProviderClient.Result> groqResult = groq;
        CompletableFuture<ModerationResult> combined = CompletableFuture.allOf(mistral, groq)
                .thenApply(ignored -> {
                    AiProviderClient.Result mistralValue = mistralResult.join();
                    AiProviderClient.Result groqValue = groqResult.join();
                    boolean allFailed = activeProviders > 0
                            && (failedProviders.get() == activeProviders
                            || (mistralValue == null && groqValue == null));
                    return combineModeration(playerId, msg, mistralValue, groqValue, cfg, allFailed);
                })
                ;
        return withTimeout(combined, cfg.censorTimeoutSeconds(), mistral, groq)
                .handle((result, error) -> {
                    if (error == null) {
                        return result;
                    }
                    plugin.getLogs().warning("moderation-request", LogVars.of("error", rootMessage(error)));
                    return failureResult(cfg);
                }).thenApply(result -> {
                    logModeration(playerId, msg, result, cfg);
                    return result;
                }).whenComplete((result, error) -> queue.release());
    }

    private <T> CompletableFuture<T> tolerateProviderFailure(CompletableFuture<T> future, String provider,
            AtomicInteger failedProviders) {
        return future.handle((result, error) -> {
            if (error != null || result == null) {
                failedProviders.incrementAndGet();
                plugin.getLogs().warning("moderation-provider", LogVars.of(
                        "provider", provider,
                        "error", error == null ? "empty response" : rootMessage(error)));
            }
            return result;
        });
    }

    public CompletableFuture<String> censorIfViolation(UUID uuid, String msg) {
        return moderate(uuid, msg).thenApply(result -> result.censoredMessage().orElse(null));
    }

    public void punish(String playerName, ModerationResult result) {
        if (playerName == null || result == null || !result.violation() || result.rule() == null) {
            return;
        }
        scheduler.runServer(() -> {
            AiRules.RuleInfo info = rules.getRules().get(result.rule());
            if (info == null || info.punishCmd == null || info.punishCmd.isEmpty()) {
                return;
            }
            String command = info.punishCmd
                    .replace("%player%", playerName)
                    .replace("%rule%", info.id)
                    .replace("%probability%", String.valueOf(result.probability()));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        });
    }

    private ModerationResult combineModeration(UUID playerId, String msg, AiProviderClient.Result mistral,
            AiProviderClient.Result groq, RuntimeConfig.Ai cfg, boolean allProvidersFailed) {
        if (allProvidersFailed) {
            return failureResult(cfg);
        }
        String censored = msg;
        String rule = null;
        double probability = 0.0;
        boolean violation = false;
        boolean blockingViolation = false;
        List<String> badWords = new ArrayList<>();
        if (isPunished(mistral, cfg.mistral().punishProbability())) {
            violation = true;
            blockingViolation = "block".equalsIgnoreCase(cfg.mistral().mode());
            rule = mistral.rule();
            probability = mistral.probability();
            if (mistral.badWords() != null) {
                badWords.addAll(mistral.badWords());
            }
            censored = censorResult(censored, mistral.badWords());
        }
        if (isPunished(groq, cfg.groq().punishProbability())) {
            violation = true;
            blockingViolation = blockingViolation || "block".equalsIgnoreCase(cfg.groq().mode());
            if (groq.probability() > probability) {
                rule = groq.rule();
                probability = groq.probability();
            }
            if (groq.badWords() != null) {
                badWords.addAll(groq.badWords());
            }
            censored = censorResult(censored, groq.badWords());
        }
        if (violation) {
            showCensorTitle(playerId, cfg.censorTitle());
            return new ModerationResult(true, probability, rule, badWords, censored, true, blockingViolation);
        }
        return ModerationResult.clean();
    }

    private ModerationResult failureResult(RuntimeConfig.Ai cfg) {
        if (cfg.failurePolicy() == RuntimeConfig.Ai.FailurePolicy.BLOCK) {
            return ModerationResult.unavailable();
        }
        return ModerationResult.clean();
    }

    private boolean isConfigured(RuntimeConfig.Provider provider, String key) {
        return provider.enabled() && key != null && !key.trim().isEmpty();
    }

    private <T> CompletableFuture<T> delayed(long delayTicks, Supplier<CompletableFuture<T>> request) {
        if (delayTicks == 0) {
            return request.get();
        }
        CompletableFuture<T> result = new CompletableFuture<T>();
        AtomicReference<CompletableFuture<T>> requestFuture = new AtomicReference<CompletableFuture<T>>();
        ScheduledFuture<?> delayed = timeoutExecutor.schedule(() -> {
            if (result.isCancelled()) {
                return;
            }
            try {
                CompletableFuture<T> current = request.get();
                requestFuture.set(current);
                if (result.isCancelled()) {
                    current.cancel(true);
                    return;
                }
                current.whenComplete((value, error) -> {
                    if (error == null) {
                        result.complete(value);
                    } else {
                        result.completeExceptionally(error);
                    }
                });
            } catch (RuntimeException error) {
                result.completeExceptionally(error);
            }
        }, delayTicks * 50L, TimeUnit.MILLISECONDS);
        result.whenComplete((value, error) -> {
            if (result.isCancelled()) {
                delayed.cancel(false);
                CompletableFuture<T> current = requestFuture.get();
                if (current != null) {
                    current.cancel(true);
                }
            }
        });
        return result;
    }

    private boolean isModerationProvider(RuntimeConfig.Provider provider, boolean available) {
        return available && provider.enabled()
                && ("censor".equalsIgnoreCase(provider.mode()) || "block".equalsIgnoreCase(provider.mode()));
    }

    private boolean isPunished(AiProviderClient.Result result, double threshold) {
        return result != null && result.violation() && result.probability() >= threshold;
    }

    private String censorResult(String message, List<String> badWords) {
        if (badWords == null || badWords.isEmpty()) {
            return censorMessage(message);
        }
        String result = message;
        for (String badWord : badWords) {
            result = censor(result, badWord);
        }
        return result;
    }

    static String censor(String source, String target) {
        if (target == null || target.isEmpty()) {
            return source;
        }
        Pattern pattern = Pattern.compile(Pattern.quote(target), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Matcher matcher = pattern.matcher(source);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(censorWord(matcher.group())));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String censorWord(String word) {
        if (word.length() <= 2) {
            StringBuilder stars = new StringBuilder(word.length());
            for (int i = 0; i < word.length(); i++) {
                stars.append('*');
            }
            return stars.toString();
        }
        StringBuilder result = new StringBuilder(word.length()).append(word.charAt(0));
        for (int i = 1; i < word.length() - 1; i++) {
            result.append(Character.isWhitespace(word.charAt(i)) ? ' ' : '*');
        }
        return result.append(word.charAt(word.length() - 1)).toString();
    }

    private String censorMessage(String message) {
        String[] words = message.split("(?<=\\s)|(?=\\s)");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            result.append(word.trim().isEmpty() ? word : censorWord(word));
        }
        return result.toString();
    }

    private void showCensorTitle(UUID uuid, RuntimeConfig.CensorTitle titleConfig) {
        scheduler.runForPlayer(uuid, () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                return;
            }
            Title.Times times = TextUtil.titleTimes(
                    Duration.ofMillis(titleConfig.fadeInMs()),
                    Duration.ofMillis(titleConfig.stayMs()),
                    Duration.ofMillis(titleConfig.fadeOutMs()));
            player.showTitle(Title.title(Component.empty(),
                    TextUtil.format(plugin.getMessageSnapshot().text("ai-helper.subtitle")), times));
        });
    }

    private String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return String.valueOf(cause.getMessage());
    }

    private void logModeration(UUID playerId, String message, ModerationResult result, RuntimeConfig.Ai config) {
        boolean allLogs = config.mistral().allLogs() || config.groq().allLogs();
        if (!allLogs && !result.violation()) {
            return;
        }
        scheduler.runServer(() -> {
            Player player = Bukkit.getPlayer(playerId);
            String playerName = player == null ? playerId.toString() : player.getName();
            String messageText = plugin.getMessageSnapshot().text("ai-helper.log-message")
                    .replace("%player%", playerName)
                    .replace("%message%", message);
            String verdict = plugin.getMessageSnapshot().text(
                    result.violation() ? "ai-helper.verdict-punished" : "ai-helper.verdict-clean");
            String resultText = plugin.getMessageSnapshot().text("ai-helper.log-result")
                    .replace("%model_ai%", "AI")
                    .replace("%probability%", String.valueOf(result.probability()))
                    .replace("%verdict%", verdict);
            plugin.getLogger().info(TextUtil.plain(TextUtil.format(messageText)));
            plugin.getLogger().info(TextUtil.plain(TextUtil.format(resultText)));
        });
    }

    public AiRules getRules() {
        return rules;
    }

    @Override
    public boolean isEnabled() {
        RuntimeConfig.Ai cfg = aiConfig;
        return cfg != null && (mistralApi != null || groqApi != null);
    }

    private CompletableFuture<ModerationResult> withTimeout(CompletableFuture<ModerationResult> future,
            long timeoutSeconds, CompletableFuture<?>... requests) {
        if (timeoutSeconds <= 0) {
            return future;
        }
        ScheduledFuture<?> timeout = timeoutExecutor.schedule(() -> {
                    for (CompletableFuture<?> request : requests) {
                        request.cancel(true);
                    }
                    future.completeExceptionally(new IllegalStateException("moderation timeout"));
                },
                timeoutSeconds, TimeUnit.SECONDS);
        future.whenComplete((result, error) -> timeout.cancel(false));
        return future;
    }

    private void stopProviders() {
        if (mistralApi != null) {
            mistralApi.close();
        }
        if (groqApi != null) {
            groqApi.close();
        }
        mistralApi = null;
        groqApi = null;
    }

    public void close() {
        stopProviders();
        timeoutExecutor.shutdownNow();
    }

}
