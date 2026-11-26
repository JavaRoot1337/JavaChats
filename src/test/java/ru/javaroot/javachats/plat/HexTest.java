package ru.javaroot.javachats.plat;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class HexTest {
    @Test
    void parsesLegacyAndHexWithoutInjectingMessageColors() {
        Component rendered = Hex.formatTemplate("&7[&#FF8B1DГлобал&7] %message%", "&cфейк");
        assertEquals("[Глобал] &cфейк", Hex.plain(rendered));
    }

    @Test
    void extractsTrailingActiveColorsAfterReset() {
        assertEquals("&#F8AE5B&l", Hex.activeColors("&7[&r&#F8AE5B&l"));
    }
}
