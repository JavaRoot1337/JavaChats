package ru.javaroot.javachats;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import ru.javaroot.javachats.ai.AiSvc;
import ru.javaroot.javachats.api.ApiProvider;
import ru.javaroot.javachats.api.ChatSvc;
import ru.javaroot.javachats.api.JavaChatsApi;
import ru.javaroot.javachats.api.ModSvc;
import ru.javaroot.javachats.api.PmSvc;
import ru.javaroot.javachats.cfg.Cfg;
import ru.javaroot.javachats.cfg.LocaleMgr;
import ru.javaroot.javachats.cfg.LogCfg;
import ru.javaroot.javachats.cfg.Msg;
import ru.javaroot.javachats.chat.ChatEng;
import ru.javaroot.javachats.chat.ChatLog;
import ru.javaroot.javachats.chat.ChatPing;
import ru.javaroot.javachats.chat.PmSvcImpl;
import ru.javaroot.javachats.cmd.AiCmd;
import ru.javaroot.javachats.cmd.JavaChatsCmd;
import ru.javaroot.javachats.cmd.MsgCmd;
import ru.javaroot.javachats.hook.MetaHook;
import ru.javaroot.javachats.list.ChatList;
import ru.javaroot.javachats.list.ConnList;
import ru.javaroot.javachats.plat.Exec;
import ru.javaroot.javachats.upd.GhUpd;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public final class JavaChats extends JavaPlugin {
    private static final int BSTATS_ID = 34054;
    private static final long UPDATE_DELAY_TICKS = 60L;

    private LocaleMgr localeMgr;
    private LogCfg logs;
    private Exec exec;
    private GhUpd updater;
    private AiSvc ai;
    private ChatEng chat;
    private PmSvcImpl pmSvc;
    private ChatLog chatLog;

    private volatile Cfg cfg;
    private volatile Msg msg;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        PluginManager pm = getServer().getPluginManager();
        registerPermissions(pm);

        localeMgr = new LocaleMgr(this);
        logs = new LogCfg(getLogger());
        logs.reload(localeMgr.bundledConfig("ru"));
        exec = new Exec(this);
        updater = new GhUpd(this, exec, () -> cfg, this::announceUpdate);

        MetaHook meta = MetaHook.load(pm);
        ai = new AiSvc(this, logs, exec, () -> msg);
        chatLog = new ChatLog(this, logs);
        ChatPing pinger = new ChatPing(() -> cfg, () -> msg, logs);
        chat = new ChatEng(exec, () -> cfg, () -> msg, meta, ai, pinger, chatLog);
        pmSvc = new PmSvcImpl(exec, () -> msg, chatLog);

        if (!reloadConfigs()) {
            pm.disablePlugin(this);
            return;
        }

        JavaChatsApi api = new JavaChatsApi() {
            @Override
            public String apiVersion() {
                return JavaChatsApi.API_VERSION;
            }

            @Override
            public ChatSvc chat() {
                return chat;
            }

            @Override
            public PmSvc privateMessages() {
                return pmSvc;
            }

            @Override
            public ModSvc moderation() {
                return ai;
            }
        };
        Bukkit.getServicesManager().register(JavaChatsApi.class, api, this, ServicePriority.Normal);
        ApiProvider.register(api);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var reg = event.registrar();
            reg.register(JavaChatsCmd.build(this::reloadConfigs, () -> msg), "Управление JavaChats");
            reg.register(MsgCmd.build(pmSvc, () -> msg), "Личное сообщение", List.of("tell", "w"));
            reg.register(AiCmd.build(ai, exec, () -> msg), "Обучение AIHelper");
        });

        pm.registerEvents(new ChatList(chat), this);
        pm.registerEvents(new ConnList(() -> cfg, () -> msg, chat), this);
        new Metrics(this, BSTATS_ID);

        if (cfg.updateCheck()) {
            exec.runGlobalLater(updater::checkForUpdates, UPDATE_DELAY_TICKS);
        }
        getLogger().info("JavaChats включён");
    }

    @Override
    public void onDisable() {
        ApiProvider.unregister();
        Bukkit.getServicesManager().unregisterAll(this);
        if (chat != null) {
            chat.close();
        }
        if (ai != null) {
            ai.close();
        }
        if (chatLog != null) {
            chatLog.close();
        }
        if (updater != null) {
            updater.close();
        }
        if (exec != null) {
            exec.close();
        }
        getLogger().info("JavaChats выключен");
    }

    public boolean reloadConfigs() {
        boolean wasUpdateCheck = cfg != null && cfg.updateCheck();
        LocaleMgr.Active active;
        Cfg nextCfg;
        Msg nextMsg;
        try {
            reloadConfig();
            active = localeMgr.load();
            addDefaults(active.config(), active.locale(), "config.yml");
            addDefaults(active.message(), active.locale(), "message.yml");
            nextCfg = Cfg.from(active.config());
            nextMsg = Msg.from(active.message());
            nextCfg.validate();
            nextMsg.validate();
        } catch (RuntimeException ex) {
            logs.warning("config-validation", Map.of("error", String.valueOf(ex.getMessage())));
            return false;
        }
        if (ai != null && !ai.reload(nextCfg, active.directory())) {
            return false;
        }
        cfg = nextCfg;
        msg = nextMsg;
        logs.reload(active.config());
        if (chatLog != null) {
            chatLog.reload(active.config());
        }
        if (updater != null && !wasUpdateCheck && nextCfg.updateCheck()) {
            exec.runGlobalLater(updater::checkForUpdates, UPDATE_DELAY_TICKS);
        }
        return true;
    }

    private void addDefaults(FileConfiguration target, String locale, String fileName) {
        String resource = "locate/" + locale + "/" + fileName;
        try (InputStream stream = getResource(resource)) {
            if (stream == null) {
                logs.warning("config-resource-missing", Map.of("resource", resource));
                return;
            }
            FileConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            target.addDefaults(defaults);
        } catch (IOException ex) {
            logs.warning("config-defaults-load", Map.of("resource", resource, "error", String.valueOf(ex.getMessage())));
        }
    }

    private void announceUpdate(GhUpd checker) {
        if (cfg == null || !cfg.updateCheck() || msg == null) {
            return;
        }
        String tpl = msg.text("messages.update-available");
        if (tpl == null || tpl.trim().isEmpty()) {
            return;
        }
        String text = tpl.replace("%current%", checker.getCurrentVersion())
                .replace("%latest%", checker.getLatestVersion())
                .replace("%github%", checker.getReleaseUrl()).trim();
        for (String line : text.split("\\r?\\n")) {
            if (!line.trim().isEmpty()) {
                getLogger().info(line);
            }
        }
    }

    private static void registerPermissions(PluginManager pm) {
        registerPerm(pm, "javachats.admin", "Управление JavaChats и AIHelper", PermissionDefault.OP);
        registerPerm(pm, "javachats.ai.log", "Просмотр логов AIHelper", PermissionDefault.OP);
        registerPerm(pm, "javachats.local.view", "Просмотр локального чата", PermissionDefault.TRUE);
        registerPerm(pm, "javachats.local.send", "Отправка сообщений в локальный чат", PermissionDefault.TRUE);
        registerPerm(pm, "javachats.global.view", "Просмотр глобального чата", PermissionDefault.TRUE);
        registerPerm(pm, "javachats.global.send", "Отправка сообщений в глобальный чат", PermissionDefault.TRUE);
        registerPerm(pm, "javachats.msg", "Использование личных сообщений", PermissionDefault.TRUE);
    }

    private static void registerPerm(PluginManager pm, String name, String desc, PermissionDefault def) {
        if (pm.getPermission(name) == null) {
            pm.addPermission(new Permission(name, desc, def));
        }
    }
}
