package ru.javaroot.javachats.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Запрос на отправку личного сообщения.
 */
public record PmReq(UUID senderId, String senderName, UUID recipientId, String recipientName, String message) {
    private static final int MAX_LEN = 4096;

    public PmReq {
        Objects.requireNonNull(senderId, "senderId");
        senderName = clean(senderName, "senderName");
        Objects.requireNonNull(recipientId, "recipientId");
        recipientName = clean(recipientName, "recipientName");
        message = clean(message, "message");
        if (message.length() > MAX_LEN) {
            throw new IllegalArgumentException("message is too long");
        }
    }

    private static String clean(String value, String field) {
        String trimmed = Objects.requireNonNull(value, field).trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return trimmed;
    }
}
