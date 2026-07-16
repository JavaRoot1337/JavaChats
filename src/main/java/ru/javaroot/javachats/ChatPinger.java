package ru.javaroot.javachats;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import ru.javaroot.javachats.utils.TextUtil;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatPinger {
    private final JavaChat plugin;

    public ChatPinger(JavaChat plugin) {
        this.plugin = plugin;
    }

    public Set<Player> getMentionedPlayers(String message) {
        Set<Player> mentioned = new HashSet<>();
        var cfg = plugin.getConfig();
        if (!cfg.getBoolean("ping-chat.enabled", true))
            return mentioned;

        Pattern pattern = Pattern.compile("@(\\w+)");
        Matcher matcher = pattern.matcher(message);

        while (matcher.find()) {
            String name = matcher.group(1);
            Player player = Bukkit.getPlayerExact(name);
            if (player != null) {
                mentioned.add(player);
            }
        }
        return mentioned;
    }

    public String processMessageFor(String message, Player recipient) {
        var cfg = plugin.getConfig();
        if (!cfg.getBoolean("ping-chat.enabled", true))
            return message;

        var msgCfg = plugin.getMessageConfig();
        String targetColor = msgCfg.getString("ping.highlight.target", "&9");
        String othersColor = msgCfg.getString("ping.highlight.others", "&7");

        Pattern pattern = Pattern.compile("@" + recipient.getName() + "\\b", Pattern.CASE_INSENSITIVE);
        return pattern.matcher(message).replaceAll(targetColor + "@" + recipient.getName() + othersColor);
    }

    public void sendNotification(Player player) {
        var cfg = plugin.getConfig();
        var msgCfg = plugin.getMessageConfig();

        if (cfg.getBoolean("ping-chat.sound.enable", true)) {
            try {
                @SuppressWarnings("deprecation")
                Sound sound = Sound.valueOf(cfg.getString("ping-chat.sound.name", "BLOCK_NOTE_BLOCK_GUITAR"));
                float volume = (float) cfg.getDouble("ping-chat.sound.volume", 0.7);
                float pitch = (float) cfg.getDouble("ping-chat.sound.pitch", 2.0);
                String catName = cfg.getString("ping-chat.sound.category", "MASTER");
                SoundCategory cat;
                try {
                    cat = SoundCategory.valueOf(catName);
                } catch (IllegalArgumentException e) {
                    cat = SoundCategory.MASTER;
                }
                player.playSound(player.getLocation(), sound, cat, volume, pitch);
            } catch (Exception ignored) {
            }
        }

        int in = cfg.getInt("ping-chat.title.fade-in", 10);
        int stay = cfg.getInt("ping-chat.title.stay", 70);
        int out = cfg.getInt("ping-chat.title.fade-out", 20);

        var times = Title.Times.times(Duration.ofMillis(in * 50L), Duration.ofMillis(stay * 50L),
                Duration.ofMillis(out * 50L));

        Component title = TextUtil.format(msgCfg.getString("ping.title.text", ""));
        Component sub = TextUtil.format(msgCfg.getString("ping.title.sub-text", ""));

        if (!net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(title)
                .isEmpty()
                || !net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(sub)
                        .isEmpty()) {
            player.showTitle(Title.title(title, sub, times));
        }
    }
}
