package ru.javaroot;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.ServicePriority;
import org.bstats.bukkit.Metrics;
import ru.javaroot.javachats.aihelper.AiMod;
import ru.javaroot.javachats.api.JavaChatsApi;
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
import ru.javaroot.javachats.config.LocaleConfigManager;
import ru.javaroot.javachats.config.RuntimeConfig;
import ru.javaroot.javachats.runtime.ServerScheduler;
import ru.javaroot.javachats.service.PrivateMessages;
import ru.javaroot.javachats.update.gitupdater;
import ru.javaroot.javachats.utils.LogVars;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class JavaChat extends JavaPlugin {
    private static final int BSTATS_PLUGIN_ID = 34054;
    private static final long UPDATE_CHECK_DELAY_TICKS = 60L;
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
    private LocaleConfigManager localeConfigManager;
    private gitupdater updateChecker;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        localeConfigManager = new LocaleConfigManager(this);
        logs = new LogCfg(this);
        logs.reload(localeConfigManager.bundledConfig("ru"));
        scheduler = new ServerScheduler(this);
        updateChecker = new gitupdater(this, scheduler, this::announceUpdate);

        org.bukkit.plugin.PluginManager pm = getServer().getPluginManager();
        metaProvider = IntegrationLoader.loadLuckPerms();

        aiMod = new AiMod(this, scheduler);
        chatList = new ChatList(this, scheduler);
        privateMessages = new PrivateMessages(this, scheduler);
        chatLogger = new ChatLogger(this);
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

        registerCommands();

        pm.registerEvents(new ChatEventList(chatList), this);
        pm.registerEvents(new ConnectionList(this, chatList), this);
        new Metrics(this, BSTATS_PLUGIN_ID);
        if (runtimeConfig.updateCheck()) {
            scheduler.runServerLater(updateChecker::checkForUpdates, UPDATE_CHECK_DELAY_TICKS);
        }
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
        if (updateChecker != null) {
            updateChecker.close();
        }
        if (scheduler != null) {
            scheduler.close();
        }
        Bukkit.getServicesManager().unregisterAll(this);
        api = null;
    }

    public boolean reloadConfigs() {
        boolean updateCheckWasEnabled = runtimeConfig != null && runtimeConfig.updateCheck();
        RuntimeConfig newRuntimeConfig;
        MessageSnapshot newMessageSnapshot;
        FileConfiguration newConfig;
        LocaleConfigManager.ActiveConfig activeConfig;
        try {
            reloadConfig();
            activeConfig = localeConfigManager.load();
            newConfig = activeConfig.config();
            loadDefaults(newConfig, activeConfig.locale(), "config.yml");
            loadDefaults(activeConfig.message(), activeConfig.locale(), "message.yml");
            newRuntimeConfig = RuntimeConfig.from(newConfig);
            newMessageSnapshot = MessageSnapshot.from(activeConfig.message());
            newRuntimeConfig.validate();
            newMessageSnapshot.validate();
        } catch (RuntimeException error) {
            logs.warning("config-validation", LogVars.of("error", String.valueOf(error.getMessage())));
            return false;
        }
        if (aiMod != null && !aiMod.reload(newRuntimeConfig, activeConfig.directory())) {
            return false;
        }
        runtimeConfig = newRuntimeConfig;
        messageSnapshot = newMessageSnapshot;
        logs.reload(newConfig);
        if (chatLogger != null) {
            chatLogger.reload(newConfig);
        }
        if (updateChecker != null && !updateCheckWasEnabled && newRuntimeConfig.updateCheck()) {
            scheduler.runServerLater(updateChecker::checkForUpdates, UPDATE_CHECK_DELAY_TICKS);
        }
        return true;
    }

    private void loadDefaults(FileConfiguration config, String locale, String fileName) {
        String resource = "locate/" + locale + "/" + fileName;
        try (InputStream stream = getResource(resource)) {
            if (stream == null) {
                logs.warning("config-resource-missing", LogVars.of(
                        "resource", resource));
                return;
            }
            FileConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            config.addDefaults(defaults);
        } catch (IOException e) {
            logs.warning("config-defaults-load", LogVars.of(
                    "resource", resource,
                    "error", String.valueOf(e.getMessage())));
        }
    }

    private void announceUpdate(gitupdater checker) {
        if (runtimeConfig == null || !runtimeConfig.updateCheck()) {
            return;
        }
        String template = messageSnapshot.text("messages.update-available");
        if (template == null || template.trim().isEmpty()) {
            return;
        }
        String message = template.replace("%current%", checker.getCurrentVersion())
                .replace("%latest%", checker.getLatestVersion())
                .replace("%github%", checker.getReleaseUrl()).trim();
        for (String line : message.split("\\r?\\n")) {
            if (!line.trim().isEmpty()) {
                getLogger().info(line);
            }
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
