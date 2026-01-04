package ru.javaroot.javachats;

import net.luckperms.api.LuckPerms;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import ru.javaroot.javachats.command.MsgCommand;
import ru.javaroot.javachats.command.ReloadCommand;
import ru.javaroot.javachats.listener.ChatListener;
import ru.javaroot.javachats.listener.ConnectionListener;

public class JavaChats extends JavaPlugin {

    private static JavaChats instance;
    private LuckPerms luckPerms;
    private ru.javaroot.javachats.configuration.DiscordConfig discordConfig;
    private ru.javaroot.javachats.listener.DiscordListener discordListener;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        discordConfig = new ru.javaroot.javachats.configuration.DiscordConfig(this);

        // Hook into LuckPerms
        RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider != null) {
            this.luckPerms = provider.getProvider();
        } else {
            getLogger().warning("LuckPerms не найден! Префиксы работать не будут.");
        }

        // Register Commands
        getCommand("javachats").setExecutor(new ReloadCommand(this));
        getCommand("msg").setExecutor(new MsgCommand(this));

        // Register Listener
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new ConnectionListener(this), this);

        // Hook into DiscordSRV
        if (Bukkit.getPluginManager().isPluginEnabled("DiscordSRV")) {
            discordListener = new ru.javaroot.javachats.listener.DiscordListener(this);
            github.scarsz.discordsrv.DiscordSRV.api.subscribe(discordListener);
        }

        getLogger().info("JavaChats включен!");
    }

    @Override
    public void onDisable() {
        if (discordListener != null) {
            github.scarsz.discordsrv.DiscordSRV.api.unsubscribe(discordListener);
        }
        getLogger().info("JavaChats выключен!");
    }

    public static JavaChats getInstance() {
        return instance;
    }

    public LuckPerms getLuckPerms() {
        return luckPerms;
    }

    public ru.javaroot.javachats.configuration.DiscordConfig getDiscordConfig() {
        return discordConfig;
    }
}
