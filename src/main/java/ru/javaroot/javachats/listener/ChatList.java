package ru.javaroot.javachats.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.title.Title;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import ru.javaroot.javachats.JavaChat;
import ru.javaroot.javachats.ChatPinger;
import ru.javaroot.javachats.utils.TextUtil;

import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChatList implements Listener {
    private final JavaChat plugin;
    private final Map<UUID, Long> lastMsgTime = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastMessages = new ConcurrentHashMap<>();

    public ChatList(JavaChat plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChat(AsyncChatEvent e) {
        if (e.isCancelled())
            return;

        Player p = e.getPlayer();
        String msg = PlainTextComponentSerializer.plainText().serialize(e.message());
        var cfg = plugin.getConfig();
        var msgCfg = plugin.getMessageConfig();

        if (cfg.getBoolean("anti-repeat.enable", true)) {
            String lastMsg = lastMessages.get(p.getUniqueId());
            if (msg.equalsIgnoreCase(lastMsg)) {
                p.sendMessage(TextUtil.format(msgCfg.getString("anti-repeat", "&cНе повторяйте сообщения!")));
                e.setCancelled(true);
                return;
            }
        }

        if (cfg.getBoolean("anti-spam.enable", true)) {
            long now = System.currentTimeMillis();
            long lastTime = lastMsgTime.getOrDefault(p.getUniqueId(), 0L);
            long delay = cfg.getLong("anti-spam.delay-ms", 1000L);
            if (now - lastTime < delay) {
                e.setCancelled(true);
                triggerSpamPunish(p);
                return;
            }
            lastMsgTime.put(p.getUniqueId(), now);
        }

        String globalSym = cfg.getString("chats.global.symbol", "!");
        boolean isGlobal = msg.startsWith(globalSym);
        String type = isGlobal ? "global" : "local";

        if (!cfg.getBoolean("chats." + type + ".enable"))
            return;

        String sendPerm = cfg.getString("chats." + type + ".sending-perms");
        if (sendPerm != null && !sendPerm.isEmpty() && !p.hasPermission(sendPerm)) {
            p.sendMessage(TextUtil.format(msgCfg.getString("messages.no-permission")));
            e.setCancelled(true);
            return;
        }

        if (isGlobal) {
            msg = msg.substring(globalSym.length()).trim();
            if (msg.isEmpty()) {
                e.setCancelled(true);
                return;
            }
        }

        String originalMsg = msg;
        boolean wasCensored = false;
        if (plugin.getAiMod() != null) {
            String censored = plugin.getAiMod().censorIfViolation(p, msg);
            if (censored != null) {
                msg = censored;
                wasCensored = true;
            }
            plugin.getAiMod().handleMsg(p, originalMsg);
        }

        if (isCaps(msg)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                p.getWorld().strikeLightningEffect(p.getLocation());
                var times = Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3500), Duration.ofMillis(1000));
                p.showTitle(
                        Title.title(TextUtil.format(msgCfg.getString("anti-caps.title")), Component.empty(), times));
            });
        }

        e.setCancelled(true);
        lastMessages.put(p.getUniqueId(), msg);

        String format = msgCfg.getString("chat." + type, "");
        String pref = "", suff = "";

        if (plugin.getLuckPerms() != null) {
            User user = plugin.getLuckPerms().getUserManager().getUser(p.getUniqueId());
            if (user != null) {
                pref = user.getCachedData().getMetaData().getPrefix();
                suff = user.getCachedData().getMetaData().getSuffix();
            }
        }
        pref = pref == null ? "" : pref;
        suff = suff == null ? "" : suff;

        String[] parts = format.split("%player%", 2);
        if (parts.length < 2)
            return;

        Component prefixComp = TextUtil.format(parts[0].replace("%prefix%", pref));
        String lastColors = TextUtil.getColors(parts[0]);

        Component playerComp = TextUtil.format(lastColors + p.getName())
                .hoverEvent(HoverEvent.showText(TextUtil.format(msgCfg.getString("pm.hover"))))
                .clickEvent(ClickEvent.suggestCommand("/msg " + p.getName() + " "));

        String[] msgParts = parts[1].split("%message%", 2);
        Component suffixComp = msgParts.length > 0 ? TextUtil.format(msgParts[0].replace("%suffix%", suff))
                : Component.empty();
        Component tailComp = msgParts.length > 1 ? TextUtil.format(msgParts[1]) : Component.empty();

        Set<Player> rec = new HashSet<>();
        int range = cfg.getInt("chats." + type + ".range");
        String viewPerm = cfg.getString("chats." + type + ".viewing-perms");

        if (range == -1) {
            for (Player op : Bukkit.getOnlinePlayers()) {
                if (viewPerm == null || viewPerm.isEmpty() || op.hasPermission(viewPerm))
                    rec.add(op);
            }
        } else {
            Location loc = p.getLocation();
            for (Player op : loc.getWorld().getPlayers()) {
                if (op.getLocation().distance(loc) <= range
                        && (viewPerm == null || viewPerm.isEmpty() || op.hasPermission(viewPerm))) {
                    rec.add(op);
                }
            }
        }

        ChatPinger pinger = new ChatPinger(plugin);
        Set<Player> mentioned = pinger.getMentionedPlayers(msg);
        for (Player m : mentioned) {
            if (rec.contains(m))
                pinger.sendNotification(m);
        }

        String sndName = cfg.getString("chats." + type + ".sound");
        Sound sound = null;
        if (sndName != null && !sndName.isEmpty()) {
            try {
                sound = Sound.valueOf(sndName.toUpperCase());
            } catch (Exception ignored) {
            }
        }

        for (Player r : rec) {
            String displayed = mentioned.isEmpty() ? msg : pinger.processMessageFor(msg, r);
            Component msgComp = TextUtil.format(displayed);
            Component fullMsg = prefixComp.append(playerComp).append(suffixComp).append(msgComp).append(tailComp);
            r.sendMessage(fullMsg);
            if (sound != null) {
                r.playSound(r.getLocation(), sound, 1f, 1f);
            }
        }

        Component consoleMsg = prefixComp.append(playerComp).append(suffixComp).append(TextUtil.format(msg))
                .append(tailComp);
        Bukkit.getConsoleSender().sendMessage(consoleMsg);

        if (plugin.getChatLogger() != null) {
            if (wasCensored) {
                plugin.getChatLogger()
                        .log("[" + type.toUpperCase() + "] " + p.getName() + ": " + originalMsg + " (анти-цензура)");
            } else {
                plugin.getChatLogger().log("[" + type.toUpperCase() + "] " + p.getName() + ": " + msg);
            }
        }
    }

    private void triggerSpamPunish(Player p) {
        String subtitleText = plugin.getMessageConfig().getString("anti-spam.subtitle", "&cБЕЗ СПАМА!!!");
        for (int i = 0; i < 3; i++) {
            long delayTicks = i * 2L;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) {
                    p.getWorld().strikeLightningEffect(p.getLocation());
                    var times = Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(2000),
                            Duration.ofMillis(100));
                    p.showTitle(Title.title(Component.empty(), TextUtil.format(subtitleText), times));
                }
            }, delayTicks);
        }
    }

    private boolean isCaps(String msg) {
        int len = msg.length();
        var cfg = plugin.getConfig();
        if (len < cfg.getInt("settings.anti-caps-min-length", 4))
            return false;
        long caps = msg.chars().filter(Character::isUpperCase).count();
        return ((double) caps / len * 100) > cfg.getInt("settings.anti-caps-percent", 75);
    }

    public void cleanPlayerData(UUID uuid) {
        lastMsgTime.remove(uuid);
        lastMessages.remove(uuid);
    }
}
