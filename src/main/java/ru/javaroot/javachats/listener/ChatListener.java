package ru.javaroot.javachats.listener;

import github.scarsz.discordsrv.DiscordSRV;
import net.luckperms.api.model.user.User;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import ru.javaroot.javachats.JavaChats;
import ru.javaroot.javachats.utils.ColorUtils;
import ru.javaroot.javachats.PingChat;

import java.util.HashSet;
import java.util.Set;

public class ChatListener implements Listener {

    private final JavaChats plugin;

    public ChatListener(JavaChats plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (event.isCancelled())
            return;

        Player player = event.getPlayer();
        String message = event.getMessage();
        String globalSymbol = plugin.getConfig().getString("chats.global.symbol", "!");
        boolean isGlobal = message.startsWith(globalSymbol);

        // Permissions & Type
        String type = isGlobal ? "global" : "local";
        if (!plugin.getConfig().getBoolean("chats." + type + ".enable"))
            return;

        if (isGlobal) {
            if (!player.hasPermission(plugin.getConfig().getString("chats.global.sending-perms"))) {
                player.sendMessage(ColorUtils.colorize(plugin.getConfig().getString("messages.no-permission")));
                event.setCancelled(true);
                return;
            }
            message = message.substring(globalSymbol.length()).trim();
            if (message.isEmpty()) {
                event.setCancelled(true);
                return;
            }
        } else {
            if (!player.hasPermission(plugin.getConfig().getString("chats.local.sending-perms"))) {
                player.sendMessage(ColorUtils.colorize(plugin.getConfig().getString("messages.no-permission")));
                event.setCancelled(true);
                return;
            }
        }

        // Anti-Caps
        if (isCaps(message)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.getWorld().strikeLightningEffect(player.getLocation());
                player.sendTitle(ColorUtils.colorize(plugin.getConfig().getString("messages.anti-caps-title")), "", 10,
                        70, 20);
            });
        }

        event.setCancelled(true);

        // Format
        String format = plugin.getConfig().getString("chats." + type + ".format");
        String prefix = "";
        String suffix = "";

        if (plugin.getLuckPerms() != null) {
            User user = plugin.getLuckPerms().getUserManager().getUser(player.getUniqueId());
            if (user != null) {
                prefix = user.getCachedData().getMetaData().getPrefix();
                suffix = user.getCachedData().getMetaData().getSuffix();
            }
        }
        if (prefix == null)
            prefix = "";
        if (suffix == null)
            suffix = "";

        // Replace Placeholders %...%
        String baseFormat = format.replace("%prefix%", prefix)
                .replace("%suffix%", suffix)
                .replace("%player%", "%player%"); // Keep for splitting

        // Recipients
        Set<Player> recipients = new HashSet<>();
        int range = plugin.getConfig().getInt("chats." + type + ".range");
        String viewingPerm = plugin.getConfig().getString("chats." + type + ".viewing-perms");

        if (range == -1) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.hasPermission(viewingPerm)) {
                    recipients.add(p);
                }
            }
        } else {
            Location loc = player.getLocation();
            for (Player p : loc.getWorld().getPlayers()) {
                if (p.getLocation().distance(loc) <= range && p.hasPermission(viewingPerm)) {
                    recipients.add(p);
                }
            }
        }

        // Ping Chat Processing
        PingChat pingChat = new PingChat(plugin);
        Set<Player> mentioned = pingChat.getMentionedPlayers(message);
        for (Player mentionedPlayer : mentioned) {
            if (recipients.contains(mentionedPlayer)) {
                pingChat.sendNotification(mentionedPlayer);
            }
        }

        // Sound
        String soundName = plugin.getConfig().getString("chats." + type + ".sound");
        Sound sound = null;
        if (soundName != null && !soundName.isEmpty()) {
            try {
                sound = Sound.valueOf(soundName.toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }

        // Send to recipients
        for (Player recipient : recipients) {
            String displayedMessage = message;
            if (!mentioned.isEmpty()) {
                displayedMessage = pingChat.processMessageFor(message, recipient);
            }

            String finalFormat = baseFormat.replace("%message%", displayedMessage);
            TextComponent msgComponent = buildJsonMessage(finalFormat, player);

            recipient.spigot().sendMessage(msgComponent);
            if (sound != null) {
                recipient.playSound(recipient.getLocation(), sound, 1f, 1f);
            }
        }

        // Console
        String consoleFormat = baseFormat.replace("%message%", message);
        TextComponent consoleComponent = buildJsonMessage(consoleFormat, player);
        Bukkit.getConsoleSender().sendMessage(consoleComponent.toLegacyText());

        if (Bukkit.getPluginManager().isPluginEnabled("DiscordSRV")) {
            DiscordSRV.getPlugin().processChatMessage(player, message, isGlobal ? "global" : "local", false);
        }
    }

    private boolean isCaps(String message) {
        int len = message.length();
        if (len < plugin.getConfig().getInt("settings.anti-caps-min-length", 4))
            return false;
        long capsCount = message.chars().filter(Character::isUpperCase).count();
        double percent = (double) capsCount / len * 100;
        return percent > plugin.getConfig().getInt("settings.anti-caps-percent", 75);
    }

    private TextComponent buildJsonMessage(String partiallyFormatted, Player player) {
        String splitToken = "###PLAYER###";
        String preSplit = partiallyFormatted.replace("%player%", splitToken);
        String fullColorized = ColorUtils.colorize(preSplit);

        String[] parts = fullColorized.split(splitToken);

        TextComponent root = new TextComponent("");
        if (parts.length > 0) {
            for (BaseComponent c : TextComponent.fromLegacyText(parts[0])) {
                root.addExtra(c);
            }
        }

        String lastColors = "";
        if (parts.length > 0) {
            lastColors = ColorUtils.getLastColors(parts[0]);
        }

        // Parse the legacy string into components to ensure Hex colors are applied as
        // properties
        BaseComponent[] playerComponents = TextComponent.fromLegacyText(lastColors + player.getName());

        String msgHover = ColorUtils.colorize(plugin.getConfig().getString("messages.msg-hover"));
        HoverEvent hoverEvent = new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(msgHover).create());
        ClickEvent clickEvent = new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/msg " + player.getName() + " ");

        for (BaseComponent component : playerComponents) {
            component.setHoverEvent(hoverEvent);
            component.setClickEvent(clickEvent);
            root.addExtra(component);
        }

        if (parts.length > 1) {
            for (BaseComponent c : TextComponent.fromLegacyText(parts[1])) {
                root.addExtra(c);
            }
        }

        return root;
    }
}
