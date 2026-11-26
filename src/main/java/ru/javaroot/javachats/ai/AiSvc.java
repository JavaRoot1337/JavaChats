package ru.javaroot.javachats.ai;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import ru.javaroot.javachats.api.ModRes;
import ru.javaroot.javachats.api.ModSvc;
import ru.javaroot.javachats.cfg.Cfg;
import ru.javaroot.javachats.cfg.LogCfg;
import ru.javaroot.javachats.cfg.Msg;
import ru.javaroot.javachats.plat.Exec;
import ru.javaroot.javachats.plat.Hex;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AiSvc implements ModSvc, AutoCloseable {
    private static final int MAX_LEN = 4096;

    private final Plugin plug;
    private final LogCfg logs;
    private final Exec exec;
    private final Supplier<Msg> msgSupplier;
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "JavaChats-ai-timeout");
        t.setDaemon(true);
        return t;
    });

    private volatile AiRules rules;
    private volatile Cfg.Ai aiCfg;
    private volatile OpenAiProv mistral;
    private volatile OpenAiProv groq;
    private volatile Semaphore queue = new Semaphore(1);

    public AiSvc(Plugin plug, LogCfg logs, Exec exec, Supplier<Msg> msgSupplier) {
        this.plug = plug;
        this.logs = logs;
        this.exec = exec;
        this.msgSupplier = msgSupplier;
        this.rules = new AiRules(plug, logs, exec, plug.getDataFolder());
    }

    public boolean reload(Cfg cfg, File profileDir) {
        AiRules nextRules = new AiRules(plug, logs, exec, profileDir);
        try {
            nextRules.load(cfg.ai().systemPrompt());
        } catch (RuntimeException ex) {
            logs.warning("config-validation", Map.of("error", String.valueOf(ex.getMessage())));
            return false;
        }

        OpenAiProv nextMistral = buildProvider(cfg.ai().mistral(), "mistral");
        if (isConfigured(cfg.ai().mistral()) && nextMistral == null) {
            return false;
        }
        OpenAiProv nextGroq = buildProvider(cfg.ai().groq(), "groq");
        if (isConfigured(cfg.ai().groq()) && nextGroq == null) {
            if (nextMistral != null) {
                nextMistral.close();
            }
            return false;
        }

        OpenAiProv oldMistral = mistral;
        OpenAiProv oldGroq = groq;
        rules = nextRules;
        aiCfg = cfg.ai();
        mistral = nextMistral;
        groq = nextGroq;
        queue = new Semaphore(cfg.ai().blockMaxQueueSize());

        if (oldMistral != null) {
            oldMistral.close();
        }
        if (oldGroq != null) {
            oldGroq.close();
        }
        return true;
    }

    @Override
    public CompletableFuture<ModRes> moderate(UUID playerId, String msg) {
        if (playerId == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("нужен playerId"));
        }
        Player online = Bukkit.getPlayer(playerId);
        String name = online != null ? online.getName() : playerId.toString();
        return moderate(playerId, name, msg);
    }

    public CompletableFuture<ModRes> moderate(UUID playerId, String playerName, String msg) {
        if (playerId == null || msg == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("нужны playerId и сообщение"));
        }
        if (msg.length() > MAX_LEN) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("сообщение слишком длинное"));
        }
        Cfg.Ai cfg = aiCfg;
        if (cfg == null) {
            return CompletableFuture.completedFuture(ModRes.clean());
        }

        Semaphore sem = queue;
        if (!sem.tryAcquire()) {
            logs.warning("moderation-request", Map.of("error", "очередь AI заполнена"));
            return CompletableFuture.completedFuture(failureResult(cfg));
        }

        boolean useMistral = isActive(cfg.mistral(), mistral);
        boolean useGroq = isActive(cfg.groq(), groq);
        if (!useMistral && !useGroq) {
            sem.release();
            return CompletableFuture.completedFuture(ModRes.clean());
        }

        AiRules currentRules = rules;
        List<String> plus = currentRules.getPlus();
        List<String> minus = currentRules.getMinus();
        String prompt = currentRules.getSystemPrompt();
        Map<String, AiRules.RuleInfo> ruleMap = currentRules.getRules();

        CompletableFuture<OpenAiProv.ProvRes> mFuture = useMistral
                ? delayed(cfg.blockInitialDelayTicks(), () -> mistral.check(msg, ruleMap, plus, minus, cfg, prompt))
                : CompletableFuture.completedFuture(null);
        CompletableFuture<OpenAiProv.ProvRes> gFuture = useGroq
                ? delayed(cfg.blockInitialDelayTicks(), () -> groq.check(msg, ruleMap, plus, minus, cfg, prompt))
                : CompletableFuture.completedFuture(null);

        int activeCount = (useMistral ? 1 : 0) + (useGroq ? 1 : 0);
        AtomicInteger failedCount = new AtomicInteger();
        CompletableFuture<OpenAiProv.ProvRes> mSafe = useMistral ? tolerate(mFuture, "Mistral", failedCount) : mFuture;
        CompletableFuture<OpenAiProv.ProvRes> gSafe = useGroq ? tolerate(gFuture, "Groq", failedCount) : gFuture;

        CompletableFuture<ModRes> combined = CompletableFuture.allOf(mSafe, gSafe).thenApply(ignored -> {
            OpenAiProv.ProvRes mVal = mSafe.join();
            OpenAiProv.ProvRes gVal = gSafe.join();
            boolean allFailed = activeCount > 0 && (failedCount.get() == activeCount || (mVal == null && gVal == null));
            return combine(playerId, msg, mVal, gVal, cfg, allFailed);
        });

        return withTimeout(combined, cfg.censorTimeoutSeconds(), mSafe, gSafe)
                .handle((res, err) -> {
                    if (err == null) {
                        return res;
                    }
                    logs.warning("moderation-request", Map.of("error", rootMessage(err)));
                    return failureResult(cfg);
                })
                .thenApply(res -> {
                    logModeration(playerName, msg, res, cfg);
                    return res;
                })
                .whenComplete((res, err) -> sem.release());
    }

    public void punish(String playerName, ModRes res) {
        if (playerName == null || res == null || !res.violation() || res.rule() == null) {
            return;
        }
        exec.runGlobal(() -> {
            AiRules.RuleInfo info = rules.getRules().get(res.rule());
            if (info == null || info.punishCmd() == null || info.punishCmd().isEmpty()) {
                return;
            }
            String cmd = info.punishCmd()
                    .replace("%player%", playerName)
                    .replace("%rule%", info.id())
                    .replace("%probability%", String.valueOf(res.probability()));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        });
    }

    public AiRules getRules() {
        return rules;
    }

    @Override
    public boolean isEnabled() {
        return aiCfg != null && (mistral != null || groq != null);
    }

    @Override
    public void close() {
        if (mistral != null) {
            mistral.close();
            mistral = null;
        }
        if (groq != null) {
            groq.close();
            groq = null;
        }
        timer.shutdownNow();
    }

    public static String censor(String source, String target) {
        if (target == null || target.isEmpty()) {
            return source;
        }
        Pattern pattern = Pattern.compile(Pattern.quote(target), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Matcher matcher = pattern.matcher(source);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(sb, Matcher.quoteReplacement(maskWord(matcher.group())));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private OpenAiProv buildProvider(Cfg.Provider prov, String key) {
        if (!isConfigured(prov)) {
            return null;
        }
        try {
            return new OpenAiProv(logs, prov, key);
        } catch (RuntimeException ex) {
            logs.warning(key + "-config", Map.of("error", String.valueOf(ex.getMessage())));
            return null;
        }
    }

    private static boolean isConfigured(Cfg.Provider prov) {
        return prov.enabled() && prov.apiKey() != null && !prov.apiKey().trim().isEmpty();
    }

    private static boolean isActive(Cfg.Provider prov, OpenAiProv client) {
        return client != null && prov.enabled()
                && ("censor".equalsIgnoreCase(prov.mode()) || "block".equalsIgnoreCase(prov.mode()));
    }

    private CompletableFuture<OpenAiProv.ProvRes> tolerate(CompletableFuture<OpenAiProv.ProvRes> future,
            String name, AtomicInteger failed) {
        return future.handle((res, err) -> {
            if (err != null || res == null) {
                failed.incrementAndGet();
                logs.warning("moderation-provider", Map.of(
                        "provider", name,
                        "error", err == null ? "пустой ответ" : rootMessage(err)));
            }
            return res;
        });
    }

    private ModRes combine(UUID playerId, String msg, OpenAiProv.ProvRes mVal, OpenAiProv.ProvRes gVal,
            Cfg.Ai cfg, boolean allFailed) {
        if (allFailed) {
            return failureResult(cfg);
        }
        String censored = msg;
        String rule = null;
        double probability = 0.0;
        boolean violation = false;
        boolean blocking = false;
        List<String> badWords = new ArrayList<>();

        if (isPunished(mVal, cfg.mistral().punishProbability())) {
            violation = true;
            blocking = "block".equalsIgnoreCase(cfg.mistral().mode());
            rule = mVal.rule();
            probability = mVal.probability();
            badWords.addAll(mVal.badWords());
            censored = maskAll(censored, mVal.badWords());
        }
        if (isPunished(gVal, cfg.groq().punishProbability())) {
            violation = true;
            blocking = blocking || "block".equalsIgnoreCase(cfg.groq().mode());
            if (gVal.probability() > probability) {
                rule = gVal.rule();
                probability = gVal.probability();
            }
            badWords.addAll(gVal.badWords());
            censored = maskAll(censored, gVal.badWords());
        }

        if (!violation) {
            return ModRes.clean();
        }
        showCensorTitle(playerId, cfg.censorTitle());
        return new ModRes(true, probability, rule, badWords, censored, true, blocking);
    }

    private static boolean isPunished(OpenAiProv.ProvRes res, double threshold) {
        return res != null && res.violation() && res.probability() >= threshold;
    }

    private static ModRes failureResult(Cfg.Ai cfg) {
        return cfg.failurePolicy() == Cfg.Ai.FailurePolicy.BLOCK ? ModRes.unavailable() : ModRes.clean();
    }

    private static String maskAll(String msg, List<String> words) {
        if (words == null || words.isEmpty()) {
            String[] parts = msg.split("(?<=\\s)|(?=\\s)");
            StringBuilder sb = new StringBuilder();
            for (String part : parts) {
                sb.append(part.trim().isEmpty() ? part : maskWord(part));
            }
            return sb.toString();
        }
        String out = msg;
        for (String word : words) {
            out = censor(out, word);
        }
        return out;
    }

    private static String maskWord(String word) {
        if (word.length() <= 2) {
            return "*".repeat(word.length());
        }
        StringBuilder sb = new StringBuilder(word.length()).append(word.charAt(0));
        for (int i = 1; i < word.length() - 1; i++) {
            sb.append(Character.isWhitespace(word.charAt(i)) ? ' ' : '*');
        }
        return sb.append(word.charAt(word.length() - 1)).toString();
    }

    private void showCensorTitle(UUID uuid, Cfg.CensorTitle titleCfg) {
        exec.runPlayer(uuid, () -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) {
                return;
            }
            Msg msg = msgSupplier.get();
            Title.Times times = Hex.times(titleCfg.fadeInMs(), titleCfg.stayMs(), titleCfg.fadeOutMs());
            p.showTitle(Title.title(Component.empty(), Hex.parse(msg.text("ai-helper.subtitle")), times));
        });
    }

    private void logModeration(String playerName, String message, ModRes res, Cfg.Ai cfg) {
        boolean allLogs = cfg.mistral().allLogs() || cfg.groq().allLogs();
        if (!allLogs && !res.violation()) {
            return;
        }
        Msg msg = msgSupplier.get();
        String msgLine = msg.text("ai-helper.log-message")
                .replace("%player%", playerName)
                .replace("%message%", message);
        String verdict = msg.text(res.violation() ? "ai-helper.verdict-punished" : "ai-helper.verdict-clean");
        String resLine = msg.text("ai-helper.log-result")
                .replace("%model_ai%", "AI")
                .replace("%probability%", String.valueOf(res.probability()))
                .replace("%verdict%", verdict);
        plug.getLogger().info(Hex.plain(Hex.parse(msgLine)));
        plug.getLogger().info(Hex.plain(Hex.parse(resLine)));
    }

    private <T> CompletableFuture<T> delayed(long delayTicks, Supplier<CompletableFuture<T>> supplier) {
        if (delayTicks <= 0) {
            return supplier.get();
        }
        CompletableFuture<T> out = new CompletableFuture<>();
        AtomicReference<CompletableFuture<T>> inner = new AtomicReference<>();
        ScheduledFuture<?> task = timer.schedule(() -> {
            if (out.isCancelled()) {
                return;
            }
            try {
                CompletableFuture<T> next = supplier.get();
                inner.set(next);
                if (out.isCancelled()) {
                    next.cancel(true);
                    return;
                }
                next.whenComplete((v, err) -> {
                    if (err == null) {
                        out.complete(v);
                    } else {
                        out.completeExceptionally(err);
                    }
                });
            } catch (RuntimeException ex) {
                out.completeExceptionally(ex);
            }
        }, delayTicks * 50L, TimeUnit.MILLISECONDS);
        out.whenComplete((v, err) -> {
            if (out.isCancelled()) {
                task.cancel(false);
                CompletableFuture<T> next = inner.get();
                if (next != null) {
                    next.cancel(true);
                }
            }
        });
        return out;
    }

    private CompletableFuture<ModRes> withTimeout(CompletableFuture<ModRes> future, long timeoutSec,
            CompletableFuture<?>... children) {
        if (timeoutSec <= 0) {
            return future;
        }
        ScheduledFuture<?> timeout = timer.schedule(() -> {
            for (CompletableFuture<?> child : children) {
                child.cancel(true);
            }
            future.completeExceptionally(new IllegalStateException("таймаут модерации"));
        }, timeoutSec, TimeUnit.SECONDS);
        future.whenComplete((v, err) -> timeout.cancel(false));
        return future;
    }

    private static String rootMessage(Throwable err) {
        Throwable cur = err;
        while (cur.getCause() != null) {
            cur = cur.getCause();
        }
        return String.valueOf(cur.getMessage());
    }
}
