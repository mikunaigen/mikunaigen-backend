package com.mikunaigen.backend.exception;

public class EmailDispatchException extends RuntimeException {
    private final String trackingId;
    private final String stage;

    public EmailDispatchException(String trackingId, String stage, String message, Throwable cause) {
        super(message, cause);
        this.trackingId = trackingId;
        this.stage = stage;
    }

    public String trackingId() {
        return trackingId;
    }

    public String stage() {
        return stage;
    }
}
