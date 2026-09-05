package ru.javaroot.javachats.api;

import java.util.Objects;
import java.util.UUID;

public record PrivateMessageRequest(UUID senderId, String senderName, UUID recipientId, String recipientName,
        String message) {
    public PrivateMessageRequest {
        Objects.requireNonNull(senderId, "senderId");
        Objects.requireNonNull(senderName, "senderName");
        Objects.requireNonNull(recipientId, "recipientId");
        Objects.requireNonNull(recipientName, "recipientName");
        Objects.requireNonNull(message, "message");
        if (senderName.isBlank() || recipientName.isBlank() || message.isBlank()) {
            throw new IllegalArgumentException("private message fields must not be blank");
        }
        senderName = senderName.trim();
        recipientName = recipientName.trim();
        message = message.trim();
    }
}
