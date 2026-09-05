package ru.javaroot.javachats.runtime;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ServerScheduler implements AutoCloseable {
    private final Plugin plugin;
    private final Server server;
    private final Set<ScheduledTask> tasks = ConcurrentHashMap.newKeySet();

    public ServerScheduler(Plugin plugin) {
        this.plugin = plugin;
        this.server = plugin.getServer();
    }

    public void runServer(Runnable action) {
        server.getGlobalRegionScheduler().execute(plugin, action);
    }

    public ScheduledTask runServerLater(Runnable action, long delayTicks) {
        return track(server.getGlobalRegionScheduler().runDelayed(plugin, ignored -> action.run(), delayTicks));
    }

    public ScheduledTask runAsync(Runnable action) {
        return track(server.getAsyncScheduler().runNow(plugin, ignored -> action.run()));
    }

    public ScheduledTask runAsyncRepeating(Runnable action, long initialDelay, long period, TimeUnit unit) {
        return track(server.getAsyncScheduler().runAtFixedRate(plugin, ignored -> action.run(), initialDelay, period, unit));
    }

    public boolean runForPlayer(UUID playerId, Runnable action) {
        Player player = server.getPlayer(playerId);
        return player != null && player.getScheduler().execute(plugin, action, null, 1L);
    }

    private ScheduledTask track(ScheduledTask task) {
        tasks.add(task);
        return task;
    }

    @Override
    public void close() {
        for (ScheduledTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
        server.getAsyncScheduler().cancelTasks(plugin);
        server.getGlobalRegionScheduler().cancelTasks(plugin);
    }
}
