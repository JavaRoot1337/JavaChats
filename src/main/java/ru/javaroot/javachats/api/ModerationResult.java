package ru.javaroot.javachats.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ModerationResult(boolean violation, double probability, String rule, List<String> badWords,
        String censoredText) {
    public ModerationResult {
        if (Double.isNaN(probability) || probability < 0.0 || probability > 1.0) {
            throw new IllegalArgumentException("probability must be between 0 and 1");
        }
        if (!violation && censoredText != null) {
            throw new IllegalArgumentException("censoredText requires a violation");
        }
        badWords = List.copyOf(Objects.requireNonNull(badWords, "badWords"));
    }

    public Optional<String> censoredMessage() {
        return Optional.ofNullable(censoredText);
    }

    public static ModerationResult clean() {
        return new ModerationResult(false, 0.0, null, List.of(), null);
    }
}
