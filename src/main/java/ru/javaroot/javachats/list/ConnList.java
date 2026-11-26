package ru.javaroot.javachats.list;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.javaroot.javachats.cfg.Cfg;
import ru.javaroot.javachats.cfg.Msg;
import ru.javaroot.javachats.chat.ChatEng;
import ru.javaroot.javachats.plat.Hex;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

public final class ConnList implements Listener {
    private final Supplier<Cfg> cfgSupplier;
    private final Supplier<Msg> msgSupplier;
    private final ChatEng chat;

    public ConnList(Supplier<Cfg> cfgSupplier, Supplier<Msg> msgSupplier, ChatEng chat) {
        this.cfgSupplier = cfgSupplier;
        this.msgSupplier = msgSupplier;
        this.chat = chat;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!cfgSupplier.get().joinQuit().joinEnabled()) {
            event.joinMessage(null);
            return;
        }
        List<String> lines = msgSupplier.get().list("join-quit.join");
        if (lines.isEmpty()) {
            event.joinMessage(null);
            return;
        }
        String line = lines.get(ThreadLocalRandom.current().nextInt(lines.size()))
                .replace("%player%", event.getPlayer().getName());
        event.joinMessage(Hex.parse(line));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (!cfgSupplier.get().joinQuit().quitEnabled()) {
            event.quitMessage(null);
        } else {
            List<String> lines = msgSupplier.get().list("join-quit.quit");
            if (lines.isEmpty()) {
                event.quitMessage(null);
            } else {
                String line = lines.get(ThreadLocalRandom.current().nextInt(lines.size()))
                        .replace("%player%", event.getPlayer().getName());
                event.quitMessage(Hex.parse(line));
            }
        }
        chat.cleanPlayer(event.getPlayer().getUniqueId());
    }
}
