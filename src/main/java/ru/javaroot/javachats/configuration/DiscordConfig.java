package ru.javaroot.javachats.configuration;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class DiscordConfig {

    private final JavaPlugin plugin;
    private FileConfiguration config;
    private File file;

    public DiscordConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        file = new File(plugin.getDataFolder(), "discord.yml");
        if (!file.exists()) {
            plugin.saveResource("discord.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public String getTechRoleId() {
        return config.getString("tech-role", "1391468653052035251");
    }

    public void reload() {
        load();
    }

    public boolean isDebug() {
        return config.getBoolean("debug", false);
    }
}
