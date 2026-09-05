package ru.javaroot.javachats.api;

import java.util.Objects;
import java.util.Optional;

public record ChatResult(Status status, ChatRequest request, String message, String reason) {
    public enum Status {
        PUBLISHED,
        BLOCKED,
        UNAVAILABLE,
        INVALID
    }

    public ChatResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(request, "request");
        if (status == Status.PUBLISHED && (message == null || message.isBlank())) {
            throw new IllegalArgumentException("published result requires a message");
        }
    }

    public Optional<String> publishedMessage() {
        return Optional.ofNullable(message);
    }

    public static ChatResult published(ChatRequest request, String message) {
        return new ChatResult(Status.PUBLISHED, request, message, null);
    }

    public static ChatResult blocked(ChatRequest request, String reason) {
        return new ChatResult(Status.BLOCKED, request, null, reason);
    }

    public static ChatResult unavailable(ChatRequest request, String reason) {
        return new ChatResult(Status.UNAVAILABLE, request, null, reason);
    }

    public static ChatResult invalid(ChatRequest request, String reason) {
        return new ChatResult(Status.INVALID, request, null, reason);
    }
}
