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
        if (chat.handleChat(event.getPlayer(), TextUtil.plain(event.message()).trim())) {
            event.setCancelled(true);
        }
    }
}
