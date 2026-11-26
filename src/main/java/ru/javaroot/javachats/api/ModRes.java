package ru.javaroot.javachats.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Итог проверки сообщения AI-модератором.
 */
public record ModRes(
        boolean violation,
        double probability,
        String rule,
        List<String> badWords,
        String censoredText,
        boolean available,
        boolean blocking) {

    public ModRes {
        if (Double.isNaN(probability) || probability < 0.0 || probability > 1.0) {
            throw new IllegalArgumentException("probability must be between 0 and 1");
        }
        if (!violation && censoredText != null) {
            throw new IllegalArgumentException("censoredText requires a violation");
        }
        badWords = List.copyOf(Objects.requireNonNull(badWords, "badWords"));
    }

    public ModRes(boolean violation, double probability, String rule, List<String> badWords, String censoredText) {
        this(violation, probability, rule, badWords, censoredText, true, false);
    }

    public ModRes(boolean violation, double probability, String rule, List<String> badWords,
            String censoredText, boolean available) {
        this(violation, probability, rule, badWords, censoredText, available, false);
    }

    public Optional<String> censoredMessage() {
        return Optional.ofNullable(censoredText);
    }

    public static ModRes clean() {
        return new ModRes(false, 0.0, null, List.of(), null, true, false);
    }

    public static ModRes unavailable() {
        return new ModRes(false, 0.0, null, List.of(), null, false, false);
    }
}
