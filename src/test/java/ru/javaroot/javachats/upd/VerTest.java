package ru.javaroot.javachats.upd;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VerTest {
    @Test
    void comparesModernYearlyVersionsAboveLegacyMinor() {
        Ver modern = Ver.parse("26.1");
        Ver legacy = Ver.parse("1.21.11");
        assertNotNull(modern);
        assertNotNull(legacy);
        assertTrue(modern.compareTo(legacy) > 0);
        assertTrue(Ver.isAtLeast("26.3", 1, 21));
        assertTrue(Ver.isAtLeast("1.21.11-R0.1-SNAPSHOT", 1, 21));
        assertFalse(Ver.isAtLeast("1.20.6", 1, 21));
    }

    @Test
    void parsesPrefixedAndRejectsBrokenStrings() {
        Ver v = Ver.parse("v2.1.0");
        assertNotNull(v);
        assertEquals("2.1.0", v.toString());
        assertNull(Ver.parse(""));
        assertNull(Ver.parse("02.1"));
        assertNull(Ver.parse("abc"));
    }
}
