package ru.javaroot.javachats.api;

import java.util.Objects;

/**
 * Решение внешнего {@link ChatFilter} по сообщению игрока.
 */
public record ChatDec(Action action, String message, String reason) {
    public enum Action { ALLOW, BLOCK, REPLACE }

    public ChatDec {
        Objects.requireNonNull(action, "action");
        if (action == Action.REPLACE) {
            String trimmed = Objects.requireNonNull(message, "message").trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException("replacement message must not be blank");
            }
            message = trimmed;
        } else if (message != null) {
            throw new IllegalArgumentException("message is only valid for REPLACE");
        }
    }

    public static ChatDec allow() {
        return new ChatDec(Action.ALLOW, null, null);
    }

    public static ChatDec block(String reason) {
        return new ChatDec(Action.BLOCK, null, reason);
    }

    public static ChatDec replace(String message, String reason) {
        return new ChatDec(Action.REPLACE, message, reason);
    }
}
