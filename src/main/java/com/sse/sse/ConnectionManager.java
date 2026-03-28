package com.sse.sse;

import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Map;

public class ConnectionManager {
    private static final Logger log = LoggerFactory.getLogger(ConnectionManager.class);
    private final ConcurrentHashMap<String, SseEventSink> connections = new ConcurrentHashMap<>();
    private final AtomicReference<Sse> sseRef = new AtomicReference<>();

    public void setSse(Sse sse) {
        sseRef.compareAndSet(null, sse);
    }

    public Sse getSse() {
        return sseRef.get();
    }

    public void register(String clientId, SseEventSink sink) {
        SseEventSink old = connections.put(clientId, sink);
        if (old != null && !old.isClosed()) {
            old.close();
        }
        log.info("Client connected: {} (total: {})", clientId, connections.size());
    }

    public void remove(String clientId) {
        connections.remove(clientId);
        log.info("Client disconnected: {} (total: {})", clientId, connections.size());
    }

    public SseEventSink get(String clientId) {
        return connections.get(clientId);
    }

    public boolean isConnected(String clientId) {
        SseEventSink sink = connections.get(clientId);
        if (sink != null && sink.isClosed()) {
            connections.remove(clientId);
            return false;
        }
        return sink != null;
    }

    public int getConnectionCount() {
        return connections.size();
    }

    public Map<String, SseEventSink> getAll() {
        return connections;
    }
}
