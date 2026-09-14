package ru.javaroot.javachats.config;

import org.bukkit.configuration.file.FileConfiguration;
import ru.javaroot.javachats.api.ChatChannel;

public final class RuntimeConfig {
    private final Channel local;
    private final Channel global;
    private final Ping ping;
    private final JoinQuit joinQuit;
    private final AntiCaps antiCaps;
    private final AntiSpam antiSpam;
    private final boolean antiRepeat;
    private final Ai ai;
    private final String censorSuffix;

    public RuntimeConfig(Channel local, Channel global, Ping ping, JoinQuit joinQuit, AntiCaps antiCaps,
            AntiSpam antiSpam, boolean antiRepeat, Ai ai, String censorSuffix) {
        this.local = local;
        this.global = global;
        this.ping = ping;
        this.joinQuit = joinQuit;
        this.antiCaps = antiCaps;
        this.antiSpam = antiSpam;
        this.antiRepeat = antiRepeat;
        this.ai = ai;
        this.censorSuffix = censorSuffix;
    }

    public static RuntimeConfig from(FileConfiguration cfg) {
        return new RuntimeConfig(
                channel(cfg, "chats.local"),
                channel(cfg, "chats.global"),
                new Ping(cfg.getBoolean("ping-chat.enabled"), cfg.getLong("ping-chat.title.fade-in"),
                        cfg.getLong("ping-chat.title.stay"), cfg.getLong("ping-chat.title.fade-out"),
                        cfg.getBoolean("ping-chat.sound.enable"), cfg.getString("ping-chat.sound.name"),
                        cfg.getString("ping-chat.sound.category"), cfg.getDouble("ping-chat.sound.volume"),
                        cfg.getDouble("ping-chat.sound.pitch")),
                new JoinQuit(cfg.getBoolean("join-quit.join.enable"), cfg.getBoolean("join-quit.quit.enable")),
                new AntiCaps(cfg.getInt("settings.anti-caps-percent"), cfg.getInt("settings.anti-caps-min-length"),
                        cfg.getLong("settings.anti-caps.fade-in-ms"), cfg.getLong("settings.anti-caps.stay-ms"),
                        cfg.getLong("settings.anti-caps.fade-out-ms")),
                new AntiSpam(cfg.getBoolean("anti-spam.enable"), cfg.getLong("anti-spam.delay-ms"),
                        cfg.getInt("anti-spam.strikes"), cfg.getLong("anti-spam.strike-delay-ticks"),
                        cfg.getLong("anti-spam.fade-in-ms"), cfg.getLong("anti-spam.stay-ms"),
                        cfg.getLong("anti-spam.fade-out-ms")),
                cfg.getBoolean("anti-repeat.enable"), Ai.from(cfg), cfg.getString("logs.chat.censor-suffix"));
    }

    public Channel local() { return local; }
    public Channel global() { return global; }
    public Ping ping() { return ping; }
    public JoinQuit joinQuit() { return joinQuit; }
    public AntiCaps antiCaps() { return antiCaps; }
    public AntiSpam antiSpam() { return antiSpam; }
    public boolean antiRepeat() { return antiRepeat; }
    public Ai ai() { return ai; }
    public String censorSuffix() { return censorSuffix; }

    public void validate() {
        validateChannel(local, "chats.local");
        validateChannel(global, "chats.global");
        if (antiCaps.percent() < 0 || antiCaps.percent() > 100 || antiCaps.minLength() < 0) {
            throw new IllegalArgumentException("invalid anti-caps settings");
        }
        if (antiSpam.delayMs() < 0 || antiSpam.strikes() < 0 || antiSpam.strikeDelayTicks() < 0) {
            throw new IllegalArgumentException("invalid anti-spam settings");
        }
        if (ai.blockMaxQueueSize() < 1 || ai.censorTimeoutSeconds() < 0
                || Double.isNaN(ai.temperature()) || Double.isInfinite(ai.temperature())) {
            throw new IllegalArgumentException("invalid AI settings");
        }
        validateProvider(ai.mistral(), "ai-helper.mistral-api");
        validateProvider(ai.groq(), "ai-helper.groq-api");
    }

    private static void validateChannel(Channel channel, String path) {
        if (channel.range() < -1 || channel.cooldownSeconds() < -1
                || channel.volume() < 0 || channel.pitch() < 0) {
            throw new IllegalArgumentException("invalid channel settings: " + path);
        }
    }

    private static void validateProvider(Provider provider, String path) {
        if (provider.timeoutSeconds() < 1 || provider.cooldownSeconds() < 0
                || provider.punishProbability() < 0 || provider.punishProbability() > 1) {
            throw new IllegalArgumentException("invalid provider settings: " + path);
        }
    }

    public Channel channel(ChatChannel channel) {
        return channel == ChatChannel.GLOBAL ? global : local;
    }

    private static Channel channel(FileConfiguration cfg, String path) {
        return new Channel(cfg.getBoolean(path + ".enable"), cfg.getInt(path + ".range"),
                cfg.getLong(path + ".cooldown"), cfg.getString(path + ".symbol"),
                cfg.getString(path + ".viewing-perms"), cfg.getString(path + ".sending-perms"),
                cfg.getString(path + ".sound"), cfg.getDouble(path + ".volume"), cfg.getDouble(path + ".pitch"));
    }

    public static final class Channel {
        private final boolean enabled;
        private final int range;
        private final long cooldownSeconds;
        private final String symbol;
        private final String viewingPermission;
        private final String sendingPermission;
        private final String sound;
        private final double volume;
        private final double pitch;

        public Channel(boolean enabled, int range, long cooldownSeconds, String symbol, String viewingPermission,
                String sendingPermission, String sound, double volume, double pitch) {
            this.enabled = enabled;
            this.range = range;
            this.cooldownSeconds = cooldownSeconds;
            this.symbol = symbol;
            this.viewingPermission = viewingPermission;
            this.sendingPermission = sendingPermission;
            this.sound = sound;
            this.volume = volume;
            this.pitch = pitch;
        }

        public boolean enabled() { return enabled; }
        public int range() { return range; }
        public long cooldownSeconds() { return cooldownSeconds; }
        public String symbol() { return symbol; }
        public String viewingPermission() { return viewingPermission; }
        public String sendingPermission() { return sendingPermission; }
        public String sound() { return sound; }
        public double volume() { return volume; }
        public double pitch() { return pitch; }
    }

    public static final class Ping {
        private final boolean enabled;
        private final long fadeInTicks;
        private final long stayTicks;
        private final long fadeOutTicks;
        private final boolean soundEnabled;
        private final String sound;
        private final String soundCategory;
        private final double volume;
        private final double pitch;

        public Ping(boolean enabled, long fadeInTicks, long stayTicks, long fadeOutTicks, boolean soundEnabled,
                String sound, String soundCategory, double volume, double pitch) {
            this.enabled = enabled;
            this.fadeInTicks = fadeInTicks;
            this.stayTicks = stayTicks;
            this.fadeOutTicks = fadeOutTicks;
            this.soundEnabled = soundEnabled;
            this.sound = sound;
            this.soundCategory = soundCategory;
            this.volume = volume;
            this.pitch = pitch;
        }

        public boolean enabled() { return enabled; }
        public long fadeInTicks() { return fadeInTicks; }
        public long stayTicks() { return stayTicks; }
        public long fadeOutTicks() { return fadeOutTicks; }
        public boolean soundEnabled() { return soundEnabled; }
        public String sound() { return sound; }
        public String soundCategory() { return soundCategory; }
        public double volume() { return volume; }
        public double pitch() { return pitch; }
    }

    public static final class JoinQuit {
        private final boolean joinEnabled;
        private final boolean quitEnabled;

        public JoinQuit(boolean joinEnabled, boolean quitEnabled) {
            this.joinEnabled = joinEnabled;
            this.quitEnabled = quitEnabled;
        }

        public boolean joinEnabled() { return joinEnabled; }
        public boolean quitEnabled() { return quitEnabled; }
    }

    public static final class AntiCaps {
        private final int percent;
        private final int minLength;
        private final long fadeInMs;
        private final long stayMs;
        private final long fadeOutMs;

        public AntiCaps(int percent, int minLength, long fadeInMs, long stayMs, long fadeOutMs) {
            this.percent = percent;
            this.minLength = minLength;
            this.fadeInMs = fadeInMs;
            this.stayMs = stayMs;
            this.fadeOutMs = fadeOutMs;
        }

        public int percent() { return percent; }
        public int minLength() { return minLength; }
        public long fadeInMs() { return fadeInMs; }
        public long stayMs() { return stayMs; }
        public long fadeOutMs() { return fadeOutMs; }
    }

    public static final class AntiSpam {
        private final boolean enabled;
        private final long delayMs;
        private final int strikes;
        private final long strikeDelayTicks;
        private final long fadeInMs;
        private final long stayMs;
        private final long fadeOutMs;

        public AntiSpam(boolean enabled, long delayMs, int strikes, long strikeDelayTicks, long fadeInMs,
                long stayMs, long fadeOutMs) {
            this.enabled = enabled;
            this.delayMs = delayMs;
            this.strikes = strikes;
            this.strikeDelayTicks = strikeDelayTicks;
            this.fadeInMs = fadeInMs;
            this.stayMs = stayMs;
            this.fadeOutMs = fadeOutMs;
        }

        public boolean enabled() { return enabled; }
        public long delayMs() { return delayMs; }
        public int strikes() { return strikes; }
        public long strikeDelayTicks() { return strikeDelayTicks; }
        public long fadeInMs() { return fadeInMs; }
        public long stayMs() { return stayMs; }
        public long fadeOutMs() { return fadeOutMs; }
    }

    public static final class Ai {
        public enum FailurePolicy { ALLOW, BLOCK }

        private final String systemPrompt;
        private final String userPromptFormat;
        private final double temperature;
        private final long censorTimeoutSeconds;
        private final long blockInitialDelayTicks;
        private final int blockMaxQueueSize;
        private final CensorTitle censorTitle;
        private final Provider mistral;
        private final Provider groq;
        private final FailurePolicy failurePolicy;

        private Ai(String systemPrompt, String userPromptFormat, double temperature, long censorTimeoutSeconds,
                long blockInitialDelayTicks, int blockMaxQueueSize, CensorTitle censorTitle, Provider mistral,
                Provider groq, FailurePolicy failurePolicy) {
            this.systemPrompt = systemPrompt;
            this.userPromptFormat = userPromptFormat;
            this.temperature = temperature;
            this.censorTimeoutSeconds = censorTimeoutSeconds;
            this.blockInitialDelayTicks = blockInitialDelayTicks;
            this.blockMaxQueueSize = blockMaxQueueSize;
            this.censorTitle = censorTitle;
            this.mistral = mistral;
            this.groq = groq;
            this.failurePolicy = failurePolicy;
        }

        private static Ai from(FileConfiguration cfg) {
            String policy = cfg.getString("ai-helper.failure-policy");
            FailurePolicy failurePolicy;
            if (policy == null || policy.trim().isEmpty() || "allow".equalsIgnoreCase(policy)) {
                failurePolicy = FailurePolicy.ALLOW;
            } else if ("block".equalsIgnoreCase(policy)) {
                failurePolicy = FailurePolicy.BLOCK;
            } else {
                throw new IllegalArgumentException("ai-helper.failure-policy must be allow or block");
            }
            return new Ai(cfg.getString("ai-helper.system-prompt"), cfg.getString("ai-helper.user-prompt-format"),
                    cfg.getDouble("ai-helper.temperature"), cfg.getLong("ai-helper.censor-timeout-seconds"),
                    cfg.getLong("ai-helper.block.initial-delay-ticks"), cfg.getInt("ai-helper.block.max-queue-size"),
                    new CensorTitle(cfg.getLong("ai-helper.censor-title.fade-in-ms"),
                            cfg.getLong("ai-helper.censor-title.stay-ms"),
                            cfg.getLong("ai-helper.censor-title.fade-out-ms")),
                    provider(cfg, "ai-helper.mistral-api"), provider(cfg, "ai-helper.groq-api"), failurePolicy);
        }

        private static Provider provider(FileConfiguration cfg, String path) {
            return new Provider(cfg.getBoolean(path + ".enable"), cfg.getString(path + ".api-key"),
                    cfg.getString(path + ".endpoint"),
                    cfg.getString(path + ".model"), cfg.getLong(path + ".timeout-seconds"),
                    cfg.getLong(path + ".cooldown"), cfg.getBoolean(path + ".all-logs"),
                    cfg.getString(path + ".mode"), cfg.getDouble(path + ".punish-probability"));
        }

        public String systemPrompt() { return systemPrompt; }
        public String userPromptFormat() { return userPromptFormat; }
        public double temperature() { return temperature; }
        public long censorTimeoutSeconds() { return censorTimeoutSeconds; }
        public long blockInitialDelayTicks() { return blockInitialDelayTicks; }
        public int blockMaxQueueSize() { return blockMaxQueueSize; }
        public CensorTitle censorTitle() { return censorTitle; }
        public Provider mistral() { return mistral; }
        public Provider groq() { return groq; }
        public FailurePolicy failurePolicy() { return failurePolicy; }
    }

    public static final class CensorTitle {
        private final long fadeInMs;
        private final long stayMs;
        private final long fadeOutMs;

        public CensorTitle(long fadeInMs, long stayMs, long fadeOutMs) {
            this.fadeInMs = fadeInMs;
            this.stayMs = stayMs;
            this.fadeOutMs = fadeOutMs;
        }

        public long fadeInMs() { return fadeInMs; }
        public long stayMs() { return stayMs; }
        public long fadeOutMs() { return fadeOutMs; }
    }

    public static final class Provider {
        private final boolean enabled;
        private final String apiKey;
        private final String endpoint;
        private final String model;
        private final long timeoutSeconds;
        private final long cooldownSeconds;
        private final boolean allLogs;
        private final String mode;
        private final double punishProbability;

        public Provider(boolean enabled, String apiKey, String endpoint, String model, long timeoutSeconds,
                long cooldownSeconds,
                boolean allLogs, String mode, double punishProbability) {
            this.enabled = enabled;
            this.apiKey = apiKey;
            this.endpoint = endpoint;
            this.model = model;
            this.timeoutSeconds = timeoutSeconds;
            this.cooldownSeconds = cooldownSeconds;
            this.allLogs = allLogs;
            this.mode = mode;
            this.punishProbability = punishProbability;
        }

        public boolean enabled() { return enabled; }
        public String apiKey() { return apiKey; }
        public String endpoint() { return endpoint; }
        public String model() { return model; }
        public long timeoutSeconds() { return timeoutSeconds; }
        public long cooldownSeconds() { return cooldownSeconds; }
        public boolean allLogs() { return allLogs; }
        public String mode() { return mode; }
        public double punishProbability() { return punishProbability; }
    }
}
