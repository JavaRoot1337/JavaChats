package ru.javaroot.javachats.cfg;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public final class LogCfg {
    private final Logger logger;
    private volatile Map<String, String> values = Map.of();

    public LogCfg(Logger logger) {
        this.logger = logger;
    }

    public void reload(FileConfiguration config) {
        Map<String, String> next = new HashMap<>();
        for (String path : config.getKeys(true)) {
            String value = config.getString(path);
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
            logger.warning(message);
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
