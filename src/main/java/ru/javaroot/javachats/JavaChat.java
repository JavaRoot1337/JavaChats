package ru.javaroot.javachats;

import net.luckperms.api.LuckPerms;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.javaroot.javachats.command.MsgCmd;
import ru.javaroot.javachats.command.GlavCmd;
import ru.javaroot.javachats.listener.ChatList;
import ru.javaroot.javachats.listener.ConnectionList;
import ru.javaroot.javachats.aihelper.AiMod;
import ru.javaroot.javachats.command.AiHelperCmd;
import ru.javaroot.javachats.utils.ChatLogger;

import java.io.File;

public class JavaChat extends JavaPlugin {
    private LuckPerms luckPerms;
    private FileConfiguration messageConfig;
    private AiMod aiMod;
    private ChatLogger chatLogger;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveDefaultMessageConfig();

        chatLogger = new ChatLogger();
        chatLogger.init(new File(getDataFolder(), "logs"));

        var provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider != null) {
            luckPerms = provider.getProvider();
        }

        aiMod = new AiMod(this);
        reloadConfigs();

        getCommand("javachats").setExecutor(new GlavCmd(this));
        getCommand("msg").setExecutor(new MsgCmd(this));

        var aiHelperCmd = new AiHelperCmd(this);
        getCommand("aihelper").setExecutor(aiHelperCmd);
        getCommand("aihelper").setTabCompleter(aiHelperCmd);

        var pm = getServer().getPluginManager();
        pm.registerEvents(new ChatList(this), this);
        pm.registerEvents(new ConnectionList(this), this);
    }

    @Override
    public void onDisable() {
    }

    public void reloadConfigs() {
        reloadConfig();
        File messageFile = new File(getDataFolder(), "message.yml");
        messageConfig = YamlConfiguration.loadConfiguration(messageFile);
        if (aiMod != null) {
            aiMod.reload();
        }
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

    public AiMod getAiMod() {
        return aiMod;
    }

    public ChatLogger getChatLogger() {
        return chatLogger;
    }
}
