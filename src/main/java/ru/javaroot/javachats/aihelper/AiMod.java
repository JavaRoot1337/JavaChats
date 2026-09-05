package ru.javaroot.javachats.aihelper;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AiMod implements ModerationService {
    private final JavaChat plugin;
    private final AiRules rules;
    private final ServerScheduler scheduler;
    private final Queue<PendingMessage> queue = new ConcurrentLinkedQueue<>();
    private final AtomicLong lastCheckMistral = new AtomicLong();
    private final AtomicLong lastCheckGroq = new AtomicLong();
    private volatile RuntimeConfig.Ai aiConfig;
    private MistralApi mistralApi;
    private GroqApi groqApi;
    private ScheduledTask processTask;

    public AiMod(JavaChat plugin) {
        this(plugin, new ServerScheduler(plugin));
    }

    public AiMod(JavaChat plugin, ServerScheduler scheduler) {
        this.plugin = plugin;
        this.rules = new AiRules(plugin);
        this.scheduler = scheduler;
    }

    public void reload() {
        close();
        RuntimeConfig.Ai config = plugin.getRuntimeConfig().ai();
        rules.load(config.systemPrompt());
        aiConfig = config;

        RuntimeConfig.Provider mistral = aiConfig.mistral();
        String mistralKey = plugin.getConfig().getString("ai-helper.mistral-api.api-key");
        if (isConfigured(mistral, mistralKey)) {
            mistralApi = createMistral(mistral, mistralKey);
        }

        RuntimeConfig.Provider groq = aiConfig.groq();
        String groqKey = plugin.getConfig().getString("ai-helper.groq-api.api-key");
        if (isConfigured(groq, groqKey)) {
            groqApi = createGroq(groq, groqKey);
        }

        long periodSeconds = Long.MAX_VALUE;
        if (isBlock(mistral, mistralApi != null)) {
            periodSeconds = Math.min(periodSeconds, mistral.cooldownSeconds());
        }
        if (isBlock(groq, groqApi != null)) {
            periodSeconds = Math.min(periodSeconds, groq.cooldownSeconds());
        }
        if (periodSeconds > 0 && periodSeconds != Long.MAX_VALUE) {
            processTask = scheduler.runAsyncRepeating(this::processNext,
                    aiConfig.blockInitialDelayTicks() * 50L, periodSeconds, TimeUnit.SECONDS);
        }
    }

    private MistralApi createMistral(RuntimeConfig.Provider provider, String key) {
        try {
            return new MistralApi(plugin, provider, key);
        } catch (RuntimeException ex) {
            plugin.getLogs().warning("mistral-config", Map.of("error", String.valueOf(ex.getMessage())));
            return null;
        }
    }

    private GroqApi createGroq(RuntimeConfig.Provider provider, String key) {
        try {
            return new GroqApi(plugin, provider, key);
        } catch (RuntimeException ex) {
            plugin.getLogs().warning("groq-config", Map.of("error", String.valueOf(ex.getMessage())));
            return null;
        }
    }

    public void handleMsg(String playerName, String msg) {
        RuntimeConfig.Ai cfg = aiConfig;
        if (cfg == null || (!isBlock(cfg.mistral(), mistralApi != null) && !isBlock(cfg.groq(), groqApi != null))) {
            return;
        }
        if (queue.size() >= cfg.blockMaxQueueSize()) {
            return;
        }
        queue.add(new PendingMessage(playerName, msg));
    }

    @Override
    public CompletableFuture<ModerationResult> moderate(UUID playerId, String msg) {
        RuntimeConfig.Ai cfg = aiConfig;
        if (cfg == null) {
            return CompletableFuture.completedFuture(ModerationResult.clean());
        }

        boolean useMistral = isCensor(cfg.mistral(), mistralApi != null)
                && claimCheck(lastCheckMistral, cfg.mistral().cooldownSeconds());
        boolean useGroq = isCensor(cfg.groq(), groqApi != null)
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

        return CompletableFuture.allOf(mistral, groq)
                .orTimeout(Math.max(cfg.censorTimeoutSeconds(), 0L), TimeUnit.SECONDS)
                .thenApply(ignored -> combineModeration(playerId, msg, mistral.join(), groq.join(), cfg))
                .exceptionally(error -> {
                    plugin.getLogs().warning("moderation-request", Map.of("error", rootMessage(error)));
                    return ModerationResult.clean();
                });
    }

    public CompletableFuture<String> censorIfViolation(UUID uuid, String msg) {
        return moderate(uuid, msg).thenApply(result -> result.censoredMessage().orElse(null));
    }

    private ModerationResult combineModeration(UUID playerId, String msg, MistralApi.AiResult mistral,
            GroqApi.AiResult groq, RuntimeConfig.Ai cfg) {
        String censored = msg;
        String rule = null;
        double probability = 0.0;
        boolean violation = false;
        List<String> badWords = new ArrayList<>();
        if (isPunished(mistral, cfg.mistral().punishProbability())) {
            violation = true;
            rule = mistral.rule;
            probability = Math.max(probability, mistral.probability);
            if (mistral.bad_words != null) {
                badWords.addAll(mistral.bad_words);
            }
            censored = censorResult(censored, mistral.bad_words);
        }
        if (isPunished(groq, cfg.groq().punishProbability())) {
            violation = true;
            rule = groq.rule;
            probability = Math.max(probability, groq.probability);
            if (groq.bad_words != null) {
                badWords.addAll(groq.bad_words);
            }
            censored = censorResult(censored, groq.bad_words);
        }
        if (violation) {
            showCensorTitle(playerId, cfg.censorTitle());
            return new ModerationResult(true, probability, rule, badWords, censored);
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
        return provider.enabled() && key != null && !key.isBlank();
    }

    private boolean isBlock(RuntimeConfig.Provider provider, boolean available) {
        return available && provider.enabled() && "block".equalsIgnoreCase(provider.mode());
    }

    private boolean isCensor(RuntimeConfig.Provider provider, boolean available) {
        return available && provider.enabled() && "censor".equalsIgnoreCase(provider.mode());
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
            return "*".repeat(word.length());
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
            Title.Times times = Title.Times.times(
                    Duration.ofMillis(titleConfig.fadeInMs()),
                    Duration.ofMillis(titleConfig.stayMs()),
                    Duration.ofMillis(titleConfig.fadeOutMs()));
            player.showTitle(Title.title(Component.empty(),
                    TextUtil.format(plugin.getMessageSnapshot().text("ai-helper.subtitle")), times));
        });
    }

    private void processNext() {
        PendingMessage pending = queue.poll();
        RuntimeConfig.Ai cfg = aiConfig;
        if (pending == null || cfg == null) {
            return;
        }

        List<String> plus = rules.getTrainingPlus();
        List<String> minus = rules.getTrainingMinus();
        String systemPrompt = rules.getSystemPrompt();
        if (isBlock(cfg.mistral(), mistralApi != null)) {
            mistralApi.checkMsg(pending.msg, rules.getRules(), plus, minus, cfg, systemPrompt)
                    .thenAccept(result -> processAsyncResult(pending, result, "Mistral", cfg.mistral()));
        }
        if (isBlock(cfg.groq(), groqApi != null)) {
            String modelName = cfg.groq().model();
            groqApi.checkMsg(pending.msg, rules.getRules(), plus, minus, cfg, systemPrompt)
                    .thenAccept(result -> processAsyncResult(pending, result,
                            "Groq (" + modelName + ")", cfg.groq()));
        }
    }

    private void processAsyncResult(PendingMessage pending, MistralApi.AiResult result, String modelName,
            RuntimeConfig.Provider provider) {
        if (result != null) {
            processAsyncResult(pending, result.violation, result.probability, result.rule, modelName, provider);
        }
    }

    private void processAsyncResult(PendingMessage pending, GroqApi.AiResult result, String modelName,
            RuntimeConfig.Provider provider) {
        if (result != null) {
            processAsyncResult(pending, result.violation, result.probability, result.rule, modelName, provider);
        }
    }

    private void processAsyncResult(PendingMessage pending, boolean violation, double probability, String rule,
            String modelName,
            RuntimeConfig.Provider provider) {
        scheduler.runServer(() -> {
            boolean punished = violation && probability >= provider.punishProbability();
            String verdict = plugin.getMessageSnapshot().text(
                    punished ? "ai-helper.verdict-punished" : "ai-helper.verdict-clean");
            if (punished) {
                AiRules.RuleInfo info = rules.getRules().get(rule);
                if (info != null && info.punishCmd != null && !info.punishCmd.isEmpty()) {
                    String command = info.punishCmd
                            .replace("%player%", pending.playerName)
                            .replace("%rule%", info.id)
                            .replace("%probability%", String.valueOf(probability));
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                }
            }

            String logMsg = plugin.getMessageSnapshot().text("ai-helper.log-message")
                    .replace("%player%", pending.playerName)
                    .replace("%message%", pending.msg);
            String logRes = plugin.getMessageSnapshot().text("ai-helper.log-result")
                    .replace("%model_ai%", modelName)
                    .replace("%probability%", String.valueOf(probability))
                    .replace("%verdict%", verdict);
            if (punished || provider.allLogs()) {
                notifyAdmins(logMsg);
                notifyAdmins(logRes);
            }
        });
    }

    private void notifyAdmins(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Component component = TextUtil.format(text);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("javachats.admin") || player.hasPermission("javachats.ai.log")) {
                player.sendMessage(component);
            }
        }
        Bukkit.getConsoleSender().sendMessage(component);
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

    public void close() {
        if (processTask != null) {
            processTask.cancel();
            processTask = null;
        }
        queue.clear();
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

    private static class PendingMessage {
        private final String playerName;
        private final String msg;

        private PendingMessage(String playerName, String msg) {
            this.playerName = playerName;
            this.msg = msg;
        }
    }
}
