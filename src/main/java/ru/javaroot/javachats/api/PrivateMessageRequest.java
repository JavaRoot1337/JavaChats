package ru.javaroot.javachats.api;

import java.util.Objects;
import java.util.UUID;

public final class PrivateMessageRequest {
    private final UUID senderId;
    private final String senderName;
    private final UUID recipientId;
    private final String recipientName;
    private final String message;

    public PrivateMessageRequest(UUID senderId, String senderName, UUID recipientId, String recipientName,
            String message) {
        this.senderId = Objects.requireNonNull(senderId, "senderId");
        this.senderName = clean(senderName, "senderName");
        this.recipientId = Objects.requireNonNull(recipientId, "recipientId");
        this.recipientName = clean(recipientName, "recipientName");
        this.message = clean(message, "message");
    }

    public UUID senderId() { return senderId; }
    public String senderName() { return senderName; }
    public UUID recipientId() { return recipientId; }
    public String recipientName() { return recipientName; }
    public String message() { return message; }

    private static String clean(String value, String field) {
        String cleaned = Objects.requireNonNull(value, field).trim();
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return cleaned;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PrivateMessageRequest)) return false;
        PrivateMessageRequest that = (PrivateMessageRequest) other;
        return senderId.equals(that.senderId) && senderName.equals(that.senderName)
                && recipientId.equals(that.recipientId) && recipientName.equals(that.recipientName)
                && message.equals(that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(senderId, senderName, recipientId, recipientName, message);
    }
}
