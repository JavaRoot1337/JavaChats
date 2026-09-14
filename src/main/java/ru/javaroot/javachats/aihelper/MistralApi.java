package ru.javaroot.javachats.aihelper;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import ru.javaroot.JavaChat;
import ru.javaroot.javachats.config.RuntimeConfig;

import java.util.List;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class MistralApi implements AutoCloseable {
    private static final int MAX_MESSAGE_LENGTH = 4096;
    private static final int MAX_PROMPT_LENGTH = 32 * 1024;
    private static final int MAX_RESPONSE_CONTENT_LENGTH = 16 * 1024;
    private static final int MAX_BAD_WORDS = 16;
    private static final int MAX_BAD_WORD_LENGTH = 256;
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
        this(plugin, provider, provider.apiKey());
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
        if (key == null || key.trim().isEmpty() || systemPrompt == null || userPromptFormat == null || msg == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (msg.length() > MAX_MESSAGE_LENGTH) {
            return failedFuture(new IllegalArgumentException("message is too long"));
        }

        Map<String, AiRules.RuleInfo> safeRules = rules == null
                ? Collections.<String, AiRules.RuleInfo>emptyMap() : rules;
        String context = safeRules.values().stream()
                .filter(rule -> rule != null && rule.id != null && rule.description != null)
                .map(rule -> "- " + rule.id + ": " + rule.description)
                .collect(Collectors.joining("\n"));
        String prompt = systemPrompt
                .replace("%rules%", context)
                .replace("%examples_plus%", buildExamples("Примеры сообщений, которые НЕ нарушают правила:", plus))
                .replace("%examples_minus%", buildExamples("Примеры сообщений, которые НАРУШАЮТ правила:", minus));

        if (prompt.length() > MAX_PROMPT_LENGTH) {
            return failedFuture(new IllegalArgumentException("prompt is too long"));
        }

        JsonArray messages = new JsonArray();
        messages.add(message("system", prompt));
        messages.add(message("user", userPromptFormat.replace("%message%", msg)));

        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.add("messages", messages);
        body.addProperty("temperature", config.temperature());
        body.add("response_format", gson.fromJson("{\"type\":\"json_object\"}", JsonObject.class));

        return http.post(gson.toJson(body)).thenApply(response -> parse(response, msg, safeRules));
    }

    private AiResult parse(String response, String message, Map<String, AiRules.RuleInfo> rules) {
        try {
            JsonObject json = gson.fromJson(response, JsonObject.class);
            if (json == null || !json.has("choices") || !json.get("choices").isJsonArray()
                    || json.getAsJsonArray("choices").size() == 0) {
                throw new IllegalArgumentException("missing choices");
            }
            JsonElement choice = json.getAsJsonArray("choices").get(0);
            JsonObject messageObject = choice != null && choice.isJsonObject()
                    ? choice.getAsJsonObject().getAsJsonObject("message") : null;
            JsonElement contentElement = messageObject == null ? null : messageObject.get("content");
            if (contentElement == null || !contentElement.isJsonPrimitive()
                    || !contentElement.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("missing message content");
            }
            String content = contentElement.getAsString();
            if (content.length() > MAX_RESPONSE_CONTENT_LENGTH) {
                throw new IllegalArgumentException("response content is too long");
            }
            return validateResult(gson.fromJson(content, JsonObject.class), message, rules);
        } catch (RuntimeException ex) {
            plugin.getLogs().warning("mistral-parse", ru.javaroot.javachats.utils.LogVars.of(
                    "error", String.valueOf(ex.getMessage())));
            throw new IllegalArgumentException("invalid Mistral response", ex);
        }
    }

    private AiResult validateResult(JsonObject json, String message, Map<String, AiRules.RuleInfo> rules) {
        if (json == null || !json.has("violation") || !json.get("violation").isJsonPrimitive()
                || !json.getAsJsonPrimitive("violation").isBoolean()
                || !json.has("probability") || !json.get("probability").isJsonPrimitive()
                || !json.getAsJsonPrimitive("probability").isNumber()
                || !json.has("rule") || !json.get("rule").isJsonPrimitive()
                || !json.getAsJsonPrimitive("rule").isString()
                || !json.has("bad_words") || !json.get("bad_words").isJsonArray()) {
            throw new IllegalArgumentException("invalid moderation result fields");
        }
        AiResult result = new AiResult();
        result.violation = json.get("violation").getAsBoolean();
        result.probability = json.get("probability").getAsDouble();
        result.rule = json.get("rule").getAsString();
        if (Double.isNaN(result.probability) || Double.isInfinite(result.probability)
                || result.probability < 0.0 || result.probability > 1.0) {
            throw new IllegalArgumentException("probability must be between 0 and 1");
        }
        if (result.violation && (result.rule.trim().isEmpty() || !rules.containsKey(result.rule))) {
            throw new IllegalArgumentException("unknown violation rule");
        }
        if (!result.violation) {
            result.rule = "";
        }
        result.bad_words = new java.util.ArrayList<String>();
        JsonArray badWords = json.getAsJsonArray("bad_words");
        if (badWords.size() > MAX_BAD_WORDS) {
            throw new IllegalArgumentException("too many bad_words");
        }
        for (JsonElement element : badWords) {
            if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("bad_words must contain strings");
            }
            String badWord = element.getAsString();
            if (badWord.isEmpty() || badWord.length() > MAX_BAD_WORD_LENGTH
                    || !containsIgnoreCase(message, badWord)) {
                throw new IllegalArgumentException("invalid bad_word");
            }
            result.bad_words.add(badWord);
        }
        if (!result.violation) {
            result.bad_words.clear();
        }
        return result;
    }

    private boolean containsIgnoreCase(String source, String target) {
        for (int i = 0; i <= source.length() - target.length(); i++) {
            if (source.regionMatches(true, i, target, 0, target.length())) {
                return true;
            }
        }
        return false;
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

    private static <T> CompletableFuture<T> failedFuture(Throwable error) {
        CompletableFuture<T> future = new CompletableFuture<T>();
        future.completeExceptionally(error);
        return future;
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
