package ru.javaroot.javachats.integration;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.UUID;

public final class LuckPermsIntegration implements MetaProvider {
    private final LuckPerms luckPerms;

    private LuckPermsIntegration(LuckPerms luckPerms) {
        this.luckPerms = luckPerms;
    }

    public static MetaProvider create() {
        RegisteredServiceProvider<LuckPerms> provider =
                Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        return provider == null ? new EmptyMetaProvider() : new LuckPermsIntegration(provider.getProvider());
    }

    @Override
    public String prefix(UUID playerId) {
        User user = luckPerms.getUserManager().getUser(playerId);
        if (user == null || user.getCachedData().getMetaData().getPrefix() == null) {
            return "";
        }
        return user.getCachedData().getMetaData().getPrefix();
    }

    @Override
    public String suffix(UUID playerId) {
        User user = luckPerms.getUserManager().getUser(playerId);
        if (user == null || user.getCachedData().getMetaData().getSuffix() == null) {
            return "";
        }
        return user.getCachedData().getMetaData().getSuffix();
    }
}
