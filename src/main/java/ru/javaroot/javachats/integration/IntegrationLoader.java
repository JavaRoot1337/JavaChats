package ru.javaroot.javachats.integration;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class IntegrationLoader {
    private IntegrationLoader() {
    }

    public static MetaProvider loadLuckPerms() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("LuckPerms");
        if (plugin == null || !plugin.isEnabled()) {
            return new EmptyMetaProvider();
        }
        try {
            return LuckPermsIntegration.create();
        } catch (NoClassDefFoundError error) {
            return new EmptyMetaProvider();
        }
    }
}
