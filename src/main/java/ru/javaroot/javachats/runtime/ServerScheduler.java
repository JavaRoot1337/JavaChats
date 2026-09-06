package ru.javaroot.javachats.runtime;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class ServerScheduler implements AutoCloseable {
    private final Plugin plugin;
    private final Server server;
    private final Set<BukkitTask> tasks = ConcurrentHashMap.newKeySet();

    public ServerScheduler(Plugin plugin) {
        this.plugin = plugin;
        this.server = plugin.getServer();
    }

    public void runServer(Runnable action) {
        track(server.getScheduler().runTask(plugin, action));
    }

    public BukkitTask runServerLater(Runnable action, long delayTicks) {
        return track(server.getScheduler().runTaskLater(plugin, action, Math.max(0L, delayTicks)));
    }

    public BukkitTask runAsync(Runnable action) {
        return track(server.getScheduler().runTaskAsynchronously(plugin, action));
    }

    public BukkitTask runAsyncRepeating(Runnable action, long initialDelayTicks, long period, TimeUnit unit) {
        long periodTicks = Math.max(1L, unit.toMillis(period) / 50L);
        return track(server.getScheduler().runTaskTimerAsynchronously(plugin, action,
                Math.max(0L, initialDelayTicks), periodTicks));
    }

    public boolean runForPlayer(UUID playerId, Runnable action) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return false;
        }
        runServer(() -> {
            Player current = Bukkit.getPlayer(playerId);
            if (current != null && current.isOnline()) {
                action.run();
            }
        });
        return true;
    }

    private BukkitTask track(BukkitTask task) {
        tasks.add(task);
        return task;
    }

    @Override
    public void close() {
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
        server.getScheduler().cancelTasks(plugin);
    }
}

