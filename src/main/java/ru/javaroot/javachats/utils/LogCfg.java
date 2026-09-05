package ru.javaroot.javachats.utils;

import ru.javaroot.JavaChat;

import java.util.HashMap;
import java.util.Map;

public class LogCfg {
    private final JavaChat plugin;
    private volatile Map<String, String> values = Map.of();

    public LogCfg(JavaChat plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        Map<String, String> next = new HashMap<>();
        for (String path : plugin.getConfig().getKeys(true)) {
            String value = plugin.getConfig().getString(path);
            if (value != null) {
                next.put(path, value);
            }
        }
        values = Map.copyOf(next);
    }

    public void warning(String key, Map<String, String> vars) {
        if (!Boolean.parseBoolean(values.get("logs.console.enabled"))) {
            return;
        }
        String message = render("logs.console.messages." + key, vars);
        if (message != null && !message.isEmpty()) {
            plugin.getLogger().warning(message);
        }
    }

    public String render(String path, Map<String, String> vars) {
        String template = values.get(path);
        if (template == null) {
            return null;
        }
        String result = template;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            result = result.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return result;
    }
}
