package ru.javaroot.javachats.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;
import ru.javaroot.javachats.JavaChats;
import ru.javaroot.javachats.utils.ColorUtils;

import java.util.List;
import java.util.Random;

public class ConnectionListener implements Listener {

    private final JavaChats plugin;
    private final Random random = new Random();

    public ConnectionListener(JavaChats plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.getConfig().getBoolean("join-quit-messages.join.enable"))
            return;

        List<String> messages = plugin.getConfig().getStringList("join-quit-messages.join.messages");
        if (messages.isEmpty())
            return;

        String message = messages.get(random.nextInt(messages.size()));
        String formatted = ColorUtils.colorize(message.replace("%player%", event.getPlayer().getName()));
        event.setJoinMessage(formatted);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (!plugin.getConfig().getBoolean("join-quit-messages.quit.enable"))
            return;

        List<String> messages = plugin.getConfig().getStringList("join-quit-messages.quit.messages");
        if (messages.isEmpty())
            return;

        String message = messages.get(random.nextInt(messages.size()));
        String formatted = ColorUtils.colorize(message.replace("%player%", event.getPlayer().getName()));
        event.setQuitMessage(formatted);
    }
}
