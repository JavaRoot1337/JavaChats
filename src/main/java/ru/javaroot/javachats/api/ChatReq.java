package ru.javaroot.javachats.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Неизменяемый запрос на отправку сообщения в чат.
 */
public record ChatReq(UUID senderId, String senderName, ChatChan channel, String message) {
    private static final int MAX_LEN = 4096;

    public ChatReq {
        Objects.requireNonNull(senderId, "senderId");
        senderName = clean(senderName, "senderName");
        Objects.requireNonNull(channel, "channel");
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
