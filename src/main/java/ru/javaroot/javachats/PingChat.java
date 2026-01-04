package ru.javaroot.javachats;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.javaroot.javachats.utils.ColorUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PingChat {

    private final JavaChats plugin;
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([a-zA-Z0-9_]+)");

    public PingChat(JavaChats plugin) {
        this.plugin = plugin;
    }

    public Set<Player> getMentionedPlayers(String message) {
        Set<Player> mentioned = new HashSet<>();
        Matcher matcher = MENTION_PATTERN.matcher(message);
        while (matcher.find()) {
            String name = matcher.group(1);
            Player p = Bukkit.getPlayerExact(name);
            if (p != null && p.isOnline()) {
                mentioned.add(p);
            }
        }
        return mentioned;
    }

    public void sendNotification(Player player) {
        if (!plugin.getConfig().getBoolean("ping-chat.enabled"))
            return;

        String title = plugin.getConfig().getString("ping-chat.title.text", "");
        String subTitle = plugin.getConfig().getString("ping-chat.title.sub-text", "");
        int fadeIn = plugin.getConfig().getInt("ping-chat.title.fade-in", 10);
        int stay = plugin.getConfig().getInt("ping-chat.title.stay", 70);
        int fadeOut = plugin.getConfig().getInt("ping-chat.title.fade-out", 20);

        if (!title.isEmpty() || !subTitle.isEmpty()) {
            player.sendTitle(ColorUtils.colorize(title), ColorUtils.colorize(subTitle), fadeIn, stay, fadeOut);
        }

        if (plugin.getConfig().getBoolean("ping-chat.sound.enable")) {
            String soundName = plugin.getConfig().getString("ping-chat.sound.name", "BLOCK_NOTE_BLOCK_GUITAR");
            float volume = (float) plugin.getConfig().getDouble("ping-chat.sound.volume", 0.7);
            float pitch = (float) plugin.getConfig().getDouble("ping-chat.sound.pitch", 2.0);

            try {
                org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundName);
                player.playSound(player.getLocation(), sound, volume, pitch);
            } catch (IllegalArgumentException e) {
                // Invalid sound name
            }
        }
    }

    public String processMessageFor(String message, Player viewer) {
        if (!plugin.getConfig().getBoolean("ping-chat.enabled"))
            return message;

        Matcher matcher = MENTION_PATTERN.matcher(message);
        StringBuffer sb = new StringBuffer();

        String colorTarget = plugin.getConfig().getString("ping-chat.highlight.target", "&9");
        String colorOthers = plugin.getConfig().getString("ping-chat.highlight.others", "&7");

        // The color to reset to after the mention. Using &f as standard chat color.
        // Assuming the format sets the message color to white or similar.
        String resetColor = "&f";

        while (matcher.find()) {
            String name = matcher.group(1);
            Player p = Bukkit.getPlayerExact(name);

            String replacement;
            if (p != null && p.isOnline()) {
                if (p.equals(viewer)) {
                    replacement = colorTarget + "@" + name + resetColor;
                } else {
                    replacement = colorOthers + "@" + name + resetColor;
                }
            } else {
                replacement = matcher.group(0);
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
