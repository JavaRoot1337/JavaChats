package ru.javaroot.javachats.list;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import ru.javaroot.javachats.chat.ChatEng;
import ru.javaroot.javachats.plat.Hex;

public final class ChatList implements Listener {
    private final ChatEng chat;

    public ChatList(ChatEng chat) {
        this.chat = chat;
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        if (event.isCancelled()) {
            return;
        }
        String text = Hex.plain(event.message()).trim();
        if (chat.handleChat(event.getPlayer().getUniqueId(), event.getPlayer().getName(), text)) {
            event.setCancelled(true);
        }
    }
}
