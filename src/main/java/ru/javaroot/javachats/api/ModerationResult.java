package ru.javaroot.javachats.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ModerationResult {
    private final boolean violation;
    private final double probability;
    private final String rule;
    private final List<String> badWords;
    private final String censoredText;
    private final boolean available;
    private final boolean blocking;

    public ModerationResult(boolean violation, double probability, String rule, List<String> badWords,
            String censoredText) {
        this(violation, probability, rule, badWords, censoredText, true);
    }

    public ModerationResult(boolean violation, double probability, String rule, List<String> badWords,
            String censoredText, boolean available) {
        this(violation, probability, rule, badWords, censoredText, available, false);
    }

    public ModerationResult(boolean violation, double probability, String rule, List<String> badWords,
            String censoredText, boolean available, boolean blocking) {
        if (Double.isNaN(probability) || probability < 0.0 || probability > 1.0) {
            throw new IllegalArgumentException("probability must be between 0 and 1");
        }
        if (!violation && censoredText != null) {
            throw new IllegalArgumentException("censoredText requires a violation");
        }
        this.violation = violation;
        this.probability = probability;
        this.rule = rule;
        this.badWords = Collections.unmodifiableList(new ArrayList<String>(
                Objects.requireNonNull(badWords, "badWords")));
        this.censoredText = censoredText;
        this.available = available;
        this.blocking = blocking;
    }

    public boolean violation() { return violation; }
    public double probability() { return probability; }
    public String rule() { return rule; }
    public List<String> badWords() { return badWords; }
    public String censoredText() { return censoredText; }
    public Optional<String> censoredMessage() { return Optional.ofNullable(censoredText); }
    public boolean available() { return available; }
    public boolean blocking() { return blocking; }

    public static ModerationResult clean() {
        return new ModerationResult(false, 0.0, null, Collections.<String>emptyList(), null);
    }

    public static ModerationResult unavailable() {
        return new ModerationResult(false, 0.0, null, Collections.<String>emptyList(), null, false);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ModerationResult)) return false;
        ModerationResult that = (ModerationResult) other;
        return violation == that.violation && Double.compare(probability, that.probability) == 0
                && Objects.equals(rule, that.rule) && badWords.equals(that.badWords)
                && Objects.equals(censoredText, that.censoredText) && available == that.available
                && blocking == that.blocking;
    }

    @Override
    public int hashCode() {
        return Objects.hash(violation, probability, rule, badWords, censoredText, available, blocking);
    }
}
