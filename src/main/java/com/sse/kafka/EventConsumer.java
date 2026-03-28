package com.sse.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sse.db.EventRepository;
import com.sse.model.ClientEvent;
import com.sse.sse.ConnectionManager;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class EventConsumer implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(EventConsumer.class);
    private final KafkaConsumer<String, String> consumer;
    private final EventBatcher batcher;
    private final EventRepository repository;
    private final ConnectionManager connectionManager;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicBoolean running = new AtomicBoolean(true);

    public EventConsumer(String bootstrapServers, String topic, String groupId,
                         EventRepository repository, ConnectionManager connectionManager) {
        this.repository = repository;
        this.connectionManager = connectionManager;

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");

        this.consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of(topic));

        this.batcher = new EventBatcher(this::processBatch, 500, 100);
    }

    @Override
    public void run() {
        try {
            while (running.get()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(200));
                records.forEach(record -> {
                    try {
                        JsonNode node = mapper.readTree(record.value());
                        String clientId = node.get("clientId").asText();
                        String eventType = node.get("eventType").asText();
                        String payload = node.get("payload").toString();
                        batcher.add(new ClientEvent(clientId, eventType, payload));
                    } catch (Exception e) {
                        log.error("Failed to parse Kafka message: {}", record.value(), e);
                    }
                });
            }
        } finally {
            batcher.shutdown();
            consumer.close();
        }
    }

    private void processBatch(List<ClientEvent> batch) {
        try {
            List<Long> ids = repository.insertBatch(batch);
            for (int i = 0; i < batch.size(); i++) {
                ClientEvent event = batch.get(i);
                long id = ids.get(i);
                pushToClient(event, id);
            }
        } catch (Exception e) {
            log.error("Failed to process batch of {} events", batch.size(), e);
        }
    }

    private void pushToClient(ClientEvent event, long id) {
        Sse sse = connectionManager.getSse();
        if (sse == null) return;
        SseEventSink sink = connectionManager.get(event.clientId());
        if (sink == null || sink.isClosed()) return;
        try {
            OutboundSseEvent sseEvent = sse.newEventBuilder()
                    .id(String.valueOf(id))
                    .name(event.eventType())
                    .data(event.payload())
                    .build();
            sink.send(sseEvent).exceptionally(t -> {
                log.warn("Failed to push event to client {}, removing", event.clientId());
                connectionManager.remove(event.clientId());
                return null;
            });
        } catch (Exception e) {
            log.warn("Error pushing to client {}", event.clientId(), e);
            connectionManager.remove(event.clientId());
        }
    }

    public void stop() {
        running.set(false);
    }
}
