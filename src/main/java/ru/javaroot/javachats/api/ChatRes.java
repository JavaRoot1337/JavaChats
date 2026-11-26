package ru.javaroot.javachats.api;

import java.util.Objects;
import java.util.Optional;

/**
 * Итог публикации сообщения через {@link ChatSvc}.
 */
public record ChatRes(Status status, ChatReq request, String message, String reason) {
    public enum Status { PUBLISHED, BLOCKED, UNAVAILABLE, INVALID }

    public ChatRes {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(request, "request");
        if (status == Status.PUBLISHED && (message == null || message.trim().isEmpty())) {
            throw new IllegalArgumentException("published result requires a message");
        }
    }

    public Optional<String> publishedMessage() {
        return Optional.ofNullable(message);
    }

    public static ChatRes published(ChatReq request, String message) {
        return new ChatRes(Status.PUBLISHED, request, message, null);
    }

    public static ChatRes blocked(ChatReq request, String reason) {
        return new ChatRes(Status.BLOCKED, request, null, reason);
    }

    public static ChatRes unavailable(ChatReq request, String reason) {
        return new ChatRes(Status.UNAVAILABLE, request, null, reason);
    }

    public static ChatRes invalid(ChatReq request, String reason) {
        return new ChatRes(Status.INVALID, request, null, reason);
    }
}
