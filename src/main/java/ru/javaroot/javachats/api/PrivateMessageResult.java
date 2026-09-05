package ru.javaroot.javachats.api;

import java.util.Objects;

public record PrivateMessageResult(Status status, PrivateMessageRequest request, String reason) {
    public enum Status {
        SENT,
        RECIPIENT_OFFLINE,
        UNAVAILABLE
    }

    public PrivateMessageResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(request, "request");
    }
}
