package ru.javaroot.javachats.cfg;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;

public final class LocaleMgr {
    private static final String DEFAULT_LOCALE = "ru";
    private static final String RU_COMMENT = "# поддержка - t.me/javaroot";
    private static final String EN_COMMENT = "# support - t.me/javaroot";
    private static final List<String> LOCALES = List.of("ru", "en");
    private static final List<String> FILES = List.of("config.yml", "message.yml", "AIRULES.yml", "AIHELPER.yml");

    private final Plugin plug;

    public LocaleMgr(Plugin plug) {
        this.plug = plug;
    }

    public Active load() {
        File rootFile = new File(plug.getDataFolder(), "config.yml");
        FileConfiguration rootCfg = loadYaml(rootFile);
        String locale = resolveLocale(rootCfg.getString("locale"));
        seedProfiles(rootFile, rootCfg, locale);

        File profileDir = new File(new File(plug.getDataFolder(), "temp"), locale);
        FileConfiguration config = loadYaml(new File(profileDir, "config.yml"));
        FileConfiguration message = loadYaml(new File(profileDir, "message.yml"));
        syncRoot(profileDir, locale);
        return new Active(locale, profileDir, config, message);
    }

    public FileConfiguration bundledConfig(String locale) {
        String resource = "locate/" + locale + "/config.yml";
        try (InputStream stream = plug.getResource(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Отсутствует ресурс " + resource);
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalStateException("Не удалось прочитать ресурс " + resource, ex);
        }
    }

    private String resolveLocale(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return DEFAULT_LOCALE;
        }
        String locale = raw.trim().toLowerCase(Locale.ROOT);
        if (!LOCALES.contains(locale)) {
            throw new IllegalArgumentException("неподдерживаемая локаль: " + raw);
        }
        return locale;
    }

    private void seedProfiles(File rootFile, FileConfiguration rootCfg, String locale) {
        boolean legacyInstall = !hasProfiles();
        boolean legacyCfg = legacyInstall && (rootCfg.contains("chats") || rootCfg.contains("ping-chat"));
        for (String item : LOCALES) {
            File dir = new File(new File(plug.getDataFolder(), "temp"), item);
            try {
                Files.createDirectories(dir.toPath());
                for (String name : FILES) {
                    Path target = new File(dir, name).toPath();
                    if (Files.exists(target)) {
                        continue;
                    }
                    File legacy = new File(plug.getDataFolder(), name);
                    if (legacyInstall && item.equals(locale)
                            && ((!name.equals("config.yml") && legacy.isFile())
                            || (name.equals("config.yml") && legacyCfg))) {
                        Files.copy(legacy.toPath(), target);
                    } else {
                        copyResource(item, name, target);
                    }
                }
                ensureComment(dir, item);
            } catch (IOException ex) {
                throw new IllegalStateException("Не удалось подготовить профиль " + item, ex);
            }
        }
        if (!locale.equals(rootCfg.getString("locale"))) {
            rootCfg.set("locale", locale);
            try {
                rootCfg.save(rootFile);
            } catch (IOException ex) {
                throw new IllegalStateException("Не удалось сохранить выбор локали", ex);
            }
        }
    }

    private boolean hasProfiles() {
        File temp = new File(plug.getDataFolder(), "temp");
        for (String locale : LOCALES) {
            for (String name : FILES) {
                if (new File(new File(temp, locale), name).isFile()) {
                    return true;
                }
            }
        }
        return false;
    }

    private void syncRoot(File profileDir, String locale) {
        File dataFolder = plug.getDataFolder();
        try {
            for (String name : FILES) {
                Path src = new File(profileDir, name).toPath();
                Path dst = new File(dataFolder, name).toPath();
                if (name.equals("config.yml")) {
                    String content = Files.readString(src, StandardCharsets.UTF_8);
                    String body = content + (content.endsWith("\n") ? "" : "\n") + "locale: " + locale + "\n";
                    Files.writeString(dst, body, StandardCharsets.UTF_8);
                } else {
                    Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Не удалось обновить корневые конфиги для " + locale, ex);
        }
    }

    private void ensureComment(File profileDir, String locale) throws IOException {
        String comment = locale.equals("ru") ? RU_COMMENT : EN_COMMENT;
        for (String name : List.of("config.yml", "message.yml")) {
            Path file = new File(profileDir, name).toPath();
            String content = Files.readString(file, StandardCharsets.UTF_8);
            if (!content.contains(comment)) {
                Files.writeString(file, comment + "\n" + content, StandardCharsets.UTF_8);
            }
        }
    }

    private void copyResource(String locale, String name, Path target) throws IOException {
        String resource = "locate/" + locale + "/" + name;
        try (InputStream stream = plug.getResource(resource)) {
            if (stream == null) {
                throw new IOException("Ресурс не найден: " + resource);
            }
            Files.copy(stream, target);
        }
    }

    private FileConfiguration loadYaml(File file) {
        YamlConfiguration cfg = new YamlConfiguration();
        try {
            cfg.load(file);
            return cfg;
        } catch (IOException | InvalidConfigurationException ex) {
            throw new IllegalStateException("Не удалось загрузить " + file.getName(), ex);
        }
    }

    public record Active(String locale, File directory, FileConfiguration config, FileConfiguration message) {
    }
}
