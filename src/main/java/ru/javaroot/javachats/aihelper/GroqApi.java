package ru.javaroot.javachats.aihelper;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import ru.javaroot.javachats.JavaChat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class GroqApi {
    private static final String URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String DEFAULT_PROMPT = 
        "Ты профессиональный модератор русскоязычного Minecraft-сервера с продвинутой системой распознавания обходов и скрытого мата/оскорблений.\n" +
        "Твоя задача — анализировать сообщения игроков на соответствие правилам сервера:\n" +
        "%rules%\n\n" +
        "Критически важно распознавать любые обходы фильтрации, включая:\n" +
        "1. Замену букв на похожие латинские символы, цифры или спецсимволы (например: 'u' или 'uu' вместо 'и', '0' или 'O' вместо 'о', 'n' вместо 'п', 'a' вместо 'а', '@' вместо 'а', 'La' вместо 'ла' и т.д.). Пример: \"PUДрuuLaa\" (это завуалированное \"пидрила\"), \"nидop\" (это \"пидор\"), \"мaть шлюxа\" с латинскими буквами.\n" +
        "2. Использование смеси русских и латинских букв в пределах одного слова (mixed alphabets / homoglyphs).\n" +
        "3. Написание слов с пробелами между буквами или другими символами (например: \"п и д о р\", \"п.и.д.о.р\", \"пі Д ОOOr\").\n" +
        "4. Удвоение или растягивание букв (например: \"пидооорааас\", \"пиидрилаа\").\n" +
        "5. Транслитерацию русских матов латиницей (например: \"pidor\", \"blyat\", \"shluha\").\n\n" +
        "Если ты обнаружил хотя бы малейший намек на завуалированное или прямое нарушение правил (мат, оскорбления, оскорбления родных и т.д.), ты ДОЛЖЕН:\n" +
        "- Установить \"violation\": true\n" +
        "- Определить вероятность \"probability\" (если это явный обход мата, вероятность должна быть близка к 1.0, например 0.95-1.0)\n" +
        "- Добавить это слово или фразу в том виде, в каком её написал игрок (со всеми обходами, пробелами, неверными буквами) в список \"bad_words\". Пример: если игрок написал \"ты PUДрuuLaa\", то в \"bad_words\" должно быть [\"PUДрuuLaa\"].\n\n" +
        "%examples_plus%" +
        "%examples_minus%" +
        "Ответь строго в формате JSON: {\"violation\": true/false, \"rule\": \"id\", \"probability\": 0.0-1.0, \"bad_words\": [\"оригинальное_слово_с_обходом\"]}";

    private final HttpClient http;
    private final JavaChat plugin;
    private final Gson gson = new Gson();
    private final String key;
    private final String model;

    public GroqApi(JavaChat plugin, String key, String model) {
        this.plugin = plugin;
        this.key = key;
        this.model = (model == null || model.isEmpty()) ? "llama-3.1-8b-instant" : model;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public CompletableFuture<AiResult> checkMsg(String msg, Map<String, AiRules.RuleInfo> rules, List<String> plus,
            List<String> minus) {
        if (key == null || key.isEmpty())
            return CompletableFuture.completedFuture(null);

        String context = rules.values().stream()
                .map(r -> "- " + r.id + ": " + r.description)
                .collect(Collectors.joining("\n"));

        String exPlus = "";
        if (plus != null && !plus.isEmpty()) {
            StringBuilder sb = new StringBuilder("Примеры сообщений, которые НЕ нарушают правила (их НЕ нужно наказывать):\n");
            plus.forEach(p -> sb.append("- ").append(p).append("\n"));
            sb.append("\n");
            exPlus = sb.toString();
        }

        String exMinus = "";
        if (minus != null && !minus.isEmpty()) {
            StringBuilder sb = new StringBuilder("Примеры сообщений, которые НАРУШАЮТ правила (их обязательно нужно наказать):\n");
            minus.forEach(m -> sb.append("- ").append(m).append("\n"));
            sb.append("\n");
            exMinus = sb.toString();
        }

        String promptTemplate = plugin.getConfig().getString("ai-helper.system-prompt", DEFAULT_PROMPT);
        String prompt = promptTemplate
                .replace("%rules%", context)
                .replace("%examples_plus%", exPlus)
                .replace("%examples_minus%", exMinus);

        JsonObject sysMsg = new JsonObject();
        sysMsg.addProperty("role", "system");
        sysMsg.addProperty("content", prompt);

        JsonObject usrMsg = new JsonObject();
        usrMsg.addProperty("role", "user");
        String userPromptFormat = plugin.getConfig().getString("ai-helper.user-prompt-format", "Сообщение: %message%");
        usrMsg.addProperty("content", userPromptFormat.replace("%message%", msg));

        JsonArray arr = new JsonArray();
        arr.add(sysMsg);
        arr.add(usrMsg);

        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.add("messages", arr);
        body.addProperty("temperature", 0.0);
        body.add("response_format", gson.fromJson("{\"type\": \"json_object\"}", JsonObject.class));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + key)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();

        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenApply(res -> {
            if (res.statusCode() >= 200 && res.statusCode() < 300) {
                try {
                    JsonObject json = gson.fromJson(res.body(), JsonObject.class);
                    String content = json.getAsJsonArray("choices").get(0).getAsJsonObject()
                            .getAsJsonObject("message").get("content").getAsString();
                    return gson.fromJson(content, AiResult.class);
                } catch (Exception e) {
                    plugin.getLogger().warning("Error parsing Groq JSON: " + e.getMessage());
                }
            } else {
                plugin.getLogger().warning("Groq API Error (" + res.statusCode() + "): " + res.body());
            }
            return null;
        });
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
