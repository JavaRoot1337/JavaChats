package ru.javaroot.javachats.api;

import java.util.Objects;
import java.util.Optional;

public final class ChatResult {
    public enum Status { PUBLISHED, BLOCKED, UNAVAILABLE, INVALID }

    private final Status status;
    private final ChatRequest request;
    private final String message;
    private final String reason;

    public ChatResult(Status status, ChatRequest request, String message, String reason) {
        this.status = Objects.requireNonNull(status, "status");
        this.request = Objects.requireNonNull(request, "request");
        if (status == Status.PUBLISHED && (message == null || message.trim().isEmpty())) {
            throw new IllegalArgumentException("published result requires a message");
        }
        this.message = message;
        this.reason = reason;
    }

    public Status status() { return status; }
    public ChatRequest request() { return request; }
    public String message() { return message; }
    public String reason() { return reason; }
    public Optional<String> publishedMessage() { return Optional.ofNullable(message); }

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

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ChatResult)) return false;
        ChatResult that = (ChatResult) other;
        return status == that.status && request.equals(that.request)
                && Objects.equals(message, that.message) && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, request, message, reason);
    }
}
