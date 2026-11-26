package ru.javaroot.javachats.cfg;

import org.bukkit.configuration.file.FileConfiguration;
import ru.javaroot.javachats.api.ChatChan;

import java.net.URI;

public record Cfg(
        Channel local,
        Channel global,
        Ping ping,
        JoinQuit joinQuit,
        AntiCaps antiCaps,
        AntiSpam antiSpam,
        boolean antiRepeat,
        Ai ai,
        String censorSuffix,
        boolean updateCheck) {

    public static Cfg from(FileConfiguration cfg) {
        return new Cfg(
                readChannel(cfg, "chats.local"),
                readChannel(cfg, "chats.global"),
                new Ping(
                        cfg.getBoolean("ping-chat.enabled"),
                        cfg.getLong("ping-chat.title.fade-in"),
                        cfg.getLong("ping-chat.title.stay"),
                        cfg.getLong("ping-chat.title.fade-out"),
                        cfg.getBoolean("ping-chat.sound.enable"),
                        cfg.getString("ping-chat.sound.name"),
                        cfg.getString("ping-chat.sound.category"),
                        cfg.getDouble("ping-chat.sound.volume"),
                        cfg.getDouble("ping-chat.sound.pitch")),
                new JoinQuit(
                        cfg.getBoolean("join-quit.join.enable"),
                        cfg.getBoolean("join-quit.quit.enable")),
                new AntiCaps(
                        cfg.getInt("settings.anti-caps-percent"),
                        cfg.getInt("settings.anti-caps-min-length"),
                        longVal(cfg, "settings.anti-caps-fade-in-ms", "settings.anti-caps.fade-in-ms"),
                        longVal(cfg, "settings.anti-caps-stay-ms", "settings.anti-caps.stay-ms"),
                        longVal(cfg, "settings.anti-caps-fade-out-ms", "settings.anti-caps.fade-out-ms")),
                new AntiSpam(
                        cfg.getBoolean("anti-spam.enable"),
                        cfg.getLong("anti-spam.delay-ms"),
                        cfg.getInt("anti-spam.strikes"),
                        cfg.getLong("anti-spam.strike-delay-ticks"),
                        cfg.getLong("anti-spam.fade-in-ms"),
                        cfg.getLong("anti-spam.stay-ms"),
                        cfg.getLong("anti-spam.fade-out-ms")),
                cfg.getBoolean("anti-repeat.enable"),
                Ai.from(cfg),
                cfg.getString("logs.chat.censor-suffix"),
                cfg.getBoolean("update-check"));
    }

    public Channel channel(ChatChan chan) {
        return chan == ChatChan.GLOBAL ? global : local;
    }

    public void validate() {
        validateChannel(local, "chats.local");
        validateChannel(global, "chats.global");
        if (antiCaps.percent() < 0 || antiCaps.percent() > 100 || antiCaps.minLength() < 0) {
            throw new IllegalArgumentException("некорректные настройки антикапса");
        }
        if (antiCaps.fadeInMs() < 0 || antiCaps.stayMs() < 0 || antiCaps.fadeOutMs() < 0) {
            throw new IllegalArgumentException("некорректное время заголовка антикапса");
        }
        if (antiSpam.delayMs() < 0 || antiSpam.strikes() < 0 || antiSpam.strikeDelayTicks() < 0) {
            throw new IllegalArgumentException("некорректные настройки антиспама");
        }
        if (antiSpam.fadeInMs() < 0 || antiSpam.stayMs() < 0 || antiSpam.fadeOutMs() < 0) {
            throw new IllegalArgumentException("некорректное время заголовка антиспама");
        }
        if (ping.fadeInTicks() < 0 || ping.stayTicks() < 0 || ping.fadeOutTicks() < 0
                || ping.volume() < 0 || ping.pitch() < 0) {
            throw new IllegalArgumentException("некорректные настройки пинга");
        }
        if (ai.blockMaxQueueSize() < 1 || ai.blockInitialDelayTicks() < 0 || ai.censorTimeoutSeconds() < 0
                || Double.isNaN(ai.temperature()) || Double.isInfinite(ai.temperature())) {
            throw new IllegalArgumentException("некорректные общие настройки AI");
        }
        if (ai.censorTitle().fadeInMs() < 0 || ai.censorTitle().stayMs() < 0 || ai.censorTitle().fadeOutMs() < 0) {
            throw new IllegalArgumentException("некорректное время заголовка цензуры AI");
        }
        validateProvider(ai.mistral(), "ai-helper.mistral-api");
        validateProvider(ai.groq(), "ai-helper.groq-api");
    }

    private static long longVal(FileConfiguration cfg, String primary, String fallback) {
        return cfg.contains(primary) ? cfg.getLong(primary) : cfg.getLong(fallback);
    }

    private static Channel readChannel(FileConfiguration cfg, String path) {
        return new Channel(
                cfg.getBoolean(path + ".enable"),
                cfg.getInt(path + ".range"),
                cfg.getLong(path + ".cooldown"),
                cfg.getString(path + ".symbol"),
                cfg.getString(path + ".viewing-perms"),
                cfg.getString(path + ".sending-perms"),
                cfg.getString(path + ".sound"),
                cfg.getDouble(path + ".volume"),
                cfg.getDouble(path + ".pitch"));
    }

    private static void validateChannel(Channel chan, String path) {
        if (chan.range() < -1 || chan.cooldownSeconds() < -1 || chan.volume() < 0 || chan.pitch() < 0) {
            throw new IllegalArgumentException("некорректные настройки канала " + path);
        }
    }

    private static void validateProvider(Provider prov, String path) {
        if (prov.timeoutSeconds() < 1 || prov.cooldownSeconds() < 0
                || prov.punishProbability() < 0 || prov.punishProbability() > 1) {
            throw new IllegalArgumentException("некорректные параметры провайдера " + path);
        }
        if (!"censor".equalsIgnoreCase(prov.mode()) && !"block".equalsIgnoreCase(prov.mode())) {
            throw new IllegalArgumentException("неизвестный режим провайдера " + path);
        }
        try {
            URI uri = URI.create(prov.endpoint());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("некорректный адрес провайдера " + path);
            }
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("некорректный адрес провайдера " + path, ex);
        }
    }

    public record Channel(
            boolean enabled,
            int range,
            long cooldownSeconds,
            String symbol,
            String viewingPermission,
            String sendingPermission,
            String sound,
            double volume,
            double pitch) {
    }

    public record Ping(
            boolean enabled,
            long fadeInTicks,
            long stayTicks,
            long fadeOutTicks,
            boolean soundEnabled,
            String sound,
            String soundCategory,
            double volume,
            double pitch) {
    }

    public record JoinQuit(boolean joinEnabled, boolean quitEnabled) {
    }

    public record AntiCaps(int percent, int minLength, long fadeInMs, long stayMs, long fadeOutMs) {
    }

    public record AntiSpam(
            boolean enabled,
            long delayMs,
            int strikes,
            long strikeDelayTicks,
            long fadeInMs,
            long stayMs,
            long fadeOutMs) {
    }

    public record CensorTitle(long fadeInMs, long stayMs, long fadeOutMs) {
    }

    public record Provider(
            boolean enabled,
            String apiKey,
            String endpoint,
            String model,
            long timeoutSeconds,
            long cooldownSeconds,
            boolean allLogs,
            String mode,
            double punishProbability) {
    }

    public record Ai(
            String systemPrompt,
            String userPromptFormat,
            double temperature,
            long censorTimeoutSeconds,
            long blockInitialDelayTicks,
            int blockMaxQueueSize,
            CensorTitle censorTitle,
            Provider mistral,
            Provider groq,
            FailurePolicy failurePolicy) {

        public enum FailurePolicy { ALLOW, BLOCK }

        private static Ai from(FileConfiguration cfg) {
            String raw = cfg.getString("ai-helper.failure-policy");
            FailurePolicy policy;
            if (raw == null || raw.trim().isEmpty() || "allow".equalsIgnoreCase(raw)) {
                policy = FailurePolicy.ALLOW;
            } else if ("block".equalsIgnoreCase(raw)) {
                policy = FailurePolicy.BLOCK;
            } else {
                throw new IllegalArgumentException("ai-helper.failure-policy должен быть allow или block");
            }
            return new Ai(
                    cfg.getString("ai-helper.system-prompt"),
                    cfg.getString("ai-helper.user-prompt-format"),
                    cfg.getDouble("ai-helper.temperature"),
                    cfg.getLong("ai-helper.censor-timeout-seconds"),
                    cfg.getLong("ai-helper.block.initial-delay-ticks"),
                    cfg.getInt("ai-helper.block.max-queue-size"),
                    new CensorTitle(
                            cfg.getLong("ai-helper.censor-title.fade-in-ms"),
                            cfg.getLong("ai-helper.censor-title.stay-ms"),
                            cfg.getLong("ai-helper.censor-title.fade-out-ms")),
                    readProvider(cfg, "ai-helper.mistral-api"),
                    readProvider(cfg, "ai-helper.groq-api"),
                    policy);
        }

        private static Provider readProvider(FileConfiguration cfg, String path) {
            return new Provider(
                    cfg.getBoolean(path + ".enable"),
                    cfg.getString(path + ".api-key"),
                    cfg.getString(path + ".endpoint"),
                    cfg.getString(path + ".model"),
                    cfg.getLong(path + ".timeout-seconds"),
                    cfg.getLong(path + ".cooldown"),
                    cfg.getBoolean(path + ".all-logs"),
                    cfg.getString(path + ".mode"),
                    cfg.getDouble(path + ".punish-probability"));
        }
    }
}
