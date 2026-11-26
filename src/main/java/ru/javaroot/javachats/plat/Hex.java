package ru.javaroot.javachats.plat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Hex {
    private static final Pattern COLOR_TOKEN = Pattern.compile("(?i)(?:&#[0-9a-f]{6}|&[0-9a-fk-or])");
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private Hex() {
    }

    public static Component parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        return LEGACY.deserialize(raw);
    }

    public static Component literal(String raw) {
        return raw == null || raw.isEmpty() ? Component.empty() : Component.text(raw);
    }

    public static Component formatTemplate(String template, String message) {
        if (template == null || template.isEmpty()) {
            return Component.empty();
        }
        int idx = template.indexOf("%message%");
        if (idx < 0) {
            return parse(template);
        }
        return parse(template.substring(0, idx))
                .append(literal(message))
                .append(parse(template.substring(idx + "%message%".length())));
    }

    public static String plain(Component component) {
        return component == null ? "" : PLAIN.serialize(component);
    }

    public static String activeColors(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        Matcher matcher = COLOR_TOKEN.matcher(raw);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String token = matcher.group();
            if (token.equalsIgnoreCase("&r")) {
                out.setLength(0);
            } else {
                out.append(token);
            }
        }
        return out.toString();
    }

    public static Title.Times times(long fadeInMs, long stayMs, long fadeOutMs) {
        return Title.Times.times(
                Duration.ofMillis(Math.max(0L, fadeInMs)),
                Duration.ofMillis(Math.max(0L, stayMs)),
                Duration.ofMillis(Math.max(0L, fadeOutMs)));
    }
}
