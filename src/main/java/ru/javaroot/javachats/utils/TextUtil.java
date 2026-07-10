package ru.javaroot.javachats.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextUtil {
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([a-fA-F0-9]{6})");
    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("(?i)§[0-9a-fk-orx]");

    public static Component format(String s) {
        if (s == null) return Component.empty();
        Matcher matcher = HEX_PATTERN.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            matcher.appendReplacement(sb, "§x§" + hex.charAt(0) + "§" + hex.charAt(1) + "§" + hex.charAt(2) + "§" + hex.charAt(3) + "§" + hex.charAt(4) + "§" + hex.charAt(5));
        }
        matcher.appendTail(sb);
        return LegacyComponentSerializer.legacySection().deserialize(sb.toString().replace('&', '§'));
    }

    public static String getColors(String s) {
        if (s == null) return "";
        String formatted = LegacyComponentSerializer.legacySection().serialize(format(s));
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
