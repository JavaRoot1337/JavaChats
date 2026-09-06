package ru.javaroot.javachats.aihelper;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import ru.javaroot.JavaChat;
import ru.javaroot.javachats.config.RuntimeConfig;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class MistralApi implements AutoCloseable {
    private final JavaChat plugin;
    private final HttpAiClient http;
    private final Gson gson = new Gson();
    private final String key;
    private final String model;

    public MistralApi(JavaChat plugin, String key, String endpoint, String model, long timeoutSeconds) {
        this.plugin = plugin;
        this.key = key;
        this.model = model;
        this.http = new HttpAiClient(plugin, key, endpoint, timeoutSeconds, "mistral");
    }

    public MistralApi(JavaChat plugin, RuntimeConfig.Provider provider) {
        this(plugin, provider, plugin.getConfig().getString("ai-helper.mistral-api.api-key"));
    }

    public MistralApi(JavaChat plugin, RuntimeConfig.Provider provider, String key) {
        this(plugin, key, provider.endpoint(), provider.model(), provider.timeoutSeconds());
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
        String userPromptFormat = config.userPromptFormat();
        if (key == null || key.isEmpty() || systemPrompt == null || userPromptFormat == null) {
            return CompletableFuture.completedFuture(null);
        }

        String context = rules.values().stream()
                .map(rule -> "- " + rule.id + ": " + rule.description)
                .collect(Collectors.joining("\n"));
        String prompt = systemPrompt
                .replace("%rules%", context)
                .replace("%examples_plus%", buildExamples("Примеры сообщений, которые НЕ нарушают правила:", plus))
                .replace("%examples_minus%", buildExamples("Примеры сообщений, которые НАРУШАЮТ правила:", minus));

        JsonArray messages = new JsonArray();
        messages.add(message("system", prompt));
        messages.add(message("user", userPromptFormat.replace("%message%", msg)));

        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.add("messages", messages);
        body.addProperty("temperature", config.temperature());
        body.add("response_format", gson.fromJson("{\"type\":\"json_object\"}", JsonObject.class));

        return http.post(gson.toJson(body)).thenApply(response -> parse(response));
    }

    private AiResult parse(String response) {
        if (response == null) {
            return null;
        }
        try {
            JsonObject json = gson.fromJson(response, JsonObject.class);
            String content = json.getAsJsonArray("choices").get(0).getAsJsonObject()
                    .getAsJsonObject("message").get("content").getAsString();
            return gson.fromJson(content, AiResult.class);
        } catch (RuntimeException ex) {
            plugin.getLogs().warning("mistral-parse", ru.javaroot.javachats.utils.LogVars.of(
                    "error", String.valueOf(ex.getMessage())));
            return null;
        }
    }

    private JsonObject message(String role, String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content);
        return message;
    }

    private String buildExamples(String title, List<String> examples) {
        if (examples == null || examples.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder(title).append('\n');
        for (String example : examples) {
            result.append("- ").append(example).append('\n');
        }
        return result.append('\n').toString();
    }

    @Override
    public void close() {
        http.close();
    }

    public static final class AiResult {
        public boolean violation;
        public String rule;
        public double probability;
        public List<String> bad_words;
    }
}

