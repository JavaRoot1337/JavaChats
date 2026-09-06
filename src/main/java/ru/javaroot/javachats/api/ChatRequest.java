package ru.javaroot.javachats.api;

import java.util.Objects;
import java.util.UUID;

public final class ChatRequest {
    private final UUID senderId;
    private final String senderName;
    private final ChatChannel channel;
    private final String message;

    public ChatRequest(UUID senderId, String senderName, ChatChannel channel, String message) {
        this.senderId = Objects.requireNonNull(senderId, "senderId");
        this.senderName = cleanText(senderName, "senderName");
        this.channel = Objects.requireNonNull(channel, "channel");
        this.message = cleanText(message, "message");
    }

    public UUID senderId() { return senderId; }
    public String senderName() { return senderName; }
    public ChatChannel channel() { return channel; }
    public String message() { return message; }

    private static String cleanText(String value, String field) {
        String cleaned = Objects.requireNonNull(value, field).trim();
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return cleaned;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ChatRequest)) return false;
        ChatRequest that = (ChatRequest) other;
        return senderId.equals(that.senderId) && senderName.equals(that.senderName)
                && channel == that.channel && message.equals(that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(senderId, senderName, channel, message);
    }

    @Override
    public String toString() {
        return "ChatRequest{" + senderId + ", " + senderName + ", " + channel + ", " + message + "}";
    }
}
