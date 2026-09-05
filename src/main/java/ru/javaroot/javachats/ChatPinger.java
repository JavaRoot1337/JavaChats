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

import java.time.Duration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatPinger {
    private static final Pattern MENTION_PATTERN = Pattern.compile("@(\\w+)");
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
            Player player = Bukkit.getPlayerExact(matcher.group(1));
            if (player != null) {
                mentioned.add(player);
            }
        }
        return mentioned;
    }

    public String processMessageFor(String message, Player recipient) {
        if (!plugin.getRuntimeConfig().ping().enabled()) {
            return message;
        }

        String targetColor = plugin.getMessageSnapshot().text("ping.highlight.target");
        String othersColor = plugin.getMessageSnapshot().text("ping.highlight.others");
        String target = Pattern.quote(recipient.getName());
        return Pattern.compile("@" + target + "\\b", Pattern.CASE_INSENSITIVE)
                .matcher(message)
                .replaceAll(Matcher.quoteReplacement(targetColor + "@" + recipient.getName() + othersColor));
    }

    public void sendNotification(Player player) {
        var cfg = plugin.getRuntimeConfig().ping();
        if (cfg.soundEnabled()) {
            playSound(player, cfg);
        }

        Title.Times times = Title.Times.times(
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
            plugin.getLogs().warning("ping-sound", Map.of("sound", soundName));
        }
    }
}
