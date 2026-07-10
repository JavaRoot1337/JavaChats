package ru.javaroot.javachats.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.javaroot.javachats.ChatPlugin;
import ru.javaroot.javachats.utils.TextUtil;
import java.util.List;
import java.util.Random;

public class JoinQuitListener implements Listener {
    private final ChatPlugin plugin;
    private final Random random = new Random();

    public JoinQuitListener(ChatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!plugin.getConfig().getBoolean("join-quit.join.enable")) return;

        List<String> list = plugin.getMessageConfig().getStringList("join-quit.join");
        if (list.isEmpty()) return;

        String raw = list.get(random.nextInt(list.size()));
        e.joinMessage(TextUtil.format(raw.replace("%player%", e.getPlayer().getName())));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (!plugin.getConfig().getBoolean("join-quit.quit.enable")) return;

        List<String> list = plugin.getMessageConfig().getStringList("join-quit.quit");
        if (list.isEmpty()) return;

        String raw = list.get(random.nextInt(list.size()));
        e.quitMessage(TextUtil.format(raw.replace("%player%", e.getPlayer().getName())));
    }
}
