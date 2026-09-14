package ru.javaroot.javachats;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import ru.javaroot.JavaChat;
import ru.javaroot.javachats.utils.TextUtil;
import ru.javaroot.javachats.utils.LogVars;

import java.time.Duration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatPinger {
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([\\p{L}\\p{N}_]+)");
    private final JavaChat plugin;

    public ChatPinger(JavaChat plugin) {
        this.plugin = plugin;
    }

    public Set<Player> getMentionedPlayers(String message) {
        Set<Player> mentioned = new HashSet<>();
        if (!plugin.getRuntimeConfig().ping().enabled()) {
            return mentioned;
        }

        Matcher matcher = MENTION_PATTERN.matcher(message);
        while (matcher.find()) {
            String name = matcher.group(1);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().equalsIgnoreCase(name)) {
                    mentioned.add(player);
                    break;
                }
            }
        }
        return mentioned;
    }

    public Component processComponentFor(String message, Player recipient) {
        if (!plugin.getRuntimeConfig().ping().enabled()) {
            return TextUtil.literal(message);
        }
        String targetColor = plugin.getMessageSnapshot().text("ping.highlight.target");
        String othersColor = plugin.getMessageSnapshot().text("ping.highlight.others");
        String target = Pattern.quote(recipient.getName());
        Pattern pattern = Pattern.compile("(?<![\\p{L}\\p{N}_])@" + target
                + "(?![\\p{L}\\p{N}_])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Matcher matcher = pattern.matcher(message);
        Component result = Component.empty();
        int end = 0;
        while (matcher.find()) {
            result = result.append(TextUtil.literal(message.substring(end, matcher.start())));
            result = result.append(TextUtil.format((targetColor == null ? "" : targetColor)
                    + "@" + recipient.getName() + (othersColor == null ? "" : othersColor)));
            end = matcher.end();
        }
        return result.append(TextUtil.literal(message.substring(end)));
    }

    public void sendNotification(Player player) {
        ru.javaroot.javachats.config.RuntimeConfig.Ping cfg = plugin.getRuntimeConfig().ping();
        if (cfg.soundEnabled()) {
            playSound(player, cfg);
        }

        Title.Times times = TextUtil.titleTimes(
                Duration.ofMillis(cfg.fadeInTicks() * 50L),
                Duration.ofMillis(cfg.stayTicks() * 50L),
                Duration.ofMillis(cfg.fadeOutTicks() * 50L));
        Component title = TextUtil.format(plugin.getMessageSnapshot().text("ping.title.text"));
        Component sub = TextUtil.format(plugin.getMessageSnapshot().text("ping.title.sub-text"));
        player.showTitle(Title.title(title, sub, times));
    }

    private void playSound(Player player, ru.javaroot.javachats.config.RuntimeConfig.Ping cfg) {
        String soundName = cfg.sound();
        if (soundName == null || soundName.isEmpty()) {
            return;
        }

        try {
            Sound sound = Registry.SOUNDS.get(NamespacedKey.minecraft(soundName.toLowerCase(Locale.ROOT)));
            if (sound == null) {
                throw new IllegalArgumentException(soundName);
            }
            SoundCategory category = SoundCategory.valueOf(cfg.soundCategory().toUpperCase(Locale.ROOT));
            float volume = (float) cfg.volume();
            float pitch = (float) cfg.pitch();
            player.playSound(player.getLocation(), sound, category, volume, pitch);
        } catch (IllegalArgumentException ignored) {
            plugin.getLogs().warning("ping-sound", LogVars.of("sound", soundName));
        }
    }
}
