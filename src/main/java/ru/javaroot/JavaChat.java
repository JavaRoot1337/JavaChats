package ru.javaroot;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.ServicePriority;
import ru.javaroot.javachats.aihelper.AiMod;
import ru.javaroot.javachats.api.JavaChatsApi;
import ru.javaroot.javachats.bstats.BStats;
import ru.javaroot.javachats.command.AiHelperCmd;
import ru.javaroot.javachats.command.GlavCmd;
import ru.javaroot.javachats.command.MsgCmd;
import ru.javaroot.javachats.listener.ChatList;
import ru.javaroot.javachats.listener.ChatEventList;
import ru.javaroot.javachats.listener.ConnectionList;
import ru.javaroot.javachats.integration.IntegrationLoader;
import ru.javaroot.javachats.integration.MetaProvider;
import ru.javaroot.javachats.utils.ChatLogger;
import ru.javaroot.javachats.utils.LogCfg;
import ru.javaroot.javachats.config.MessageSnapshot;
import ru.javaroot.javachats.config.RuntimeConfig;
import ru.javaroot.javachats.runtime.ServerScheduler;
import ru.javaroot.javachats.service.PrivateMessages;
import ru.javaroot.javachats.utils.LogVars;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class JavaChat extends JavaPlugin {
    private MetaProvider metaProvider;
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
        logs.reload();
        scheduler = new ServerScheduler(this);

        org.bukkit.plugin.PluginManager pm = getServer().getPluginManager();
        metaProvider = IntegrationLoader.loadLuckPerms();

        aiMod = new AiMod(this, scheduler);
        chatList = new ChatList(this, scheduler);
        privateMessages = new PrivateMessages(this, scheduler);
        if (!reloadConfigs()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
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

        pm.registerEvents(new ChatEventList(chatList), this);
        pm.registerEvents(new ConnectionList(this, chatList), this);
        new BStats(this);
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

    public boolean reloadConfigs() {
        reloadConfig();
        loadConfigDefaults();
        File messageFile = new File(getDataFolder(), "message.yml");
        RuntimeConfig newRuntimeConfig;
        MessageSnapshot newMessageSnapshot;
        try {
            FileConfiguration newMessageConfig = YamlConfiguration.loadConfiguration(messageFile);
            newRuntimeConfig = RuntimeConfig.from(getConfig());
            newMessageSnapshot = MessageSnapshot.from(newMessageConfig);
            newRuntimeConfig.validate();
            newMessageSnapshot.validate();
        } catch (RuntimeException error) {
            logs.warning("config-validation", LogVars.of("error", String.valueOf(error.getMessage())));
            return false;
        }
        runtimeConfig = newRuntimeConfig;
        messageSnapshot = newMessageSnapshot;
        logs.reload();
        if (aiMod != null) {
            aiMod.reload();
        }
        if (chatLogger != null) {
            chatLogger.reload();
        }
        return true;
    }

    private void loadConfigDefaults() {
        try (InputStream stream = getResource("config.yml")) {
            if (stream == null) {
                logs.warning("config-resource-missing", LogVars.of("resource", "config.yml"));
                return;
            }
            FileConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            getConfig().addDefaults(defaults);
        } catch (IOException e) {
            logs.warning("config-defaults-load", LogVars.of(
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

    public MetaProvider getMetaProvider() {
        return metaProvider;
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
