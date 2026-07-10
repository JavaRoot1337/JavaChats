package ru.javaroot.javachats;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import ru.javaroot.javachats.utils.TextUtil;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PingHandler {
    private final ChatPlugin plugin;
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([a-zA-Z0-9_]+)");

    public PingHandler(ChatPlugin plugin) {
        this.plugin = plugin;
    }

    public Set<Player> getMentionedPlayers(String msg) {
        Set<Player> mentioned = new HashSet<>();
        Matcher matcher = MENTION_PATTERN.matcher(msg);
        while (matcher.find()) {
            Player p = Bukkit.getPlayerExact(matcher.group(1));
            if (p != null && p.isOnline()) {
                mentioned.add(p);
            }
        }
        return mentioned;
    }

    public void sendNotification(Player p) {
        var cfg = plugin.getConfig();
        var msgCfg = plugin.getMessageConfig();
        if (!cfg.getBoolean("ping-chat.enabled")) return;

        String titleText = msgCfg.getString("ping.title.text", "");
        String subText = msgCfg.getString("ping.title.sub-text", "");
        int in = cfg.getInt("ping-chat.title.fade-in", 10);
        int stay = cfg.getInt("ping-chat.title.stay", 70);
        int out = cfg.getInt("ping-chat.title.fade-out", 20);

        if (!titleText.isEmpty() || !subText.isEmpty()) {
            var times = Title.Times.times(
                Duration.ofMillis(in * 50L),
                Duration.ofMillis(stay * 50L),
                Duration.ofMillis(out * 50L)
            );
            p.showTitle(Title.title(TextUtil.format(titleText), TextUtil.format(subText), times));
        }

        if (cfg.getBoolean("ping-chat.sound.enable")) {
            try {
                Sound sound = Sound.valueOf(cfg.getString("ping-chat.sound.name", "BLOCK_NOTE_BLOCK_GUITAR"));
                float vol = (float) cfg.getDouble("ping-chat.sound.volume", 0.7);
                float pitch = (float) cfg.getDouble("ping-chat.sound.pitch", 2.0);
                p.playSound(p.getLocation(), sound, vol, pitch);
            } catch (Exception ignored) {}
        }
    }

    public String processMessageFor(String msg, Player viewer) {
        var cfg = plugin.getConfig();
        if (!cfg.getBoolean("ping-chat.enabled")) return msg;

        var msgCfg = plugin.getMessageConfig();
        Matcher matcher = MENTION_PATTERN.matcher(msg);
        StringBuilder sb = new StringBuilder();

        String colorTarget = msgCfg.getString("ping.highlight.target", "&9");
        String colorOthers = msgCfg.getString("ping.highlight.others", "&7");
        String resetColor = "&f";

        while (matcher.find()) {
            String name = matcher.group(1);
            Player p = Bukkit.getPlayerExact(name);
            String replacement;
            if (p != null && p.isOnline()) {
                replacement = (p.equals(viewer) ? colorTarget : colorOthers) + "@" + name + resetColor;
            } else {
                replacement = matcher.group();
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        return matcher.appendTail(sb).toString();
    }
}
