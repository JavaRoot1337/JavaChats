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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

public final class ServerScheduler implements AutoCloseable {
    private final Plugin plugin;
    private final Server server;
    private final Set<TrackedTask> tasks = ConcurrentHashMap.newKeySet();
    private final Object lifecycleLock = new Object();
    private volatile boolean closed;

    public ServerScheduler(Plugin plugin) {
        this.plugin = plugin;
        this.server = plugin.getServer();
    }

    public BukkitTask runServer(Runnable action) {
        return schedule(action, wrapped -> server.getScheduler().runTask(plugin, wrapped), true);
    }

    public BukkitTask runServerLater(Runnable action, long delayTicks) {
        long delay = Math.max(0L, delayTicks);
        return schedule(action, wrapped -> server.getScheduler().runTaskLater(plugin, wrapped, delay), true);
    }

    public BukkitTask runAsync(Runnable action) {
        return schedule(action, wrapped -> server.getScheduler().runTaskAsynchronously(plugin, wrapped), true);
    }

    public BukkitTask runAsyncRepeating(Runnable action, long initialDelayTicks, long period, TimeUnit unit) {
        long periodTicks = Math.max(1L, unit.toMillis(period) / 50L);
        long initialDelay = Math.max(0L, initialDelayTicks);
        return schedule(action,
                wrapped -> server.getScheduler().runTaskTimerAsynchronously(plugin, wrapped, initialDelay, periodTicks),
                false);
    }

    public boolean runForPlayer(UUID playerId, Runnable action) {
        if (playerId == null || action == null || closed) {
            return false;
        }
        BukkitTask task = schedule(() -> {
            Player current = Bukkit.getPlayer(playerId);
            if (current != null && current.isOnline()) {
                action.run();
            }
        }, wrapped -> server.getScheduler().runTask(plugin, wrapped), true);
        return task != null;
    }

    private BukkitTask schedule(Runnable action, Function<Runnable, BukkitTask> registrar, boolean oneShot) {
        if (action == null) {
            throw new NullPointerException("action");
        }

        TrackedTask tracked = new TrackedTask(this);
        Runnable wrapped = () -> {
            if (tracked.isCancelled() || closed) {
                tracked.complete();
                return;
            }
            try {
                action.run();
            } finally {
                if (oneShot) {
                    tracked.complete();
                }
            }
        };

        synchronized (lifecycleLock) {
            if (closed) {
                return null;
            }
            BukkitTask delegate = registrar.apply(wrapped);
            tracked.setDelegate(delegate);
            if (closed) {
                tracked.cancelDelegate();
                return null;
            }
            tasks.add(tracked);
            if (tracked.isComplete()) {
                tasks.remove(tracked);
            }
            return tracked;
        }
    }

    @Override
    public void close() {
        synchronized (lifecycleLock) {
            if (closed) {
                return;
            }
            closed = true;
            for (TrackedTask task : tasks) {
                task.cancelDelegate();
            }
            tasks.clear();
        }
        server.getScheduler().cancelTasks(plugin);
    }

    private void untrack(TrackedTask task) {
        tasks.remove(task);
    }

    private static final class TrackedTask implements BukkitTask {
        private final ServerScheduler owner;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicBoolean complete = new AtomicBoolean();
        private volatile BukkitTask delegate;

        private TrackedTask(ServerScheduler owner) {
            this.owner = owner;
        }

        private void setDelegate(BukkitTask delegate) {
            this.delegate = delegate;
        }

        private boolean isComplete() {
            return complete.get();
        }

        private void complete() {
            if (complete.compareAndSet(false, true)) {
                owner.untrack(this);
            }
        }

        private void cancelDelegate() {
            cancelled.set(true);
            BukkitTask task = delegate;
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
            complete();
        }

        @Override
        public int getTaskId() {
            BukkitTask task = delegate;
            return task == null ? -1 : task.getTaskId();
        }

        @Override
        public Plugin getOwner() {
            return owner.plugin;
        }

        @Override
        public boolean isSync() {
            BukkitTask task = delegate;
            return task != null && task.isSync();
        }

        @Override
        public boolean isCancelled() {
            BukkitTask task = delegate;
            return cancelled.get() || (task != null && task.isCancelled());
        }

        @Override
        public void cancel() {
            cancelDelegate();
        }
    }
}
