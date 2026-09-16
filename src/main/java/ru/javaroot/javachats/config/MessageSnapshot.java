package ru.javaroot.javachats.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MessageSnapshot {
    private static final List<String> TEXT_PATHS = Collections.unmodifiableList(Arrays.asList(
            "chat.local", "chat.global", "messages.reload", "messages.reload-failed", "messages.no-permission", "messages.cooldown",
            "messages.no-player", "messages.only-players", "messages.usage-javachats", "messages.usage-msg",
            "messages.cannot-msg-self", "pm.sender", "pm.receiver", "pm.hover", "ping.highlight.target",
            "ping.highlight.others", "ping.title.text", "ping.title.sub-text", "anti-caps.title",
            "anti-spam.subtitle", "anti-repeat", "ai-helper.log-message", "ai-helper.log-result",
            "ai-helper.verdict-punished", "ai-helper.verdict-clean", "ai-helper.error", "ai-helper.subtitle",
            "ai-helper.added-message", "ai-helper.already-added", "ai-helper.disabled", "ai-helper.usage-aihelper"));
    private static final List<String> LIST_PATHS = Collections.unmodifiableList(
            Arrays.asList("join-quit.join", "join-quit.quit"));

    private final Map<String, String> values;
    private final Map<String, List<String>> lists;

    public MessageSnapshot(Map<String, String> values, Map<String, List<String>> lists) {
        this.values = Collections.unmodifiableMap(new HashMap<String, String>(values));
        Map<String, List<String>> copied = new HashMap<String, List<String>>();
        for (Map.Entry<String, List<String>> entry : lists.entrySet()) {
            copied.put(entry.getKey(), Collections.unmodifiableList(
                    new java.util.ArrayList<String>(entry.getValue())));
        }
        this.lists = Collections.unmodifiableMap(copied);
    }

    public static MessageSnapshot from(FileConfiguration source) {
        Map<String, String> values = new HashMap<String, String>();
        for (String path : TEXT_PATHS) {
            String value = source.getString(path);
            if (value != null) {
                values.put(path, value);
            }
        }

        Map<String, List<String>> lists = new HashMap<String, List<String>>();
        for (String path : LIST_PATHS) {
            lists.put(path, Collections.unmodifiableList(source.getStringList(path)));
        }
        return new MessageSnapshot(values, lists);
    }

    public Map<String, String> values() { return values; }
    public Map<String, List<String>> lists() { return lists; }

    public String text(String path) { return values.get(path); }

    public void validate() {
        String[] required = {"chat.local", "chat.global", "pm.sender", "pm.receiver",
                "messages.no-permission", "messages.cooldown", "messages.usage-msg"};
        for (String path : required) {
            String value = values.get(path);
            if (value == null || value.trim().isEmpty()) {
                throw new IllegalArgumentException("missing message: " + path);
            }
        }
        for (String path : new String[] {"chat.local", "chat.global", "pm.sender", "pm.receiver"}) {
            String value = values.get(path);
            if (!value.contains("%message%")) {
                throw new IllegalArgumentException("missing %message% in: " + path);
            }
        }
        if (!values.get("chat.local").contains("%player%")
                || !values.get("chat.global").contains("%player%")) {
            throw new IllegalArgumentException("chat format requires %player%");
        }
    }

    public List<String> list(String path) {
        List<String> result = lists.get(path);
        return result == null ? Collections.<String>emptyList() : result;
    }
}
