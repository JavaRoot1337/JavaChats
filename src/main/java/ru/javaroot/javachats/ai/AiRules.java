package ru.javaroot.javachats.ai;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import ru.javaroot.javachats.cfg.LogCfg;
import ru.javaroot.javachats.plat.Exec;

import java.io.BufferedReader;
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
import java.util.concurrent.CompletableFuture;

public final class AiRules {
    private static final int MAX_LINES = 1000;
    private static final int MAX_LINE_LEN = 4096;
    private static final long MAX_FILE_BYTES = 1024L * 1024L;

    private final Plugin plug;
    private final LogCfg logs;
    private final Exec exec;
    private final File cfgDir;
    private final Map<String, RuleInfo> rules = new HashMap<>();
    private final List<String> plus = new ArrayList<>();
    private final List<String> minus = new ArrayList<>();
    private String systemPrompt;

    public AiRules(Plugin plug, LogCfg logs, Exec exec, File cfgDir) {
        this.plug = plug;
        this.logs = logs;
        this.exec = exec;
        this.cfgDir = cfgDir;
    }

    public synchronized void load(String fallbackPrompt) {
        File promptFile = new File(cfgDir, "AIRULES.yml");
        if (!promptFile.exists()) {
            throw new IllegalStateException("Отсутствует AIRULES.yml в профиле локали");
        }
        FileConfiguration promptYaml = YamlConfiguration.loadConfiguration(promptFile);
        String configured = promptYaml.getString("system-prompt");
        if (configured == null) {
            configured = readPlain(promptFile);
        }
        systemPrompt = resolveSystemPrompt(true, configured, fallbackPrompt);

        File rulesFile = new File(cfgDir, "AIHELPER.yml");
        if (!rulesFile.exists()) {
            throw new IllegalStateException("Отсутствует AIHELPER.yml в профиле локали");
        }
        FileConfiguration cfg = loadYaml(rulesFile);
        rules.clear();
        ConfigurationSection sec = cfg.getConfigurationSection("rules");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                String desc = sec.getString(key + ".description");
                if (desc == null || desc.isEmpty()) {
                    logs.warning("ai-rule-description", Map.of("rule", key));
                    continue;
                }
                rules.put(key, new RuleInfo(key, desc, sec.getString(key + ".punish-command")));
            }
        }
        loadTraining();
    }

    public CompletableFuture<Boolean> addTrainingAsync(boolean isPlus, String message) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        if (exec.runAsync(() -> future.complete(addTraining(isPlus, message))).isCancelled()) {
            future.complete(false);
        }
        return future;
    }

    public synchronized Map<String, RuleInfo> getRules() {
        return Map.copyOf(rules);
    }

    public synchronized List<String> getPlus() {
        return List.copyOf(plus);
    }

    public synchronized List<String> getMinus() {
        return List.copyOf(minus);
    }

    public synchronized String getSystemPrompt() {
        return systemPrompt;
    }

    public static String resolveSystemPrompt(boolean fileExists, String configured, String fallback) {
        String loaded = configured == null || configured.trim().isEmpty() ? null : configured;
        if (fileExists && loaded != null) {
            return loaded;
        }
        if (fallback != null && !fallback.trim().isEmpty()) {
            return fallback;
        }
        return loaded;
    }

    private void loadTraining() {
        plus.clear();
        minus.clear();
        plus.addAll(readTraining("learning/trainingplus.txt"));
        minus.addAll(readTraining("learning/trainingminus.txt"));
    }

    private synchronized boolean addTraining(boolean isPlus, String message) {
        if (message == null || message.length() > MAX_LINE_LEN) {
            return false;
        }
        String cleaned = message.trim();
        if (cleaned.isEmpty()) {
            return false;
        }
        List<String> current = isPlus ? plus : minus;
        if (current.size() >= MAX_LINES) {
            return false;
        }
        for (String line : current) {
            if (line.trim().equalsIgnoreCase(cleaned)) {
                return false;
            }
        }
        String fileName = isPlus ? "learning/trainingplus.txt" : "learning/trainingminus.txt";
        Path path = new File(plug.getDataFolder(), fileName).toPath();
        try {
            Files.createDirectories(path.getParent());
            byte[] bytes = (cleaned + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
            if (Files.exists(path) && Files.size(path) + bytes.length > MAX_FILE_BYTES) {
                return false;
            }
            Files.write(path, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            logs.warning("ai-training-write", Map.of("file", fileName, "error", String.valueOf(ex.getMessage())));
            return false;
        }
        loadTraining();
        return true;
    }

    private List<String> readTraining(String fileName) {
        Path path = new File(plug.getDataFolder(), fileName).toPath();
        try {
            Files.createDirectories(path.getParent());
            if (Files.notExists(path)) {
                Files.createFile(path);
                return List.of();
            }
            if (Files.size(path) > MAX_FILE_BYTES) {
                throw new IOException("файл обучения превышает лимит");
            }
            List<String> out = new ArrayList<>();
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.length() > MAX_LINE_LEN) {
                        throw new IOException("строка обучения слишком длинная");
                    }
                    if (out.size() >= MAX_LINES) {
                        throw new IOException("превышен лимит строк обучения");
                    }
                    out.add(line);
                }
            }
            return out;
        } catch (IOException ex) {
            logs.warning("ai-training-read", Map.of("file", fileName, "error", String.valueOf(ex.getMessage())));
            return List.of();
        }
    }

    private String readPlain(File file) {
        try {
            if (Files.size(file.toPath()) > MAX_FILE_BYTES) {
                throw new IOException("файл промпта превышает лимит");
            }
            String prompt = Files.readString(file.toPath(), StandardCharsets.UTF_8).trim();
            return prompt.isEmpty() ? null : prompt;
        } catch (IOException ex) {
            logs.warning("ai-training-read", Map.of("file", file.getName(), "error", String.valueOf(ex.getMessage())));
            return null;
        }
    }

    private FileConfiguration loadYaml(File file) {
        YamlConfiguration cfg = new YamlConfiguration();
        try {
            cfg.load(file);
            return cfg;
        } catch (IOException | InvalidConfigurationException ex) {
            throw new IllegalStateException("Не удалось прочитать " + file.getName(), ex);
        }
    }

    public record RuleInfo(String id, String description, String punishCmd) {
    }
}
