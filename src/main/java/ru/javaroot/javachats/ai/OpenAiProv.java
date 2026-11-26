package ru.javaroot.javachats.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import ru.javaroot.javachats.cfg.Cfg;
import ru.javaroot.javachats.cfg.LogCfg;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

final class OpenAiProv implements AutoCloseable {
    private static final int MAX_MSG_LEN = 4096;
    private static final int MAX_PROMPT_LEN = 32 * 1024;
    private static final int MAX_CONTENT_LEN = 16 * 1024;
    private static final int MAX_BAD_WORDS = 16;
    private static final int MAX_BAD_WORD_LEN = 256;

    private final LogCfg logs;
    private final AiClient http;
    private final Gson gson = new Gson();
    private final String key;
    private final String model;
    private final String name;

    OpenAiProv(LogCfg logs, Cfg.Provider prov, String name) {
        this.logs = logs;
        this.key = prov.apiKey();
        this.model = prov.model();
        this.name = name;
        this.http = new AiClient(logs, key, prov.endpoint(), prov.timeoutSeconds(), prov.cooldownSeconds(), name);
    }

    CompletableFuture<ProvRes> check(String msg, Map<String, AiRules.RuleInfo> rules, List<String> plus,
            List<String> minus, Cfg.Ai cfg, String systemPrompt) {
        String userFmt = cfg.userPromptFormat();
        if (key == null || key.trim().isEmpty() || systemPrompt == null || userFmt == null || msg == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (msg.length() > MAX_MSG_LEN) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("сообщение слишком длинное"));
        }

        Map<String, AiRules.RuleInfo> safeRules = rules == null ? Map.of() : rules;
        String prompt = systemPrompt
                .replace("%rules%", formatRules(safeRules))
                .replace("%examples_plus%", formatExamples("Примеры сообщений с нарушением:", plus))
                .replace("%examples_minus%", formatExamples("Примеры сообщений без нарушения:", minus));
        if (prompt.length() > MAX_PROMPT_LEN) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("промпт превышает лимит"));
        }

        JsonArray messages = new JsonArray();
        messages.add(entry("system", prompt));
        messages.add(entry("user", userFmt.replace("%message%", msg)));

        JsonObject responseFormat = new JsonObject();
        responseFormat.addProperty("type", "json_object");

        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.add("messages", messages);
        body.addProperty("temperature", cfg.temperature());
        body.add("response_format", responseFormat);

        CompletableFuture<String> raw = http.post(gson.toJson(body));
        CompletableFuture<ProvRes> out = raw.thenApply(val -> parse(val, msg, safeRules));
        out.whenComplete((v, err) -> {
            if (out.isCancelled()) {
                raw.cancel(true);
            }
        });
        return out;
    }

    private ProvRes parse(String response, String message, Map<String, AiRules.RuleInfo> rules) {
        try {
            JsonObject json = gson.fromJson(response, JsonObject.class);
            if (json == null || !json.has("choices") || !json.get("choices").isJsonArray()
                    || json.getAsJsonArray("choices").isEmpty()) {
                throw new IllegalArgumentException("нет массива choices");
            }
            JsonElement first = json.getAsJsonArray("choices").get(0);
            JsonObject msgObj = first != null && first.isJsonObject()
                    ? first.getAsJsonObject().getAsJsonObject("message") : null;
            JsonElement content = msgObj == null ? null : msgObj.get("content");
            if (content == null || !content.isJsonPrimitive() || !content.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("отсутствует текст ответа");
            }
            String value = content.getAsString();
            if (value.length() > MAX_CONTENT_LEN) {
                throw new IllegalArgumentException("ответ слишком длинный");
            }
            return validate(gson.fromJson(value, JsonObject.class), message, rules);
        } catch (RuntimeException ex) {
            logs.warning(name + "-parse", Map.of("error", String.valueOf(ex.getMessage())));
            throw new IllegalArgumentException("ошибка разбора ответа " + name, ex);
        }
    }

    private ProvRes validate(JsonObject json, String message, Map<String, AiRules.RuleInfo> rules) {
        if (json == null || !isBool(json, "violation") || !isNum(json, "probability")
                || !isStr(json, "rule") || !json.has("bad_words") || !json.get("bad_words").isJsonArray()) {
            throw new IllegalArgumentException("некорректная структура JSON");
        }
        boolean violation = json.get("violation").getAsBoolean();
        double probability = json.get("probability").getAsDouble();
        String rule = json.get("rule").getAsString();
        if (Double.isNaN(probability) || Double.isInfinite(probability) || probability < 0.0 || probability > 1.0) {
            throw new IllegalArgumentException("probability вне диапазона [0, 1]");
        }
        if (violation && (rule.trim().isEmpty() || !rules.containsKey(rule))) {
            throw new IllegalArgumentException("неизвестное правило: " + rule);
        }

        JsonArray arr = json.getAsJsonArray("bad_words");
        if (arr.size() > MAX_BAD_WORDS) {
            throw new IllegalArgumentException("слишком много bad_words");
        }
        List<String> words = new ArrayList<>(arr.size());
        for (JsonElement el : arr) {
            if (el == null || !el.isJsonPrimitive() || !el.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("bad_words должен содержать строки");
            }
            String word = el.getAsString();
            if (word.isEmpty() || word.length() > MAX_BAD_WORD_LEN || !containsIgnoreCase(message, word)) {
                throw new IllegalArgumentException("фрагмент отсутствует в исходном сообщении");
            }
            words.add(word);
        }
        return new ProvRes(violation, violation ? rule : "", violation ? List.copyOf(words) : List.of(), probability);
    }

    private static boolean isBool(JsonObject obj, String key) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() && obj.getAsJsonPrimitive(key).isBoolean();
    }

    private static boolean isNum(JsonObject obj, String key) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() && obj.getAsJsonPrimitive(key).isNumber();
    }

    private static boolean isStr(JsonObject obj, String key) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() && obj.getAsJsonPrimitive(key).isString();
    }

    private static boolean containsIgnoreCase(String src, String target) {
        for (int i = 0; i <= src.length() - target.length(); i++) {
            if (src.regionMatches(true, i, target, 0, target.length())) {
                return true;
            }
        }
        return false;
    }

    private static String formatRules(Map<String, AiRules.RuleInfo> rules) {
        return rules.values().stream()
                .filter(r -> r != null && r.id() != null && r.description() != null)
                .map(r -> "- " + r.id() + ": " + r.description())
                .collect(Collectors.joining("\n"));
    }

    private static String formatExamples(String title, List<String> examples) {
        if (examples == null || examples.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(title).append('\n');
        for (String item : examples) {
            sb.append("- ").append(item).append('\n');
        }
        return sb.append('\n').toString();
    }

    private static JsonObject entry(String role, String content) {
        JsonObject msg = new JsonObject();
        msg.addProperty("role", role);
        msg.addProperty("content", content);
        return msg;
    }

    @Override
    public void close() {
        http.close();
    }

    record ProvRes(boolean violation, String rule, List<String> badWords, double probability) {
    }
}
