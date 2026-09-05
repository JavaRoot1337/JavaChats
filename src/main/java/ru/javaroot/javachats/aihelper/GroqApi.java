package ru.javaroot.javachats.aihelper;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import ru.javaroot.JavaChat;
import ru.javaroot.javachats.config.RuntimeConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class GroqApi implements AutoCloseable {
    private final JavaChat plugin;
    private final HttpClient http;
    private final Gson gson = new Gson();
    private final String key;
    private final URI endpoint;
    private final String model;
    private final Duration timeout;

    public GroqApi(JavaChat plugin, String key, String endpoint, String model, long timeoutSeconds) {
        this.plugin = plugin;
        this.key = key;
        this.endpoint = URI.create(endpoint);
        this.model = model;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    public GroqApi(JavaChat plugin, RuntimeConfig.Provider provider) {
        this(plugin, provider, plugin.getConfig().getString("ai-helper.groq-api.api-key"));
    }

    public GroqApi(JavaChat plugin, RuntimeConfig.Provider provider, String key) {
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
        String exPlus = buildExamples("Примеры сообщений, которые НЕ нарушают правила:", plus);
        String exMinus = buildExamples("Примеры сообщений, которые НАРУШАЮТ правила:", minus);
        String prompt = systemPrompt
                .replace("%rules%", context)
                .replace("%examples_plus%", exPlus)
                .replace("%examples_minus%", exMinus);

        JsonArray messages = new JsonArray();
        messages.add(message("system", prompt));
        messages.add(message("user", userPromptFormat.replace("%message%", msg)));

        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.add("messages", messages);
        body.addProperty("temperature", config.temperature());
        body.add("response_format", gson.fromJson("{\"type\":\"json_object\"}", JsonObject.class));

        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + key)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();

        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                plugin.getLogs().warning("groq-http", Map.of(
                        "status", String.valueOf(response.statusCode())));
                return null;
            }
            try {
                JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                String content = json.getAsJsonArray("choices").get(0).getAsJsonObject()
                        .getAsJsonObject("message").get("content").getAsString();
                return gson.fromJson(content, AiResult.class);
            } catch (RuntimeException ex) {
                plugin.getLogs().warning("groq-parse", Map.of(
                        "error", String.valueOf(ex.getMessage())));
                return null;
            }
        }).exceptionally(error -> {
            plugin.getLogs().warning("groq-request", Map.of(
                    "error", String.valueOf(error.getMessage())));
            return null;
        });
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

    public String getModelName() {
        return model;
    }

    public static class AiResult {
        public boolean violation;
        public String rule;
        public double probability;
        public List<String> bad_words;
    }
}
