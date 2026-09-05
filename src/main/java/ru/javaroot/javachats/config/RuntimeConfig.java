package ru.javaroot.javachats.config;

import org.bukkit.configuration.file.FileConfiguration;
import ru.javaroot.javachats.api.ChatChannel;

public record RuntimeConfig(Channel local, Channel global, Ping ping, JoinQuit joinQuit, AntiCaps antiCaps,
        AntiSpam antiSpam, boolean antiRepeat, Ai ai, String censorSuffix) {
    public static RuntimeConfig from(FileConfiguration cfg) {
        return new RuntimeConfig(
                channel(cfg, "chats.local"),
                channel(cfg, "chats.global"),
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
                        cfg.getLong("settings.anti-caps.fade-in-ms"),
                        cfg.getLong("settings.anti-caps.stay-ms"),
                        cfg.getLong("settings.anti-caps.fade-out-ms")),
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
                cfg.getString("logs.chat.censor-suffix"));
    }

    public Channel channel(ChatChannel channel) {
        return channel == ChatChannel.GLOBAL ? global : local;
    }

    private static Channel channel(FileConfiguration cfg, String path) {
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

    public record Channel(boolean enabled, int range, long cooldownSeconds, String symbol,
            String viewingPermission, String sendingPermission, String sound, double volume, double pitch) {
    }

    public record Ping(boolean enabled, long fadeInTicks, long stayTicks, long fadeOutTicks, boolean soundEnabled,
            String sound, String soundCategory, double volume, double pitch) {
    }

    public record JoinQuit(boolean joinEnabled, boolean quitEnabled) {
    }

    public record AntiCaps(int percent, int minLength, long fadeInMs, long stayMs, long fadeOutMs) {
    }

    public record AntiSpam(boolean enabled, long delayMs, int strikes, long strikeDelayTicks, long fadeInMs,
            long stayMs, long fadeOutMs) {
    }

    public record Ai(String systemPrompt, String userPromptFormat, double temperature, long censorTimeoutSeconds,
            long blockInitialDelayTicks, int blockMaxQueueSize, CensorTitle censorTitle, Provider mistral,
            Provider groq) {
        private static Ai from(FileConfiguration cfg) {
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
                    provider(cfg, "ai-helper.mistral-api"),
                    provider(cfg, "ai-helper.groq-api"));
        }

        private static Provider provider(FileConfiguration cfg, String path) {
            return new Provider(
                    cfg.getBoolean(path + ".enable"),
                    cfg.getString(path + ".endpoint"),
                    cfg.getString(path + ".model"),
                    cfg.getLong(path + ".timeout-seconds"),
                    cfg.getLong(path + ".cooldown"),
                    cfg.getBoolean(path + ".all-logs"),
                    cfg.getString(path + ".mode"),
                    cfg.getDouble(path + ".punish-probability"));
        }
    }

    public record CensorTitle(long fadeInMs, long stayMs, long fadeOutMs) {
    }

    public record Provider(boolean enabled, String endpoint, String model, long timeoutSeconds,
            long cooldownSeconds, boolean allLogs, String mode, double punishProbability) {
    }
}
