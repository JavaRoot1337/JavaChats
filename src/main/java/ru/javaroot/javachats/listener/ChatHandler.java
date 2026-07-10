package ru.javaroot.javachats.listener;

import github.scarsz.discordsrv.DiscordSRV;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import ru.javaroot.javachats.ChatPlugin;
import ru.javaroot.javachats.PingHandler;
import ru.javaroot.javachats.utils.TextUtil;
import java.util.HashSet;
import java.util.Set;
import net.kyori.adventure.title.Title;
import java.time.Duration;

public class ChatHandler implements Listener {
    private final ChatPlugin plugin;

    public ChatHandler(ChatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        if (e.isCancelled()) return;

        Player player = e.getPlayer();
        String msg = e.getMessage();
        var cfg = plugin.getConfig();
        var msgCfg = plugin.getMessageConfig();

        String globalSymbol = cfg.getString("chats.global.symbol", "!");
        boolean isGlobal = msg.startsWith(globalSymbol);
        String type = isGlobal ? "global" : "local";

        if (!cfg.getBoolean("chats." + type + ".enable")) return;

        String sendPerm = cfg.getString("chats." + type + ".sending-perms");
        if (sendPerm != null && !sendPerm.isEmpty() && !player.hasPermission(sendPerm)) {
            player.sendMessage(TextUtil.format(msgCfg.getString("messages.no-permission")));
            e.setCancelled(true);
            return;
        }

        if (isGlobal) {
            msg = msg.substring(globalSymbol.length()).trim();
            if (msg.isEmpty()) {
                e.setCancelled(true);
                return;
            }
        }

        if (isCaps(msg)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.getWorld().strikeLightningEffect(player.getLocation());
                var times = Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3500), Duration.ofMillis(1000));
                player.showTitle(Title.title(TextUtil.format(msgCfg.getString("anti-caps.title")), Component.empty(), times));
            });
        }

        e.setCancelled(true);

        String format = msgCfg.getString("chat." + type, "");
        String prefix = "", suffix = "";

        if (plugin.getLuckPerms() != null) {
            User user = plugin.getLuckPerms().getUserManager().getUser(player.getUniqueId());
            if (user != null) {
                prefix = user.getCachedData().getMetaData().getPrefix();
                suffix = user.getCachedData().getMetaData().getSuffix();
            }
        }
        prefix = prefix == null ? "" : prefix;
        suffix = suffix == null ? "" : suffix;

        String[] parts = format.split("%player%", 2);
        if (parts.length < 2) return;

        Component prefixComp = TextUtil.format(parts[0].replace("%prefix%", prefix));
        String lastColors = TextUtil.getColors(parts[0]);

        Component playerComp = TextUtil.format(lastColors + player.getName())
                .hoverEvent(HoverEvent.showText(TextUtil.format(msgCfg.getString("pm.hover"))))
                .clickEvent(ClickEvent.suggestCommand("/msg " + player.getName() + " "));

        String[] msgParts = parts[1].split("%message%", 2);
        Component suffixComp = msgParts.length > 0 ? TextUtil.format(msgParts[0].replace("%suffix%", suffix)) : Component.empty();
        Component tailComp = msgParts.length > 1 ? TextUtil.format(msgParts[1]) : Component.empty();

        Set<Player> recipients = new HashSet<>();
        int range = cfg.getInt("chats." + type + ".range");
        String viewPerm = cfg.getString("chats." + type + ".viewing-perms");

        if (range == -1) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (viewPerm == null || viewPerm.isEmpty() || p.hasPermission(viewPerm)) recipients.add(p);
            }
        } else {
            Location loc = player.getLocation();
            for (Player p : loc.getWorld().getPlayers()) {
                if (p.getLocation().distance(loc) <= range && (viewPerm == null || viewPerm.isEmpty() || p.hasPermission(viewPerm))) {
                    recipients.add(p);
                }
            }
        }

        PingHandler pingHandler = new PingHandler(plugin);
        Set<Player> mentioned = pingHandler.getMentionedPlayers(msg);
        for (Player m : mentioned) {
            if (recipients.contains(m)) pingHandler.sendNotification(m);
        }

        String soundName = cfg.getString("chats." + type + ".sound");
        Sound sound = null;
        if (soundName != null && !soundName.isEmpty()) {
            try {
                sound = Sound.valueOf(soundName.toUpperCase());
            } catch (Exception ignored) {}
        }

        for (Player r : recipients) {
            String displayedMsg = mentioned.isEmpty() ? msg : pingHandler.processMessageFor(msg, r);
            Component msgComp = TextUtil.format(displayedMsg);
            Component fullMsg = prefixComp.append(playerComp).append(suffixComp).append(msgComp).append(tailComp);
            r.sendMessage(fullMsg);
            if (sound != null) {
                r.playSound(r.getLocation(), sound, 1f, 1f);
            }
        }

        Component consoleMsg = prefixComp.append(playerComp).append(suffixComp).append(TextUtil.format(msg)).append(tailComp);
        Bukkit.getConsoleSender().sendMessage(consoleMsg);

        if (Bukkit.getPluginManager().isPluginEnabled("DiscordSRV")) {
            DiscordSRV.getPlugin().processChatMessage(player, msg, isGlobal ? "global" : "local", false);
        }
    }

    private boolean isCaps(String msg) {
        int len = msg.length();
        var cfg = plugin.getConfig();
        if (len < cfg.getInt("settings.anti-caps-min-length", 4)) return false;
        long caps = msg.chars().filter(Character::isUpperCase).count();
        return ((double) caps / len * 100) > cfg.getInt("settings.anti-caps-percent", 75);
    }
}
