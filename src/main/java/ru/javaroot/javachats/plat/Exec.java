package ru.javaroot.javachats.plat;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Exec implements AutoCloseable {
    private static final TaskHandle NOOP = new TaskHandle() {
        @Override
        public void cancel() {
        }

        @Override
        public boolean isCancelled() {
            return true;
        }
    };

    private final Plugin plug;
    private final Server srv;
    private final Set<Tracked> tasks = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();

    public Exec(Plugin plug) {
        this.plug = Objects.requireNonNull(plug, "plug");
        this.srv = plug.getServer();
    }

    public TaskHandle runAsync(Runnable action) {
        Objects.requireNonNull(action, "action");
        if (closed.get()) {
            return NOOP;
        }
        Tracked handle = new Tracked();
        ScheduledTask task = srv.getAsyncScheduler().runNow(plug, ignored -> runOneShot(handle, action));
        return register(handle, task);
    }

    public TaskHandle runAsyncLater(Runnable action, long delay, TimeUnit unit) {
        Objects.requireNonNull(action, "action");
        if (closed.get()) {
            return NOOP;
        }
        long safeDelay = Math.max(0L, delay);
        if (safeDelay == 0L) {
            return runAsync(action);
        }
        Tracked handle = new Tracked();
        ScheduledTask task = srv.getAsyncScheduler().runDelayed(plug, ignored -> runOneShot(handle, action), safeDelay, unit);
        return register(handle, task);
    }

    public TaskHandle runGlobal(Runnable action) {
        Objects.requireNonNull(action, "action");
        if (closed.get()) {
            return NOOP;
        }
        Tracked handle = new Tracked();
        ScheduledTask task = srv.getGlobalRegionScheduler().run(plug, ignored -> runOneShot(handle, action));
        return register(handle, task);
    }

    public TaskHandle runGlobalLater(Runnable action, long delayTicks) {
        Objects.requireNonNull(action, "action");
        if (closed.get()) {
            return NOOP;
        }
        if (delayTicks <= 0L) {
            return runGlobal(action);
        }
        Tracked handle = new Tracked();
        ScheduledTask task = srv.getGlobalRegionScheduler().runDelayed(plug, ignored -> runOneShot(handle, action), delayTicks);
        return register(handle, task);
    }

    public TaskHandle runAt(Location loc, Runnable action) {
        Objects.requireNonNull(loc, "loc");
        Objects.requireNonNull(action, "action");
        if (closed.get()) {
            return NOOP;
        }
        Tracked handle = new Tracked();
        ScheduledTask task = srv.getRegionScheduler().run(plug, loc, ignored -> runOneShot(handle, action));
        return register(handle, task);
    }

    public TaskHandle runEntity(Entity ent, Runnable action) {
        return runEntityLater(ent, action, 0L);
    }

    public TaskHandle runEntityLater(Entity ent, Runnable action, long delayTicks) {
        Objects.requireNonNull(ent, "ent");
        Objects.requireNonNull(action, "action");
        if (closed.get()) {
            return NOOP;
        }
        Tracked handle = new Tracked();
        Runnable retired = handle::finish;
        ScheduledTask task = delayTicks <= 0L
                ? ent.getScheduler().run(plug, ignored -> runOneShot(handle, action), retired)
                : ent.getScheduler().runDelayed(plug, ignored -> runOneShot(handle, action), retired, delayTicks);
        if (task == null) {
            handle.finish();
            return NOOP;
        }
        return register(handle, task);
    }

    public boolean runPlayer(UUID id, Runnable action) {
        return runPlayerLater(id, action, 0L);
    }

    public boolean runPlayerLater(UUID id, Runnable action, long delayTicks) {
        if (id == null || action == null || closed.get()) {
            return false;
        }
        var p = srv.getPlayer(id);
        if (p == null || !p.isOnline()) {
            return false;
        }
        TaskHandle handle = runEntityLater(p, () -> {
            if (p.isOnline()) {
                action.run();
            }
        }, delayTicks);
        return !handle.isCancelled();
    }

    public void runSender(CommandSender snd, Runnable action) {
        if (snd instanceof Entity ent) {
            runEntity(ent, action);
            return;
        }
        runGlobal(action);
    }

    private void runOneShot(Tracked handle, Runnable action) {
        if (closed.get() || handle.isCancelled()) {
            handle.finish();
            return;
        }
        try {
            action.run();
        } finally {
            handle.finish();
        }
    }

    private TaskHandle register(Tracked handle, ScheduledTask task) {
        handle.bind(task, tasks);
        if (closed.get()) {
            handle.cancel();
            return NOOP;
        }
        return handle;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        for (Tracked handle : tasks) {
            handle.cancel();
        }
        tasks.clear();
        srv.getAsyncScheduler().cancelTasks(plug);
        srv.getGlobalRegionScheduler().cancelTasks(plug);
    }

    private static final class Tracked implements TaskHandle {
        private final AtomicBoolean done = new AtomicBoolean();
        private volatile ScheduledTask delegate;
        private volatile Set<Tracked> owner;

        private void bind(ScheduledTask task, Set<Tracked> set) {
            this.delegate = task;
            this.owner = set;
            if (!done.get()) {
                set.add(this);
            }
        }

        private void finish() {
            if (done.compareAndSet(false, true)) {
                Set<Tracked> set = owner;
                if (set != null) {
                    set.remove(this);
                }
            }
        }

        @Override
        public void cancel() {
            ScheduledTask task = delegate;
            if (task != null) {
                task.cancel();
            }
            finish();
        }

        @Override
        public boolean isCancelled() {
            ScheduledTask task = delegate;
            return done.get() || (task != null && task.isCancelled());
        }
    }
}
