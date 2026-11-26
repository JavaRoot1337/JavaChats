package ru.javaroot.javachats.upd;

import java.util.ArrayList;
import java.util.List;

public final class Ver implements Comparable<Ver> {
    private final List<Integer> parts;

    private Ver(List<Integer> parts) {
        this.parts = parts;
    }

    public static Ver parse(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.startsWith("v") || value.startsWith("V")) {
            value = value.substring(1);
        }
        int dash = value.indexOf('-');
        if (dash >= 0) {
            value = value.substring(0, dash);
        }
        if (value.isEmpty()) {
            return null;
        }
        String[] split = value.split("\\.", -1);
        List<Integer> list = new ArrayList<>(split.length);
        for (String item : split) {
            if (item.isEmpty() || (item.length() > 1 && item.charAt(0) == '0')) {
                return null;
            }
            for (int i = 0; i < item.length(); i++) {
                if (item.charAt(i) < '0' || item.charAt(i) > '9') {
                    return null;
                }
            }
            try {
                list.add(Integer.parseInt(item));
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return new Ver(List.copyOf(list));
    }

    public static boolean isAtLeast(String raw, int reqMajor, int reqMinor) {
        Ver parsed = parse(raw);
        if (parsed == null) {
            return false;
        }
        int major = parsed.parts.isEmpty() ? 0 : parsed.parts.get(0);
        int minor = parsed.parts.size() > 1 ? parsed.parts.get(1) : 0;
        return major > reqMajor || (major == reqMajor && minor >= reqMinor);
    }

    @Override
    public int compareTo(Ver other) {
        int len = Math.max(parts.size(), other.parts.size());
        for (int i = 0; i < len; i++) {
            int a = i < parts.size() ? parts.get(i) : 0;
            int b = i < other.parts.size() ? other.parts.get(i) : 0;
            int cmp = Integer.compare(a, b);
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                sb.append('.');
            }
            sb.append(parts.get(i));
        }
        return sb.toString();
    }
}
