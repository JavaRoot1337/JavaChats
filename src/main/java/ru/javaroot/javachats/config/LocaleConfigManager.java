package ru.javaroot.javachats.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.InvalidConfigurationException;
import ru.javaroot.JavaChat;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class LocaleConfigManager {
    private static final String DEFAULT_LOCALE = "ru";
    private static final List<String> LOCALES = Collections.unmodifiableList(Arrays.asList("ru", "en"));
    private static final List<String> CONFIG_FILES = Collections.unmodifiableList(Arrays.asList(
            "config.yml", "message.yml", "AIRULES.yml", "AIHELPER.yml"));

    private final JavaChat plugin;

    public LocaleConfigManager(JavaChat plugin) {
        this.plugin = plugin;
    }

    public ActiveConfig load() {
        File rootFile = new File(plugin.getDataFolder(), "config.yml");
        FileConfiguration rootConfig = loadFileConfig(rootFile);
        String locale = resolveLocale(rootConfig.getString("locale"));
        migrateAndSeed(rootFile, rootConfig, locale);

        File profileDirectory = new File(new File(plugin.getDataFolder(), "temp"), locale);
        FileConfiguration config = loadFileConfig(new File(profileDirectory, "config.yml"));
        FileConfiguration message = loadFileConfig(new File(profileDirectory, "message.yml"));
        materializeRootFiles(profileDirectory, locale);
        return new ActiveConfig(locale, profileDirectory, config, message);
    }

    public FileConfiguration bundledConfig(String locale) {
        return loadResourceConfig(locale, "config.yml");
    }

    private String resolveLocale(String value) {
        if (value == null || value.trim().isEmpty()) {
            return DEFAULT_LOCALE;
        }
        String locale = value.trim().toLowerCase(Locale.ROOT);
        if (!LOCALES.contains(locale)) {
            throw new IllegalArgumentException("unsupported locale: " + value);
        }
        return locale;
    }

    private void migrateAndSeed(File rootFile, FileConfiguration rootConfig, String locale) {
        boolean legacyInstall = !hasProfileFiles();
        boolean legacyConfig = legacyInstall
                && (rootConfig.contains("chats") || rootConfig.contains("ping-chat"));
        for (String profileLocale : LOCALES) {
            File profileDirectory = new File(new File(plugin.getDataFolder(), "temp"), profileLocale);
            try {
                Files.createDirectories(profileDirectory.toPath());
                for (String fileName : CONFIG_FILES) {
                    Path target = new File(profileDirectory, fileName).toPath();
                    if (Files.exists(target)) {
                        continue;
                    }

                    File legacyFile = new File(plugin.getDataFolder(), fileName);
                    if (legacyInstall && profileLocale.equals(locale)
                            && ((!fileName.equals("config.yml") && legacyFile.isFile())
                            || (fileName.equals("config.yml") && legacyConfig))) {
                        Files.copy(legacyFile.toPath(), target);
                    } else {
                        copyResource(profileLocale, fileName, target);
                    }
                }
            } catch (IOException error) {
                throw new IllegalStateException("could not prepare locale profile " + profileLocale, error);
            }
        }

        if (!locale.equals(rootConfig.getString("locale"))) {
            rootConfig.set("locale", locale);
            try {
                rootConfig.save(rootFile);
            } catch (IOException error) {
                throw new IllegalStateException("could not save locale selection", error);
            }
        }
    }

    private boolean hasProfileFiles() {
        File tempDirectory = new File(plugin.getDataFolder(), "temp");
        for (String locale : LOCALES) {
            for (String fileName : CONFIG_FILES) {
                if (new File(new File(tempDirectory, locale), fileName).isFile()) {
                    return true;
                }
            }
        }
        return false;
    }

    private void materializeRootFiles(File profileDirectory, String locale) {
        File dataFolder = plugin.getDataFolder();
        try {
            for (String fileName : CONFIG_FILES) {
                Path source = new File(profileDirectory, fileName).toPath();
                Path target = new File(dataFolder, fileName).toPath();
                if (fileName.equals("config.yml")) {
                    String content = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
                    String rootContent = "locale: " + locale + "\n" + content;
                    Files.write(target, rootContent.getBytes(StandardCharsets.UTF_8));
                } else {
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException error) {
            throw new IllegalStateException("could not materialize locale profile " + locale, error);
        }
    }

    private void copyResource(String locale, String fileName, Path target) throws IOException {
        String resourcePath = "locate/" + locale + "/" + fileName;
        try (InputStream stream = plugin.getResource(resourcePath)) {
            if (stream == null) {
                throw new IOException("missing bundled resource " + resourcePath);
            }
            Files.copy(stream, target);
        }
    }

    private FileConfiguration loadResourceConfig(String locale, String fileName) {
        String resourcePath = "locate/" + locale + "/" + fileName;
        try (InputStream stream = plugin.getResource(resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("missing bundled resource " + resourcePath);
            }
            return YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException error) {
            throw new IllegalStateException("could not load bundled resource " + resourcePath, error);
        }
    }

    private FileConfiguration loadFileConfig(File file) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
            return config;
        } catch (IOException | InvalidConfigurationException error) {
            throw new IllegalStateException("could not load configuration " + file.getName(), error);
        }
    }

    public static final class ActiveConfig {
        private final String locale;
        private final File directory;
        private final FileConfiguration config;
        private final FileConfiguration message;

        private ActiveConfig(String locale, File directory, FileConfiguration config, FileConfiguration message) {
            this.locale = locale;
            this.directory = directory;
            this.config = config;
            this.message = message;
        }

        public String locale() {
            return locale;
        }

        public File directory() {
            return directory;
        }

        public FileConfiguration config() {
            return config;
        }

        public FileConfiguration message() {
            return message;
        }
    }
}
