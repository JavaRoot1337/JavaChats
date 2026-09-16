package ru.javaroot.javachats.api;

import java.util.Objects;

public final class ChatDecision {
    public enum Action { ALLOW, BLOCK, REPLACE }

    private final Action action;
    private final String message;
    private final String reason;

    public ChatDecision(Action action, String message, String reason) {
        this.action = Objects.requireNonNull(action, "action");
        if (action == Action.REPLACE) {
            this.message = requireText(message);
        } else {
            if (message != null) {
                throw new IllegalArgumentException("message is only valid for REPLACE");
            }
            this.message = null;
        }
        this.reason = reason;
    }

    public Action action() { return action; }
    public String message() { return message; }
    public String reason() { return reason; }

    public static ChatDecision allow() { return new ChatDecision(Action.ALLOW, null, null); }
    public static ChatDecision block(String reason) { return new ChatDecision(Action.BLOCK, null, reason); }
    public static ChatDecision replace(String message, String reason) {
        return new ChatDecision(Action.REPLACE, message, reason);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ChatDecision)) return false;
        ChatDecision that = (ChatDecision) other;
        return action == that.action && Objects.equals(message, that.message)
                && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(action, message, reason);
    }

    private static String requireText(String value) {
        String cleaned = Objects.requireNonNull(value, "message").trim();
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("replacement message must not be blank");
        }
        return cleaned;
    }
}
