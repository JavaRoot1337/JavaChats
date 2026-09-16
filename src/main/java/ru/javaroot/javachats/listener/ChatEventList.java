package ru.javaroot.javachats.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import ru.javaroot.javachats.utils.TextUtil;

public final class ChatEventList implements Listener {
    private final ChatList chat;

    public ChatEventList(ChatList chat) {
        this.chat = chat;
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        if (event.isCancelled()) {
            return;
        }
        java.util.UUID playerId = event.getPlayer().getUniqueId();
        String playerName = event.getPlayer().getName();
        String message = TextUtil.plain(event.message()).trim();
        if (chat.handleChat(playerId, playerName, message)) {
            event.setCancelled(true);
        }
    }
}
