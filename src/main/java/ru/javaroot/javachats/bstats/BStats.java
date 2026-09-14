package ru.javaroot.javachats.bstats;

import org.bstats.bukkit.Metrics;
import ru.javaroot.JavaChat;

public class BStats {
    private static final int PLUGIN_ID = 34054;

    public BStats(JavaChat plugin) {
        new Metrics(plugin, PLUGIN_ID);
    }
}
