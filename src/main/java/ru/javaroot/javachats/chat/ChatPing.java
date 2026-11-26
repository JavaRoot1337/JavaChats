package ru.javaroot.javachats.chat;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import ru.javaroot.javachats.cfg.Cfg;
import ru.javaroot.javachats.cfg.LogCfg;
import ru.javaroot.javachats.cfg.Msg;
import ru.javaroot.javachats.plat.Hex;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatPing {
    private static final Pattern MENTION = Pattern.compile("@([\\p{L}\\p{N}_]+)");

    private final Supplier<Cfg> cfgSupplier;
    private final Supplier<Msg> msgSupplier;
    private final LogCfg logs;

    public ChatPing(Supplier<Cfg> cfgSupplier, Supplier<Msg> msgSupplier, LogCfg logs) {
        this.cfgSupplier = cfgSupplier;
        this.msgSupplier = msgSupplier;
        this.logs = logs;
    }

    public Set<UUID> mentionedIds(String message) {
        Set<UUID> ids = new HashSet<>();
        Cfg cfg = cfgSupplier.get();
        if (cfg == null || !cfg.ping().enabled()) {
            return ids;
        }
        Matcher matcher = MENTION.matcher(message);
        while (matcher.find()) {
            String name = matcher.group(1);
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().equalsIgnoreCase(name)) {
                    ids.add(online.getUniqueId());
                    break;
                }
            }
        }
        return ids;
    }

    public Component highlightFor(String message, String targetName) {
        Cfg cfg = cfgSupplier.get();
        if (cfg == null || !cfg.ping().enabled()) {
            return Hex.literal(message);
        }
        Msg msg = msgSupplier.get();
        String targetColor = msg.text("ping.highlight.target");
        String othersColor = msg.text("ping.highlight.others");
        Pattern pattern = Pattern.compile(
                "(?<![\\p{L}\\p{N}_])@" + Pattern.quote(targetName) + "(?![\\p{L}\\p{N}_])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Matcher matcher = pattern.matcher(message);
        Component out = Component.empty();
        int pos = 0;
        while (matcher.find()) {
            out = out.append(Hex.literal(message.substring(pos, matcher.start())));
            out = out.append(Hex.parse((targetColor == null ? "" : targetColor)
                    + "@" + targetName + (othersColor == null ? "" : othersColor)));
            pos = matcher.end();
        }
        return out.append(Hex.literal(message.substring(pos)));
    }

    public void notifyOnEntity(Player p) {
        Cfg.Ping cfg = cfgSupplier.get().ping();
        if (cfg.soundEnabled()) {
            playSound(p, cfg);
        }
        Msg msg = msgSupplier.get();
        Title.Times times = Hex.times(cfg.fadeInTicks() * 50L, cfg.stayTicks() * 50L, cfg.fadeOutTicks() * 50L);
        p.showTitle(Title.title(Hex.parse(msg.text("ping.title.text")), Hex.parse(msg.text("ping.title.sub-text")), times));
    }

    private void playSound(Player p, Cfg.Ping cfg) {
        try {
            Sound sound = findSound(cfg.sound());
            if (sound == null) {
                throw new IllegalArgumentException(String.valueOf(cfg.sound()));
            }
            SoundCategory cat = SoundCategory.valueOf(cfg.soundCategory().toUpperCase(Locale.ROOT));
            p.playSound(p.getLocation(), sound, cat, (float) cfg.volume(), (float) cfg.pitch());
        } catch (IllegalArgumentException ignored) {
            logs.warning("ping-sound", Map.of("sound", String.valueOf(cfg.sound())));
        }
    }

    public static Sound findSound(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        NamespacedKey key = NamespacedKey.fromString(value);
        if (key == null && value.indexOf(':') < 0) {
            key = NamespacedKey.minecraft(value.replace('_', '.'));
        }
        if (key == null) {
            return null;
        }
        var reg = RegistryAccess.registryAccess().getRegistry(RegistryKey.SOUND_EVENT);
        Sound direct = reg.get(key);
        if (direct != null) {
            return direct;
        }
        if (value.indexOf(':') < 0 && value.indexOf('.') < 0) {
            return reg.get(NamespacedKey.minecraft(value.replace('_', '.')));
        }
        return null;
    }
}
