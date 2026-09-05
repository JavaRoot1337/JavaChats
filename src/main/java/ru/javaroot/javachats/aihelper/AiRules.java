package ru.javaroot.javachats.aihelper;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.javaroot.JavaChat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AiRules {
    private final JavaChat plugin;
    private final Map<String, RuleInfo> rules = new HashMap<>();
    private final List<String> trainingPlus = new ArrayList<>();
    private final List<String> trainingMinus = new ArrayList<>();
    private String systemPrompt;

    public AiRules(JavaChat plugin) {
        this.plugin = plugin;
    }

    public synchronized void load() {
        load(null);
    }

    public synchronized void load(String fallbackPrompt) {
        File promptFile = new File(plugin.getDataFolder(), "AIRULES.yml");
        boolean promptFileExists = promptFile.exists();
        if (!promptFile.exists()) {
            plugin.saveResource("AIRULES.yml", false);
        }

        FileConfiguration promptConfig = YamlConfiguration.loadConfiguration(promptFile);
        systemPrompt = resolveSystemPrompt(promptFileExists, promptConfig.getString("system-prompt"), fallbackPrompt);

        File file = new File(plugin.getDataFolder(), "AIHELPER.yml");
        if (!file.exists()) {
            plugin.saveResource("AIHELPER.yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        rules.clear();
        ConfigurationSection section = config.getConfigurationSection("rules");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String description = section.getString(key + ".description");
                if (description == null || description.isEmpty()) {
                    plugin.getLogs().warning("ai-rule-description", Map.of("rule", key));
                    continue;
                }
                rules.put(key, new RuleInfo(key, description, section.getString(key + ".punish-command")));
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
        Path path = new File(plugin.getDataFolder(), fileName).toPath();
        try {
            Files.createDirectories(path.getParent());
            if (Files.notExists(path)) {
                Files.createFile(path);
                return List.of();
            }
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogs().warning("ai-training-read", Map.of(
                    "file", fileName,
                    "error", String.valueOf(e.getMessage())));
            return List.of();
        }
    }

    public synchronized boolean addTrainingMessage(boolean plus, String message) {
        String cleaned = message.trim();
        if (cleaned.isEmpty()) {
            return false;
        }

        List<String> current = plus ? trainingPlus : trainingMinus;
        for (String line : current) {
            if (line.trim().equalsIgnoreCase(cleaned)) {
                return false;
            }
        }

        String fileName = plus ? "learning/trainingplus.txt" : "learning/trainingminus.txt";
        Path path = new File(plugin.getDataFolder(), fileName).toPath();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, cleaned + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            plugin.getLogs().warning("ai-training-write", Map.of(
                    "file", fileName,
                    "error", String.valueOf(e.getMessage())));
            return false;
        }

        loadTrainingData();
        return true;
    }

    public synchronized Map<String, RuleInfo> getRules() {
        return Map.copyOf(rules);
    }

    public synchronized List<String> getTrainingPlus() {
        return List.copyOf(trainingPlus);
    }

    public synchronized List<String> getTrainingMinus() {
        return List.copyOf(trainingMinus);
    }

    public synchronized String getSystemPrompt() {
        return systemPrompt;
    }

    static String resolveSystemPrompt(boolean promptFileExists, String configuredPrompt, String fallbackPrompt) {
        String loadedPrompt = configuredPrompt == null || configuredPrompt.isBlank() ? null : configuredPrompt;
        if (promptFileExists && loadedPrompt != null) {
            return loadedPrompt;
        }
        if (fallbackPrompt != null && !fallbackPrompt.isBlank()) {
            return fallbackPrompt;
        }
        return loadedPrompt;
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
