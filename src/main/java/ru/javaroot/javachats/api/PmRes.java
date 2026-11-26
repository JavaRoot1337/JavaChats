package ru.javaroot.javachats.api;

import java.util.Objects;

/**
 * Результат отправки личного сообщения.
 */
public record PmRes(Status status, PmReq request, String reason) {
    public enum Status { SENT, RECIPIENT_OFFLINE, UNAVAILABLE }

    public PmRes {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(request, "request");
    }
}
