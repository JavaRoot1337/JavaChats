package ru.javaroot.javachats.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextUtil {
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([a-fA-F0-9]{6})");
    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("(?i)§[0-9a-fk-orx]");
    private static final Pattern AMPERSAND_CODE_PATTERN = Pattern.compile("(?i)&([0-9a-fk-or])");
    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.builder()
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .character('§')
            .build();

    private TextUtil() {
    }

    public static Component format(String s) {
        if (s == null) {
            return Component.empty();
        }
        Matcher matcher = HEX_PATTERN.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            matcher.appendReplacement(sb, "§x§" + hex.charAt(0) + "§" + hex.charAt(1) + "§" + hex.charAt(2) + "§"
                    + hex.charAt(3) + "§" + hex.charAt(4) + "§" + hex.charAt(5));
        }
        matcher.appendTail(sb);
        return SERIALIZER.deserialize(AMPERSAND_CODE_PATTERN.matcher(sb.toString()).replaceAll("§$1"));
    }

    public static Component literal(String s) {
        return s == null ? Component.empty() : Component.text(s);
    }

    public static Component formatTemplate(String template, String message) {
        if (template == null) {
            return Component.empty();
        }
        int marker = template.indexOf("%message%");
        if (marker < 0) {
            return format(template);
        }
        return format(template.substring(0, marker))
                .append(literal(message))
                .append(format(template.substring(marker + "%message%".length())));
    }

    public static String plain(Component component) {
        if (component == null) {
            return "";
        }
        return COLOR_CODE_PATTERN.matcher(SERIALIZER.serialize(component)).replaceAll("");
    }

    public static Title.Times titleTimes(final Duration fadeIn, final Duration stay, final Duration fadeOut) {
        return new Title.Times() {
            @Override
            public Duration fadeIn() {
                return fadeIn;
            }

            @Override
            public Duration stay() {
                return stay;
            }

            @Override
            public Duration fadeOut() {
                return fadeOut;
            }
        };
    }

    public static String getColors(String s) {
        if (s == null) {
            return "";
        }
        String formatted = SERIALIZER.serialize(format(s));
        Matcher matcher = COLOR_CODE_PATTERN.matcher(formatted);
        StringBuilder colors = new StringBuilder();
        while (matcher.find()) {
            String code = matcher.group();
            if (code.equalsIgnoreCase("§r")) {
                colors.setLength(0);
            } else {
                colors.append(code);
            }
        }
        return colors.toString();
    }
}
