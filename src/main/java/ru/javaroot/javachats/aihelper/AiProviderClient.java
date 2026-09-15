package ru.javaroot.javachats.aihelper;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import ru.javaroot.JavaChat;
import ru.javaroot.javachats.config.RuntimeConfig;
import ru.javaroot.javachats.utils.LogVars;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

final class AiProviderClient implements AutoCloseable {
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
    private final String provider;

    AiProviderClient(JavaChat plugin, String key, String endpoint, String model, long timeoutSeconds,
            long cooldownSeconds, String provider) {
        this.plugin = plugin;
        this.key = key;
        this.model = model;
        this.provider = provider;
        this.http = new HttpAiClient(plugin, key, endpoint, timeoutSeconds, cooldownSeconds, provider);
    }

    CompletableFuture<Result> checkMsg(String msg, Map<String, AiRules.RuleInfo> rules, List<String> plus,
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
        String prompt = systemPrompt
                .replace("%rules%", rulesText(safeRules))
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

        CompletableFuture<String> response = http.post(gson.toJson(body));
        CompletableFuture<Result> result = response.thenApply(value -> parse(value, msg, safeRules));
        result.whenComplete((value, error) -> {
            if (result.isCancelled()) {
                response.cancel(true);
            }
        });
        return result;
    }

    private String rulesText(Map<String, AiRules.RuleInfo> rules) {
        return rules.values().stream()
                .filter(rule -> rule != null && rule.id != null && rule.description != null)
                .map(rule -> "- " + rule.id + ": " + rule.description)
                .collect(Collectors.joining("\n"));
    }

    private Result parse(String response, String message, Map<String, AiRules.RuleInfo> rules) {
        try {
            JsonObject json = gson.fromJson(response, JsonObject.class);
            if (json == null || !json.has("choices") || !json.get("choices").isJsonArray()
                    || json.getAsJsonArray("choices").size() == 0) {
                throw new IllegalArgumentException("missing choices");
            }
            JsonElement choice = json.getAsJsonArray("choices").get(0);
            JsonObject messageObject = choice != null && choice.isJsonObject()
                    ? choice.getAsJsonObject().getAsJsonObject("message") : null;
            JsonElement content = messageObject == null ? null : messageObject.get("content");
            if (content == null || !content.isJsonPrimitive() || !content.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("missing message content");
            }
            String value = content.getAsString();
            if (value.length() > MAX_RESPONSE_CONTENT_LENGTH) {
                throw new IllegalArgumentException("response content is too long");
            }
            return validate(gson.fromJson(value, JsonObject.class), message, rules);
        } catch (RuntimeException error) {
            plugin.getLogs().warning(provider + "-parse", LogVars.of("error", String.valueOf(error.getMessage())));
            throw new IllegalArgumentException("invalid " + provider + " response", error);
        }
    }

    private Result validate(JsonObject json, String message, Map<String, AiRules.RuleInfo> rules) {
        if (json == null || !json.has("violation") || !json.get("violation").isJsonPrimitive()
                || !json.getAsJsonPrimitive("violation").isBoolean()
                || !json.has("probability") || !json.get("probability").isJsonPrimitive()
                || !json.getAsJsonPrimitive("probability").isNumber()
                || !json.has("rule") || !json.get("rule").isJsonPrimitive()
                || !json.getAsJsonPrimitive("rule").isString()
                || !json.has("bad_words") || !json.get("bad_words").isJsonArray()) {
            throw new IllegalArgumentException("invalid moderation result fields");
        }
        boolean violation = json.get("violation").getAsBoolean();
        double probability = json.get("probability").getAsDouble();
        String rule = json.get("rule").getAsString();
        if (Double.isNaN(probability) || Double.isInfinite(probability) || probability < 0.0 || probability > 1.0) {
            throw new IllegalArgumentException("probability must be between 0 and 1");
        }
        if (violation && (rule.trim().isEmpty() || !rules.containsKey(rule))) {
            throw new IllegalArgumentException("unknown violation rule");
        }

        JsonArray values = json.getAsJsonArray("bad_words");
        if (values.size() > MAX_BAD_WORDS) {
            throw new IllegalArgumentException("too many bad_words");
        }
        List<String> badWords = new ArrayList<String>();
        for (JsonElement element : values) {
            if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("bad_words must contain strings");
            }
            String badWord = element.getAsString();
            if (badWord.isEmpty() || badWord.length() > MAX_BAD_WORD_LENGTH
                    || !containsIgnoreCase(message, badWord)) {
                throw new IllegalArgumentException("invalid bad_word");
            }
            badWords.add(badWord);
        }
        return new Result(violation, violation ? rule : "", violation ? badWords : Collections.<String>emptyList(),
                probability);
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

    static final class Result {
        private final boolean violation;
        private final String rule;
        private final List<String> badWords;
        private final double probability;

        private Result(boolean violation, String rule, List<String> badWords, double probability) {
            this.violation = violation;
            this.rule = rule;
            this.badWords = badWords;
            this.probability = probability;
        }

        boolean violation() {
            return violation;
        }

        String rule() {
            return rule;
        }

        List<String> badWords() {
            return badWords;
        }

        double probability() {
            return probability;
        }
    }
}
