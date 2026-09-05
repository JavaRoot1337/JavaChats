package ru.javaroot.javachats.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record MessageSnapshot(Map<String, String> values, Map<String, List<String>> lists) {
    private static final List<String> TEXT_PATHS = List.of(
            "chat.local", "chat.global", "messages.reload", "messages.no-permission", "messages.cooldown",
            "messages.no-player", "messages.only-players", "messages.usage-javachats", "messages.usage-msg",
            "messages.cannot-msg-self", "pm.sender", "pm.receiver", "pm.hover", "ping.highlight.target",
            "ping.highlight.others", "ping.title.text", "ping.title.sub-text", "anti-caps.title",
            "anti-spam.subtitle", "anti-repeat", "ai-helper.log-message", "ai-helper.log-result",
            "ai-helper.verdict-punished", "ai-helper.verdict-clean", "ai-helper.error", "ai-helper.subtitle",
            "ai-helper.added-message", "ai-helper.already-added", "ai-helper.disabled", "ai-helper.usage-aihelper");

    private static final List<String> LIST_PATHS = List.of("join-quit.join", "join-quit.quit");

    public static MessageSnapshot from(FileConfiguration source) {
        Map<String, String> values = new HashMap<>();
        for (String path : TEXT_PATHS) {
            String value = source.getString(path);
            if (value != null) {
                values.put(path, value);
            }
        }

        Map<String, List<String>> lists = new HashMap<>();
        for (String path : LIST_PATHS) {
            lists.put(path, List.copyOf(source.getStringList(path)));
        }
        return new MessageSnapshot(Map.copyOf(values), Map.copyOf(lists));
    }

    public String text(String path) {
        return values.get(path);
    }

    public List<String> list(String path) {
        return lists.getOrDefault(path, List.of());
    }
}
