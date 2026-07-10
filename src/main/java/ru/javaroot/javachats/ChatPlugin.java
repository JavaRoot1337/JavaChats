package ru.javaroot.javachats;

import net.luckperms.api.LuckPerms;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.javaroot.javachats.command.PMCommand;
import ru.javaroot.javachats.command.AdminCommand;
import ru.javaroot.javachats.listener.ChatHandler;
import ru.javaroot.javachats.listener.JoinQuitListener;
import ru.javaroot.javachats.listener.DiscordHandler;
import java.io.File;

public class ChatPlugin extends JavaPlugin {
    private LuckPerms luckPerms;
    private FileConfiguration messageConfig;
    private DiscordHandler discordHandler;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveDefaultMessageConfig();
        reloadConfigs();

        var provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider != null) {
            luckPerms = provider.getProvider();
        } else {
            getLogger().warning("LuckPerms не найден! Префиксы работать не будут.");
        }

        getCommand("javachats").setExecutor(new AdminCommand(this));
        getCommand("msg").setExecutor(new PMCommand(this));

        var pm = getServer().getPluginManager();
        pm.registerEvents(new ChatHandler(this), this);
        pm.registerEvents(new JoinQuitListener(this), this);

        if (pm.isPluginEnabled("DiscordSRV")) {
            discordHandler = new DiscordHandler(this);
            github.scarsz.discordsrv.DiscordSRV.api.subscribe(discordHandler);
        }

        getLogger().info("JavaChats включен!");
    }

    @Override
    public void onDisable() {
        if (discordHandler != null) {
            github.scarsz.discordsrv.DiscordSRV.api.unsubscribe(discordHandler);
        }
        getLogger().info("JavaChats выключен!");
    }

    public void reloadConfigs() {
        reloadConfig();
        File messageFile = new File(getDataFolder(), "message.yml");
        messageConfig = YamlConfiguration.loadConfiguration(messageFile);
    }

    private void saveDefaultMessageConfig() {
        File file = new File(getDataFolder(), "message.yml");
        if (!file.exists()) {
            saveResource("message.yml", false);
        }
    }

    public FileConfiguration getMessageConfig() {
        return messageConfig;
    }

    public LuckPerms getLuckPerms() {
        return luckPerms;
    }
}
