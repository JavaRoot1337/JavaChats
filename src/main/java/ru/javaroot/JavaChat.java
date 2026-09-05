package ru.javaroot;

import net.luckperms.api.LuckPerms;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.ServicePriority;
import ru.javaroot.javachats.aihelper.AiMod;
import ru.javaroot.javachats.api.JavaChatsApi;
import ru.javaroot.javachats.command.AiHelperCmd;
import ru.javaroot.javachats.command.GlavCmd;
import ru.javaroot.javachats.command.MsgCmd;
import ru.javaroot.javachats.listener.ChatList;
import ru.javaroot.javachats.listener.ConnectionList;
import ru.javaroot.javachats.utils.ChatLogger;
import ru.javaroot.javachats.utils.LogCfg;
import ru.javaroot.javachats.config.MessageSnapshot;
import ru.javaroot.javachats.config.RuntimeConfig;
import ru.javaroot.javachats.runtime.ServerScheduler;
import ru.javaroot.javachats.service.PrivateMessages;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class JavaChat extends JavaPlugin {
    private LuckPerms luckPerms;
    private FileConfiguration messageConfig;
    private AiMod aiMod;
    private ChatLogger chatLogger;
    private ChatList chatList;
    private LogCfg logs;
    private ServerScheduler scheduler;
    private volatile RuntimeConfig runtimeConfig;
    private volatile MessageSnapshot messageSnapshot;
    private volatile JavaChatsApi api;
    private PrivateMessages privateMessages;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveDefaultMessageConfig();
        logs = new LogCfg(this);
        scheduler = new ServerScheduler(this);

        var provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider != null) {
            luckPerms = provider.getProvider();
        }

        aiMod = new AiMod(this, scheduler);
        chatList = new ChatList(this, scheduler);
        privateMessages = new PrivateMessages(this, scheduler);
        reloadConfigs();
        api = new JavaChatsApi() {
            @Override
            public String apiVersion() {
                return JavaChatsApi.API_VERSION;
            }

            @Override
            public ru.javaroot.javachats.api.ChatService chat() {
                return chatList;
            }

            @Override
            public ru.javaroot.javachats.api.PrivateMessageService privateMessages() {
                return privateMessages;
            }

            @Override
            public ru.javaroot.javachats.api.ModerationService moderation() {
                return aiMod;
            }
        };
        Bukkit.getServicesManager().register(JavaChatsApi.class, api, this, ServicePriority.Normal);

        chatLogger = new ChatLogger(this);
        chatLogger.init();

        registerCommands();

        var pm = getServer().getPluginManager();
        pm.registerEvents(chatList, this);
        pm.registerEvents(new ConnectionList(this, chatList), this);
    }

    @Override
    public void onDisable() {
        if (chatList != null) {
            chatList.close();
        }
        if (aiMod != null) {
            aiMod.close();
        }
        if (chatLogger != null) {
            chatLogger.close();
        }
        if (scheduler != null) {
            scheduler.close();
        }
        Bukkit.getServicesManager().unregisterAll(this);
        api = null;
    }

    public void reloadConfigs() {
        reloadConfig();
        loadConfigDefaults();
        logs.reload();
        File messageFile = new File(getDataFolder(), "message.yml");
        FileConfiguration newMessageConfig = YamlConfiguration.loadConfiguration(messageFile);
        RuntimeConfig newRuntimeConfig = RuntimeConfig.from(getConfig());
        MessageSnapshot newMessageSnapshot = MessageSnapshot.from(newMessageConfig);
        messageConfig = newMessageConfig;
        runtimeConfig = newRuntimeConfig;
        messageSnapshot = newMessageSnapshot;
        if (aiMod != null) {
            aiMod.reload();
        }
        if (chatLogger != null) {
            chatLogger.reload();
        }
    }

    private void loadConfigDefaults() {
        try (InputStream stream = getResource("config.yml")) {
            if (stream == null) {
                logs.warning("config-resource-missing", Map.of("resource", "config.yml"));
                return;
            }
            FileConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            getConfig().addDefaults(defaults);
        } catch (IOException e) {
            logs.warning("config-defaults-load", Map.of(
                    "resource", "config.yml",
                    "error", String.valueOf(e.getMessage())));
        }
    }

    private void registerCommands() {
        PluginCommand javachats = getCommand("javachats");
        if (javachats != null) {
            javachats.setExecutor(new GlavCmd(this));
        }

        PluginCommand msg = getCommand("msg");
        if (msg != null) {
            msg.setExecutor(new MsgCmd(this));
        }

        PluginCommand aihelper = getCommand("aihelper");
        if (aihelper != null) {
            AiHelperCmd cmd = new AiHelperCmd(this);
            aihelper.setExecutor(cmd);
            aihelper.setTabCompleter(cmd);
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

    public RuntimeConfig getRuntimeConfig() {
        return runtimeConfig;
    }

    public MessageSnapshot getMessageSnapshot() {
        return messageSnapshot;
    }

    public ServerScheduler getScheduler() {
        return scheduler;
    }

    public JavaChatsApi getApi() {
        return api;
    }

    public PrivateMessages getPrivateMessages() {
        return privateMessages;
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

    public LogCfg getLogs() {
        return logs;
    }
}
