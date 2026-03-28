package com.sse.model;

import java.time.Instant;

public record ClientEvent(
    long id,
    String clientId,
    String eventType,
    String payload,
    Instant createdAt
) {
    public ClientEvent(String clientId, String eventType, String payload) {
        this(0, clientId, eventType, payload, Instant.now());
    }
}
