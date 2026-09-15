package ru.javaroot.javachats.aihelper;

import ru.javaroot.JavaChat;
import ru.javaroot.javachats.config.RuntimeConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class GroqApi implements AutoCloseable {
    private final JavaChat plugin;
    private final AiProviderClient client;

    public GroqApi(JavaChat plugin, String key, String endpoint, String model, long timeoutSeconds) {
        this(plugin, key, endpoint, model, timeoutSeconds, 0L);
    }

    public GroqApi(JavaChat plugin, RuntimeConfig.Provider provider) {
        this(plugin, provider, provider.apiKey());
    }

    public GroqApi(JavaChat plugin, RuntimeConfig.Provider provider, String key) {
        this(plugin, key, provider.endpoint(), provider.model(), provider.timeoutSeconds(), provider.cooldownSeconds());
    }

    private GroqApi(JavaChat plugin, String key, String endpoint, String model, long timeoutSeconds,
            long cooldownSeconds) {
        this.plugin = plugin;
        client = new AiProviderClient(plugin, key, endpoint, model, timeoutSeconds, cooldownSeconds, "groq");
    }

    public CompletableFuture<AiResult> checkMsg(String msg, Map<String, AiRules.RuleInfo> rules, List<String> plus,
            List<String> minus) {
        return checkMsg(msg, rules, plus, minus, plugin.getRuntimeConfig().ai());
    }

    public CompletableFuture<AiResult> checkMsg(String msg, Map<String, AiRules.RuleInfo> rules, List<String> plus,
            List<String> minus, RuntimeConfig.Ai config) {
        return checkMsg(msg, rules, plus, minus, config, config.systemPrompt());
    }

    public CompletableFuture<AiResult> checkMsg(String msg, Map<String, AiRules.RuleInfo> rules, List<String> plus,
            List<String> minus, RuntimeConfig.Ai config, String systemPrompt) {
        return check(msg, rules, plus, minus, config, systemPrompt).thenApply(AiResult::from);
    }

    CompletableFuture<AiProviderClient.Result> check(String msg, Map<String, AiRules.RuleInfo> rules, List<String> plus,
            List<String> minus, RuntimeConfig.Ai config, String systemPrompt) {
        return client.checkMsg(msg, rules, plus, minus, config, systemPrompt);
    }

    @Override
    public void close() {
        client.close();
    }

    public static final class AiResult {
        public boolean violation;
        public String rule;
        public double probability;
        public List<String> bad_words;

        private static AiResult from(AiProviderClient.Result value) {
            if (value == null) {
                return null;
            }
            AiResult result = new AiResult();
            result.violation = value.violation();
            result.rule = value.rule();
            result.probability = value.probability();
            result.bad_words = new ArrayList<String>(value.badWords());
            return result;
        }
    }
}
