package ru.javaroot.javachats.api;

import java.util.Objects;

public record ChatDecision(Action action, String message, String reason) {
    public enum Action {
        ALLOW,
        BLOCK,
        REPLACE
    }

    public ChatDecision {
        Objects.requireNonNull(action, "action");
        if (action == Action.REPLACE) {
            Objects.requireNonNull(message, "message");
            if (message.isBlank()) {
                throw new IllegalArgumentException("replacement message must not be blank");
            }
        } else if (message != null) {
            throw new IllegalArgumentException("message is only valid for REPLACE");
        }
    }

    public static ChatDecision allow() {
        return new ChatDecision(Action.ALLOW, null, null);
    }

    public static ChatDecision block(String reason) {
        return new ChatDecision(Action.BLOCK, null, reason);
    }

    public static ChatDecision replace(String message, String reason) {
        return new ChatDecision(Action.REPLACE, message, reason);
    }
}
