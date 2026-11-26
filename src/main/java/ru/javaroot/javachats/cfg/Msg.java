package ru.javaroot.javachats.cfg;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Msg {
    private static final List<String> TEXT_PATHS = List.of(
            "chat.local", "chat.global", "messages.reload", "messages.reload-failed",
            "messages.no-permission", "messages.cooldown", "messages.no-player",
            "messages.only-players", "messages.usage-javachats", "messages.usage-msg",
            "messages.cannot-msg-self", "pm.sender", "pm.receiver", "pm.hover",
            "ping.highlight.target", "ping.highlight.others", "ping.title.text",
            "ping.title.sub-text", "anti-caps.title", "anti-spam.subtitle", "anti-repeat",
            "ai-helper.log-message", "ai-helper.log-result", "ai-helper.verdict-punished",
            "ai-helper.verdict-clean", "ai-helper.error", "ai-helper.subtitle",
            "ai-helper.added-message", "ai-helper.already-added", "ai-helper.disabled",
            "ai-helper.usage-aihelper", "messages.update-available");
    private static final List<String> LIST_PATHS = List.of("join-quit.join", "join-quit.quit");

    private final Map<String, String> values;
    private final Map<String, List<String>> lists;

    public Msg(Map<String, String> values, Map<String, List<String>> lists) {
        this.values = Map.copyOf(values);
        Map<String, List<String>> copied = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : lists.entrySet()) {
            copied.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        this.lists = Map.copyOf(copied);
    }

    public static Msg from(FileConfiguration source) {
        Map<String, String> values = new HashMap<>();
        for (String path : TEXT_PATHS) {
            String value = source.getString(path);
            if (value != null) {
                values.put(path, value);
            }
        }
        Map<String, List<String>> lists = new HashMap<>();
        for (String path : LIST_PATHS) {
            lists.put(path, source.getStringList(path));
        }
        return new Msg(values, lists);
    }

    public String text(String path) {
        return values.get(path);
    }

    public List<String> list(String path) {
        return lists.getOrDefault(path, List.of());
    }

    public void validate() {
        String[] required = {
                "chat.local", "chat.global", "pm.sender", "pm.receiver",
                "messages.no-permission", "messages.cooldown", "messages.usage-msg"
        };
        for (String path : required) {
            String value = values.get(path);
            if (value == null || value.trim().isEmpty()) {
                throw new IllegalArgumentException("отсутствует сообщение: " + path);
            }
        }
        for (String path : new String[]{"chat.local", "chat.global", "pm.sender", "pm.receiver"}) {
            if (!values.get(path).contains("%message%")) {
                throw new IllegalArgumentException("в формате " + path + " нет %message%");
            }
        }
        if (!values.get("chat.local").contains("%player%") || !values.get("chat.global").contains("%player%")) {
            throw new IllegalArgumentException("формат чата требует %player%");
        }
    }
}
