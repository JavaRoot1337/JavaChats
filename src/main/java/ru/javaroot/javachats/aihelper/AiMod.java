package ru.javaroot.javachats.aihelper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import ru.javaroot.javachats.JavaChat;
import ru.javaroot.javachats.utils.TextUtil;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

public class AiMod {
    private final JavaChat plugin;
    private final AiRules rules;
    private MistralApi mistralApi;
    private GroqApi groqApi;

    private final Queue<PendingMessage> queue = new ConcurrentLinkedQueue<>();
    private BukkitTask processTask;
    private long lastCheckMistral = 0;
    private long lastCheckGroq = 0;

    public AiMod(JavaChat plugin) {
        this.plugin = plugin;
        this.rules = new AiRules(plugin);
        reload();
    }

    public void reload() {
        rules.load();

        boolean mistralEnabled = plugin.getConfig().getBoolean("ai-helper.mistral-api.enable", false);
        String mistralKey = plugin.getConfig().getString("ai-helper.mistral-api.api-key", "");
        mistralApi = (mistralEnabled && !mistralKey.isEmpty()) ? new MistralApi(plugin, mistralKey) : null;

        boolean groqEnabled = plugin.getConfig().getBoolean("ai-helper.groq-api.enable", false);
        String groqKey = plugin.getConfig().getString("ai-helper.groq-api.api-key", "");
        String groqModel = plugin.getConfig().getString("ai-helper.groq-api.model", "llama-3.1-8b-instant");
        groqApi = (groqEnabled && !groqKey.isEmpty()) ? new GroqApi(plugin, groqKey, groqModel) : null;

        if (processTask != null) {
            processTask.cancel();
            processTask = null;
        }

        queue.clear();

        boolean anyBlockMode = (mistralApi != null
                && "block".equalsIgnoreCase(plugin.getConfig().getString("ai-helper.mistral-api.mode", "block")))
                || (groqApi != null
                        && "block".equalsIgnoreCase(plugin.getConfig().getString("ai-helper.groq-api.mode", "block")));

        if (anyBlockMode) {
            long cooldownSecs = 4;
            if (mistralApi != null
                    && "block".equalsIgnoreCase(plugin.getConfig().getString("ai-helper.mistral-api.mode", "block"))) {
                cooldownSecs = Math.min(cooldownSecs, plugin.getConfig().getLong("ai-helper.mistral-api.cooldown", 4));
            }
            if (groqApi != null
                    && "block".equalsIgnoreCase(plugin.getConfig().getString("ai-helper.groq-api.mode", "block"))) {
                cooldownSecs = Math.min(cooldownSecs, plugin.getConfig().getLong("ai-helper.groq-api.cooldown", 4));
            }
            processTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::processNext, 20L,
                    cooldownSecs * 20L);
        }
    }

    public void handleMsg(Player p, String msg) {
        boolean mistralBlock = plugin.getConfig().getBoolean("ai-helper.mistral-api.enable", false)
                && "block".equalsIgnoreCase(plugin.getConfig().getString("ai-helper.mistral-api.mode", "block"))
                && mistralApi != null;

        boolean groqBlock = plugin.getConfig().getBoolean("ai-helper.groq-api.enable", false)
                && "block".equalsIgnoreCase(plugin.getConfig().getString("ai-helper.groq-api.mode", "block"))
                && groqApi != null;

        if (!mistralBlock && !groqBlock)
            return;

        if (queue.size() > 50)
            return;
        queue.add(new PendingMessage(p.getName(), msg));
    }

    public String censorIfViolation(Player p, String msg) {
        long now = System.currentTimeMillis();

        boolean mistralEnabled = plugin.getConfig().getBoolean("ai-helper.mistral-api.enable", false);
        String mistralMode = plugin.getConfig().getString("ai-helper.mistral-api.mode", "censor");
        boolean useMistral = mistralEnabled && "censor".equalsIgnoreCase(mistralMode) && (mistralApi != null);
        long mistralCooldownMs = plugin.getConfig().getLong("ai-helper.mistral-api.cooldown", 4) * 1000L;

        boolean groqEnabled = plugin.getConfig().getBoolean("ai-helper.groq-api.enable", false);
        String groqMode = plugin.getConfig().getString("ai-helper.groq-api.mode", "censor");
        boolean useGroq = groqEnabled && "censor".equalsIgnoreCase(groqMode) && (groqApi != null);
        long groqCooldownMs = plugin.getConfig().getLong("ai-helper.groq-api.cooldown", 4) * 1000L;

        if (useMistral && (now - lastCheckMistral < mistralCooldownMs)) {
            useMistral = false;
        }
        if (useGroq && (now - lastCheckGroq < groqCooldownMs)) {
            useGroq = false;
        }

        if (!useMistral && !useGroq) {
            return null;
        }

        if (useMistral)
            lastCheckMistral = now;
        if (useGroq)
            lastCheckGroq = now;

        try {
            List<String> plus = rules.getTrainingPlus();
            List<String> minus = rules.getTrainingMinus();

            CompletableFuture<MistralApi.AiResult> mistralFuture = useMistral
                    ? mistralApi.checkMsg(msg, rules.getRules(), plus, minus)
                    : CompletableFuture.completedFuture(null);

            CompletableFuture<GroqApi.AiResult> groqFuture = useGroq
                    ? groqApi.checkMsg(msg, rules.getRules(), plus, minus)
                    : CompletableFuture.completedFuture(null);

            CompletableFuture.allOf(mistralFuture, groqFuture).get(5, TimeUnit.SECONDS);

            MistralApi.AiResult mRes = mistralFuture.join();
            GroqApi.AiResult gRes = groqFuture.join();

            String censored = msg;
            boolean violation = false;

            if (mRes != null) {
                double threshold = plugin.getConfig().getDouble("ai-helper.mistral-api.punish-probability", 0.85);
                if (mRes.violation && mRes.probability >= threshold) {
                    violation = true;
                    if (mRes.bad_words != null && !mRes.bad_words.isEmpty()) {
                        for (String bw : mRes.bad_words) {
                            censored = replaceCaseInsensitive(censored, bw);
                        }
                    } else {
                        censored = censorMessage(censored);
                    }
                }
            }

            if (gRes != null) {
                double threshold = plugin.getConfig().getDouble("ai-helper.groq-api.punish-probability", 0.85);
                if (gRes.violation && gRes.probability >= threshold) {
                    violation = true;
                    if (gRes.bad_words != null && !gRes.bad_words.isEmpty()) {
                        for (String bw : gRes.bad_words) {
                            censored = replaceCaseInsensitive(censored, bw);
                        }
                    } else {
                        censored = censorMessage(censored);
                    }
                }
            }

            if (violation) {
                String subtitleText = plugin.getMessageConfig().getString("ai-helper.subtitle",
                        "&cНе нарушайте правила сервера!");
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (p.isOnline()) {
                        p.showTitle(Title.title(Component.empty(), TextUtil.format(subtitleText)));
                    }
                });
                return censored;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка при цензурировании сообщения ИИ: " + e.getMessage());
        }

        return null;
    }

    private String replaceCaseInsensitive(String source, String target) {
        if (source == null || target == null || target.isEmpty())
            return source;

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(target),
                java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher matcher = pattern.matcher(source);

        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String matched = matcher.group();
            String censored = censorBadWord(matched);
            matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(censored));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String censorBadWord(String badWord) {
        if (badWord == null || badWord.isEmpty())
            return "";
        if (badWord.length() <= 2) {
            return "*".repeat(badWord.length());
        }
        char first = badWord.charAt(0);
        char last = badWord.charAt(badWord.length() - 1);
        StringBuilder sb = new StringBuilder();
        sb.append(first);
        for (int i = 1; i < badWord.length() - 1; i++) {
            char c = badWord.charAt(i);
            if (Character.isWhitespace(c)) {
                sb.append(' ');
            } else {
                sb.append('*');
            }
        }
        sb.append(last);
        return sb.toString();
    }

    private String censorMessage(String msg) {
        if (msg == null || msg.isEmpty())
            return "";
        String[] words = msg.split("(?<=\\s)|(?=\\s)");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.trim().isEmpty()) {
                sb.append(w);
            } else {
                if (w.length() <= 2) {
                    sb.append(w);
                } else {
                    sb.append(w.charAt(0));
                    for (int i = 1; i < w.length() - 1; i++) {
                        sb.append('*');
                    }
                    sb.append(w.charAt(w.length() - 1));
                }
            }
        }
        return sb.toString();
    }

    private void processNext() {
        PendingMessage pending = queue.poll();
        if (pending == null)
            return;

        List<String> plus = rules.getTrainingPlus();
        List<String> minus = rules.getTrainingMinus();

        if (mistralApi != null
                && "block".equalsIgnoreCase(plugin.getConfig().getString("ai-helper.mistral-api.mode", "block"))) {
            mistralApi.checkMsg(pending.msg, rules.getRules(), plus, minus).thenAccept(res -> {
                if (res != null) {
                    processAsyncResult(pending, res, "Mistral", "ai-helper.mistral-api");
                }
            });
        }

        if (groqApi != null
                && "block".equalsIgnoreCase(plugin.getConfig().getString("ai-helper.groq-api.mode", "block"))) {
            groqApi.checkMsg(pending.msg, rules.getRules(), plus, minus).thenAccept(res -> {
                if (res != null) {
                    MistralApi.AiResult dummy = new MistralApi.AiResult();
                    dummy.violation = res.violation;
                    dummy.rule = res.rule;
                    dummy.probability = res.probability;
                    dummy.bad_words = res.bad_words;
                    processAsyncResult(pending, dummy, "Groq (" + groqApi.getModelName() + ")", "ai-helper.groq-api");
                }
            });
        }
    }

    private void processAsyncResult(PendingMessage pending, MistralApi.AiResult res, String modelName,
            String configPath) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            double threshold = plugin.getConfig().getDouble(configPath + ".punish-probability", 0.85);
            String verdict = plugin.getMessageConfig().getString("ai-helper.verdict-clean", "&aЧИСТ");

            if (res.violation && res.probability >= threshold) {
                verdict = plugin.getMessageConfig().getString("ai-helper.verdict-punished", "&4НАКАЗАН");
                AiRules.RuleInfo info = rules.getRules().get(res.rule);
                if (info != null && info.punishCmd != null) {
                    String cmd = info.punishCmd
                            .replace("%player%", pending.playerName)
                            .replace("%rule%", info.id)
                            .replace("%probability%", String.valueOf(res.probability));
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                }
            }

            String logMsg = plugin.getMessageConfig().getString("ai-helper.log-message", "")
                    .replace("%player%", pending.playerName)
                    .replace("%message%", pending.msg);

            String logRes = plugin.getMessageConfig().getString("ai-helper.log-result", "")
                    .replace("%model_ai%", modelName)
                    .replace("%probability%", String.valueOf(res.probability))
                    .replace("%verdict%", verdict);

            boolean isPunished = res.violation && res.probability >= threshold;
            boolean allLogs = plugin.getConfig().getBoolean(configPath + ".all-logs", true);

            if (isPunished || allLogs) {
                notifyAdmins(logMsg);
                notifyAdmins(logRes);
            }
        });
    }

    private void notifyAdmins(String txt) {
        if (txt == null || txt.isEmpty())
            return;
        net.kyori.adventure.text.Component comp = TextUtil.format(txt);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("javachats.admin") || p.hasPermission("javachats.ai.log")) {
                p.sendMessage(comp);
            }
        }
        Bukkit.getConsoleSender().sendMessage(comp);
    }

    public AiRules getRules() {
        return rules;
    }

    private static class PendingMessage {
        final String playerName;
        final String msg;

        PendingMessage(String playerName, String msg) {
            this.playerName = playerName;
            this.msg = msg;
        }
    }
}
