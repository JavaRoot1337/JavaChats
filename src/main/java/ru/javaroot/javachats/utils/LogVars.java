package ru.javaroot.javachats.utils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class LogVars {
    private LogVars() {
    }

    public static Map<String, String> of() {
        return Collections.emptyMap();
    }

    public static Map<String, String> of(String k1, String v1) {
        Map<String, String> values = new HashMap<String, String>();
        values.put(k1, v1);
        return values;
    }

    public static Map<String, String> of(String k1, String v1, String k2, String v2) {
        Map<String, String> values = of(k1, v1);
        values.put(k2, v2);
        return values;
    }

    public static Map<String, String> of(String k1, String v1, String k2, String v2,
            String k3, String v3) {
        Map<String, String> values = of(k1, v1, k2, v2);
        values.put(k3, v3);
        return values;
    }
}

