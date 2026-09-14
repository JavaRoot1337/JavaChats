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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AiMod implements ModerationService {
    private static final int MAX_MESSAGE_LENGTH = 4096;
    private final JavaChat plugin;
    private final AiRules rules;
    private final ServerScheduler scheduler;
    private final AtomicLong lastCheckMistral = new AtomicLong();
    private final AtomicLong lastCheckGroq = new AtomicLong();
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
        this.rules = new AiRules(plugin);
        this.scheduler = scheduler;
    }

    public void reload() {
        stopProviders();
        RuntimeConfig.Ai config = plugin.getRuntimeConfig().ai();
        rules.load(config.systemPrompt());
        aiConfig = config;

        RuntimeConfig.Provider mistral = aiConfig.mistral();
        String mistralKey = mistral.apiKey();
        if (isConfigured(mistral, mistralKey)) {
            mistralApi = createMistral(mistral, mistralKey);
        }

        RuntimeConfig.Provider groq = aiConfig.groq();
        String groqKey = groq.apiKey();
        if (isConfigured(groq, groqKey)) {
            groqApi = createGroq(groq, groqKey);
        }

    }

    private MistralApi createMistral(RuntimeConfig.Provider provider, String key) {
        try {
            return new MistralApi(plugin, provider, key);
        } catch (RuntimeException ex) {
            plugin.getLogs().warning("mistral-config", LogVars.of("error", String.valueOf(ex.getMessage())));
            return null;
        }
    }

    private GroqApi createGroq(RuntimeConfig.Provider provider, String key) {
        try {
            return new GroqApi(plugin, provider, key);
        } catch (RuntimeException ex) {
            plugin.getLogs().warning("groq-config", LogVars.of("error", String.valueOf(ex.getMessage())));
            return null;
        }
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

        boolean useMistral = isModerationProvider(cfg.mistral(), mistralApi != null)
                && claimCheck(lastCheckMistral, cfg.mistral().cooldownSeconds());
        boolean useGroq = isModerationProvider(cfg.groq(), groqApi != null)
                && claimCheck(lastCheckGroq, cfg.groq().cooldownSeconds());
        if (!useMistral && !useGroq) {
            return CompletableFuture.completedFuture(ModerationResult.clean());
        }

        List<String> plus = rules.getTrainingPlus();
        List<String> minus = rules.getTrainingMinus();
        String systemPrompt = rules.getSystemPrompt();
        CompletableFuture<MistralApi.AiResult> mistral = useMistral
                ? mistralApi.checkMsg(msg, rules.getRules(), plus, minus, cfg, systemPrompt)
                : CompletableFuture.completedFuture(null);
        CompletableFuture<GroqApi.AiResult> groq = useGroq
                ? groqApi.checkMsg(msg, rules.getRules(), plus, minus, cfg, systemPrompt)
                : CompletableFuture.completedFuture(null);

        int activeProviders = (useMistral ? 1 : 0) + (useGroq ? 1 : 0);
        AtomicInteger failedProviders = new AtomicInteger();
        if (useMistral) {
            mistral = tolerateProviderFailure(mistral, "Mistral", failedProviders);
        }
        if (useGroq) {
            groq = tolerateProviderFailure(groq, "Groq", failedProviders);
        }
        final CompletableFuture<MistralApi.AiResult> mistralResult = mistral;
        final CompletableFuture<GroqApi.AiResult> groqResult = groq;
        CompletableFuture<ModerationResult> combined = CompletableFuture.allOf(mistral, groq)
                .thenApply(ignored -> {
                    MistralApi.AiResult mistralValue = mistralResult.join();
                    GroqApi.AiResult groqValue = groqResult.join();
                    boolean allFailed = activeProviders > 0
                            && (failedProviders.get() == activeProviders
                            || (mistralValue == null && groqValue == null));
                    return combineModeration(playerId, msg, mistralValue, groqValue, cfg, allFailed);
                })
                ;
        return withTimeout(combined, cfg.censorTimeoutSeconds())
                .handle((result, error) -> {
                    if (error == null) {
                        return result;
                    }
                    plugin.getLogs().warning("moderation-request", LogVars.of("error", rootMessage(error)));
                    return failureResult(cfg);
                });
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

    private ModerationResult combineModeration(UUID playerId, String msg, MistralApi.AiResult mistral,
            GroqApi.AiResult groq, RuntimeConfig.Ai cfg, boolean allProvidersFailed) {
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
            rule = mistral.rule;
            probability = Math.max(probability, mistral.probability);
            if (mistral.bad_words != null) {
                badWords.addAll(mistral.bad_words);
            }
            censored = censorResult(censored, mistral.bad_words);
        }
        if (isPunished(groq, cfg.groq().punishProbability())) {
            violation = true;
            blockingViolation = blockingViolation || "block".equalsIgnoreCase(cfg.groq().mode());
            rule = groq.rule;
            probability = Math.max(probability, groq.probability);
            if (groq.bad_words != null) {
                badWords.addAll(groq.bad_words);
            }
            censored = censorResult(censored, groq.bad_words);
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

    private boolean claimCheck(AtomicLong lastCheck, long cooldownSeconds) {
        long now = System.currentTimeMillis();
        long cooldown = Math.max(cooldownSeconds, 0L) * 1000L;
        while (true) {
            long previous = lastCheck.get();
            if (previous > 0 && now - previous < cooldown) {
                return false;
            }
            if (lastCheck.compareAndSet(previous, now)) {
                return true;
            }
        }
    }

    private boolean isConfigured(RuntimeConfig.Provider provider, String key) {
        return provider.enabled() && key != null && !key.trim().isEmpty();
    }

    private boolean isModerationProvider(RuntimeConfig.Provider provider, boolean available) {
        return available && provider.enabled()
                && ("censor".equalsIgnoreCase(provider.mode()) || "block".equalsIgnoreCase(provider.mode()));
    }

    private boolean isPunished(MistralApi.AiResult result, double threshold) {
        return result != null && result.violation && result.probability >= threshold;
    }

    private boolean isPunished(GroqApi.AiResult result, double threshold) {
        return result != null && result.violation && result.probability >= threshold;
    }

    private String censorResult(String message, List<String> badWords) {
        if (badWords == null || badWords.isEmpty()) {
            return censorMessage(message);
        }
        String result = message;
        for (String badWord : badWords) {
            result = replaceCaseInsensitive(result, badWord);
        }
        return result;
    }

    private String replaceCaseInsensitive(String source, String target) {
        if (target == null || target.isEmpty()) {
            return source;
        }
        Pattern pattern = Pattern.compile(Pattern.quote(target), Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(source);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(censorWord(matcher.group())));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String censorWord(String word) {
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

    public AiRules getRules() {
        return rules;
    }

    @Override
    public boolean isEnabled() {
        RuntimeConfig.Ai cfg = aiConfig;
        return cfg != null && (mistralApi != null || groqApi != null);
    }

    private CompletableFuture<ModerationResult> withTimeout(CompletableFuture<ModerationResult> future,
            long timeoutSeconds) {
        if (timeoutSeconds <= 0) {
            return future;
        }
        ScheduledFuture<?> timeout = timeoutExecutor.schedule(
                () -> future.completeExceptionally(new IllegalStateException("moderation timeout")),
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
        lastCheckMistral.set(0L);
        lastCheckGroq.set(0L);
    }

    public void close() {
        stopProviders();
        timeoutExecutor.shutdownNow();
    }

}
