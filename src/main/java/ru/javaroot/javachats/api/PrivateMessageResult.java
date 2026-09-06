package ru.javaroot.javachats.api;

import java.util.Objects;

public final class PrivateMessageResult {
    public enum Status { SENT, RECIPIENT_OFFLINE, UNAVAILABLE }

    private final Status status;
    private final PrivateMessageRequest request;
    private final String reason;

    public PrivateMessageResult(Status status, PrivateMessageRequest request, String reason) {
        this.status = Objects.requireNonNull(status, "status");
        this.request = Objects.requireNonNull(request, "request");
        this.reason = reason;
    }

    public Status status() { return status; }
    public PrivateMessageRequest request() { return request; }
    public String reason() { return reason; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PrivateMessageResult)) return false;
        PrivateMessageResult that = (PrivateMessageResult) other;
        return status == that.status && request.equals(that.request)
                && Objects.equals(reason, that.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, request, reason);
    }
}
