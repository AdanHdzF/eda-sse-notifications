package com.sse.kafka;

import com.sse.model.ClientEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class EventBatcher {
    private static final Logger log = LoggerFactory.getLogger(EventBatcher.class);
    private final Consumer<List<ClientEvent>> onFlush;
    private final int maxBatchSize;
    private final List<ClientEvent> buffer = new ArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Object lock = new Object();

    public EventBatcher(Consumer<List<ClientEvent>> onFlush, int maxBatchSize, long flushIntervalMs) {
        this.onFlush = onFlush;
        this.maxBatchSize = maxBatchSize;
        scheduler.scheduleAtFixedRate(this::timedFlush, flushIntervalMs, flushIntervalMs, TimeUnit.MILLISECONDS);
    }

    public void add(ClientEvent event) {
        List<ClientEvent> toFlush = null;
        synchronized (lock) {
            buffer.add(event);
            if (buffer.size() >= maxBatchSize) {
                toFlush = new ArrayList<>(buffer);
                buffer.clear();
            }
        }
        if (toFlush != null) {
            flush(toFlush);
        }
    }

    private void timedFlush() {
        List<ClientEvent> toFlush;
        synchronized (lock) {
            if (buffer.isEmpty()) return;
            toFlush = new ArrayList<>(buffer);
            buffer.clear();
        }
        flush(toFlush);
    }

    private void flush(List<ClientEvent> batch) {
        try {
            onFlush.accept(batch);
        } catch (Exception e) {
            log.error("Failed to flush batch of {} events", batch.size(), e);
        }
    }

    public void shutdown() {
        scheduler.shutdown();
        timedFlush();
    }
}
