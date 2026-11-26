package ru.javaroot.javachats.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class AiSvcTest {
    @Test
    void masksTargetWordsCaseInsensitively() {
        assertEquals("Эй **, С***О", AiSvc.censor(AiSvc.censor("Эй ты, СлОвО", "ты"), "слово"));
        assertEquals("п * * * р иди", AiSvc.censor("п и д о р иди", "п и д о р"));
    }

    @Test
    void preservesPromptFallbackRule() {
        assertEquals("custom", AiRules.resolveSystemPrompt(true, "custom", "fallback"));
        assertEquals("fallback", AiRules.resolveSystemPrompt(false, "custom", "fallback"));
    }
}
