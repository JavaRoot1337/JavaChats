package ru.javaroot.javachats.aihelper;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AiRules {
    private final Plugin plugin;
    private final Map<String, RuleInfo> rules = new HashMap<>();
    private final List<String> trainingPlus = new ArrayList<>();
    private final List<String> trainingMinus = new ArrayList<>();

    public AiRules(Plugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "AIHELPER.yml");
        if (!file.exists()) {
            plugin.saveResource("AIHELPER.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        rules.clear();

        ConfigurationSection sec = config.getConfigurationSection("rules");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                String desc = sec.getString(key + ".description", "");
                String cmd = sec.getString(key + ".punish-command", "");
                rules.put(key, new RuleInfo(key, desc, cmd));
            }
        }

        loadTrainingData();
    }

    private void loadTrainingData() {
        trainingPlus.clear();
        trainingMinus.clear();

        trainingPlus.addAll(loadTrainingFile("learning/trainingplus.txt"));
        trainingMinus.addAll(loadTrainingFile("learning/trainingminus.txt"));
    }

    private List<String> loadTrainingFile(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Could not create " + fileName + ": " + e.getMessage());
            }
            return new ArrayList<>();
        }
        try {
            return Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not read " + fileName + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public synchronized boolean addTrainingMessage(boolean plus, String message) {
        String cleanedMsg = message.trim();
        List<String> currentList = plus ? trainingPlus : trainingMinus;

        for (String s : currentList) {
            if (s.trim().equalsIgnoreCase(cleanedMsg)) {
                return false;
            }
        }

        String fileName = plus ? "learning/trainingplus.txt" : "learning/trainingminus.txt";
        File file = new File(plugin.getDataFolder(), fileName);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try (FileWriter fw = new FileWriter(file, true);
                PrintWriter pw = new PrintWriter(fw)) {
            pw.println(cleanedMsg);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not write to " + fileName + ": " + e.getMessage());
            return false;
        }

        loadTrainingData();
        return true;
    }

    public Map<String, RuleInfo> getRules() {
        return rules;
    }

    public List<String> getTrainingPlus() {
        return trainingPlus;
    }

    public List<String> getTrainingMinus() {
        return trainingMinus;
    }

    public static class RuleInfo {
        public final String id;
        public final String description;
        public final String punishCmd;

        public RuleInfo(String id, String description, String punishCmd) {
            this.id = id;
            this.description = description;
            this.punishCmd = punishCmd;
        }
    }
}
