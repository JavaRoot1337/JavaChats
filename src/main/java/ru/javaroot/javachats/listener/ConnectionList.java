package ru.javaroot.javachats.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.javaroot.JavaChat;
import ru.javaroot.javachats.utils.TextUtil;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class ConnectionList implements Listener {
    private final JavaChat plugin;
    private final ChatList chatList;

    public ConnectionList(JavaChat plugin, ChatList chatList) {
        this.plugin = plugin;
        this.chatList = chatList;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!plugin.getRuntimeConfig().joinQuit().joinEnabled()) {
            e.joinMessage(null);
            return;
        }

        List<String> messages = plugin.getMessageSnapshot().list("join-quit.join");
        if (messages.isEmpty()) {
            e.joinMessage(null);
            return;
        }

        String msg = messages.get(ThreadLocalRandom.current().nextInt(messages.size()))
                .replace("%player%", e.getPlayer().getName());
        e.joinMessage(TextUtil.format(msg));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (!plugin.getRuntimeConfig().joinQuit().quitEnabled()) {
            e.quitMessage(null);
        } else {
            List<String> messages = plugin.getMessageSnapshot().list("join-quit.quit");
            if (messages.isEmpty()) {
                e.quitMessage(null);
            } else {
                String msg = messages.get(ThreadLocalRandom.current().nextInt(messages.size()))
                        .replace("%player%", e.getPlayer().getName());
                e.quitMessage(TextUtil.format(msg));
            }
        }
        chatList.cleanPlayerData(e.getPlayer().getUniqueId());
    }
}
