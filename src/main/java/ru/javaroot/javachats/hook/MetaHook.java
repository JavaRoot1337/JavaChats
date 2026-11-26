package ru.javaroot.javachats.hook;

import org.bukkit.plugin.PluginManager;

import java.util.UUID;

public interface MetaHook {
    String prefix(UUID playerId);

    String suffix(UUID playerId);

    static MetaHook load(PluginManager pm) {
        if (!pm.isPluginEnabled("LuckPerms")) {
            return new NoopMeta();
        }
        try {
            return LpHook.create();
        } catch (NoClassDefFoundError ignored) {
            return new NoopMeta();
        }
    }
}
