package ru.javaroot.javachats.api;

import java.util.Objects;
import java.util.UUID;

public record ChatRequest(UUID senderId, String senderName, ChatChannel channel, String message) {
    public ChatRequest {
        Objects.requireNonNull(senderId, "senderId");
        Objects.requireNonNull(senderName, "senderName");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(message, "message");
        if (senderName.isBlank()) {
            throw new IllegalArgumentException("senderName must not be blank");
        }
        if (message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        senderName = senderName.trim();
        message = message.trim();
    }
}
