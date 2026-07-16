package ru.javaroot.javachats.listener;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.javaroot.javachats.JavaChat;
import ru.javaroot.javachats.utils.TextUtil;

import java.util.List;
import java.util.Random;

public class ConnectionList implements Listener {
    private final JavaChat plugin;
    private final Random rand = new Random();

    public ConnectionList(JavaChat plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        var cfg = plugin.getConfig();
        if (!cfg.getBoolean("join-quit.join.enable")) {
            e.joinMessage(null);
            return;
        }

        List<String> messages = plugin.getMessageConfig().getStringList("join-quit.join");
        if (messages.isEmpty()) {
            e.joinMessage(null);
            return;
        }

        String msg = messages.get(rand.nextInt(messages.size())).replace("%player%", e.getPlayer().getName());
        e.joinMessage(TextUtil.format(msg));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        var cfg = plugin.getConfig();
        if (!cfg.getBoolean("join-quit.quit.enable")) {
            e.quitMessage(null);
            return;
        }

        List<String> messages = plugin.getMessageConfig().getStringList("join-quit.quit");
        if (messages.isEmpty()) {
            e.quitMessage(null);
            return;
        }

        String msg = messages.get(rand.nextInt(messages.size())).replace("%player%", e.getPlayer().getName());
        e.quitMessage(TextUtil.format(msg));
    }
}
