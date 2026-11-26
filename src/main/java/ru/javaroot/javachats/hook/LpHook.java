package ru.javaroot.javachats.hook;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.UUID;

public final class LpHook implements MetaHook {
    private final LuckPerms lp;

    private LpHook(LuckPerms lp) {
        this.lp = lp;
    }

    public static MetaHook create() {
        RegisteredServiceProvider<LuckPerms> provider =
                Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        return provider == null ? new NoopMeta() : new LpHook(provider.getProvider());
    }

    @Override
    public String prefix(UUID playerId) {
        User user = lp.getUserManager().getUser(playerId);
        if (user == null) {
            return "";
        }
        String value = user.getCachedData().getMetaData().getPrefix();
        return value == null ? "" : value;
    }

    @Override
    public String suffix(UUID playerId) {
        User user = lp.getUserManager().getUser(playerId);
        if (user == null) {
            return "";
        }
        String value = user.getCachedData().getMetaData().getSuffix();
        return value == null ? "" : value;
    }
}
