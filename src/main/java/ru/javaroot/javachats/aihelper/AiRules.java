package ru.javaroot.javachats.aihelper;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.javaroot.JavaChat;
import ru.javaroot.javachats.utils.LogVars;

import java.io.File;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class AiRules {
    private static final int MAX_TRAINING_LINES = 1000;
    private static final int MAX_TRAINING_LINE_LENGTH = 4096;
    private static final long MAX_TRAINING_FILE_BYTES = 1024L * 1024L;
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
        String configuredPrompt = promptConfig.getString("system-prompt");
        if (configuredPrompt == null) {
            configuredPrompt = readPlainPrompt(promptFile);
        }
        systemPrompt = resolveSystemPrompt(promptFileExists, configuredPrompt, fallbackPrompt);

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
                    plugin.getLogs().warning("ai-rule-description", LogVars.of("rule", key));
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

    private String readPlainPrompt(File file) {
        try {
            if (Files.size(file.toPath()) > MAX_TRAINING_FILE_BYTES) {
                throw new IOException("prompt file is too large");
            }
            byte[] bytes = Files.readAllBytes(file.toPath());
            String prompt = new String(bytes, StandardCharsets.UTF_8).trim();
            return prompt.isEmpty() ? null : prompt;
        } catch (IOException e) {
            plugin.getLogs().warning("ai-training-read", LogVars.of(
                    "file", file.getName(),
                    "error", String.valueOf(e.getMessage())));
            return null;
        }
    }

    private List<String> loadTrainingFile(String fileName) {
        Path path = new File(plugin.getDataFolder(), fileName).toPath();
        try {
            Files.createDirectories(path.getParent());
            if (Files.notExists(path)) {
                Files.createFile(path);
                return java.util.Collections.emptyList();
            }
            if (Files.size(path) > MAX_TRAINING_FILE_BYTES) {
                throw new IOException("training file is too large");
            }
            List<String> result = new ArrayList<String>();
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.length() > MAX_TRAINING_LINE_LENGTH) {
                        throw new IOException("training line is too long");
                    }
                    if (result.size() >= MAX_TRAINING_LINES) {
                        throw new IOException("too many training lines");
                    }
                    result.add(line);
                }
            }
            return result;
        } catch (IOException e) {
            plugin.getLogs().warning("ai-training-read", LogVars.of(
                    "file", fileName,
                    "error", String.valueOf(e.getMessage())));
            return java.util.Collections.emptyList();
        }
    }

    public CompletableFuture<Boolean> addTrainingMessageAsync(boolean plus, String message) {
        CompletableFuture<Boolean> result = new CompletableFuture<Boolean>();
        if (plugin.getScheduler().runAsync(() -> result.complete(addTrainingMessage(plus, message))) == null) {
            result.complete(false);
        }
        return result;
    }

    private synchronized boolean addTrainingMessage(boolean plus, String message) {
        if (message == null || message.length() > MAX_TRAINING_LINE_LENGTH) {
            return false;
        }
        String cleaned = message.trim();
        if (cleaned.isEmpty()) {
            return false;
        }

        List<String> current = plus ? trainingPlus : trainingMinus;
        if (current.size() >= MAX_TRAINING_LINES) {
            return false;
        }
        for (String line : current) {
            if (line.trim().equalsIgnoreCase(cleaned)) {
                return false;
            }
        }

        String fileName = plus ? "learning/trainingplus.txt" : "learning/trainingminus.txt";
        Path path = new File(plugin.getDataFolder(), fileName).toPath();
        try {
            Files.createDirectories(path.getParent());
            byte[] line = (cleaned + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
            if (Files.exists(path) && Files.size(path) + line.length > MAX_TRAINING_FILE_BYTES) {
                return false;
            }
            Files.write(path, line,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            plugin.getLogs().warning("ai-training-write", LogVars.of(
                    "file", fileName,
                    "error", String.valueOf(e.getMessage())));
            return false;
        }

        loadTrainingData();
        return true;
    }

    public synchronized Map<String, RuleInfo> getRules() {
        return java.util.Collections.unmodifiableMap(new HashMap<String, RuleInfo>(rules));
    }

    public synchronized List<String> getTrainingPlus() {
        return java.util.Collections.unmodifiableList(new ArrayList<String>(trainingPlus));
    }

    public synchronized List<String> getTrainingMinus() {
        return java.util.Collections.unmodifiableList(new ArrayList<String>(trainingMinus));
    }

    public synchronized String getSystemPrompt() {
        return systemPrompt;
    }

    static String resolveSystemPrompt(boolean promptFileExists, String configuredPrompt, String fallbackPrompt) {
        String loadedPrompt = configuredPrompt == null || configuredPrompt.trim().isEmpty() ? null : configuredPrompt;
        if (promptFileExists && loadedPrompt != null) {
            return loadedPrompt;
        }
        if (fallbackPrompt != null && !fallbackPrompt.trim().isEmpty()) {
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
