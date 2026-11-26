package ru.javaroot.javachats.chat;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import ru.javaroot.javachats.cfg.Cfg;
import ru.javaroot.javachats.cfg.Msg;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ChatEngTest {
    @Test
    void detectsCapsOnlyAboveThresholdAndMinLength() {
        Cfg.AntiCaps caps = new Cfg.AntiCaps(75, 4, 100, 1000, 100);
        assertFalse(ChatEng.isCaps("AAA", caps));
        assertFalse(ChatEng.isCaps("123456", caps));
        assertFalse(ChatEng.isCaps("Привет МИР", caps));
        assertTrue(ChatEng.isCaps("ПРИВЕТ всем", new Cfg.AntiCaps(50, 4, 100, 1000, 100)));
    }

    @Test
    void validatesBundledMessagesForBothLocales() throws Exception {
        for (String loc : new String[]{"ru", "en"}) {
            YamlConfiguration yaml = new YamlConfiguration();
            try (var stream = ChatEngTest.class.getResourceAsStream("/locate/" + loc + "/message.yml")) {
                yaml.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            }
            Msg msg = Msg.from(yaml);
            assertDoesNotThrow(msg::validate);
        }
    }
}
