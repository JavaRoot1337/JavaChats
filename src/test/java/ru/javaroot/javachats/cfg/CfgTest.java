package ru.javaroot.javachats.cfg;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class CfgTest {
    @Test
    void loadsBundledRussianConfigAndReadsAntiCapsTimings() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        try (var stream = CfgTest.class.getResourceAsStream("/locate/ru/config.yml")) {
            yaml.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        Cfg cfg = Cfg.from(yaml);
        assertDoesNotThrow(cfg::validate);
        assertEquals(500L, cfg.antiCaps().fadeInMs());
        assertEquals(3500L, cfg.antiCaps().stayMs());
        assertEquals(1000L, cfg.antiCaps().fadeOutMs());
    }

    @Test
    void rejectsNonHttpsProviderEndpoint() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        try (var stream = CfgTest.class.getResourceAsStream("/locate/ru/config.yml")) {
            yaml.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        yaml.set("ai-helper.mistral-api.endpoint", "http://example.com/v1/chat/completions");
        Cfg cfg = Cfg.from(yaml);
        assertThrows(IllegalArgumentException.class, cfg::validate);
    }
}
