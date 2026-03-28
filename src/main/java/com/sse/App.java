package com.sse;

import com.sse.auth.JwtService;
import com.sse.db.DatabaseManager;
import com.sse.db.EventRepository;
import com.sse.kafka.EventConsumer;
import com.sse.resource.HealthResource;
import com.sse.resource.SseResource;
import com.sse.sse.ConnectionManager;
import jakarta.ws.rs.sse.SseEventSink;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.internal.inject.AbstractBinder;
import org.glassfish.jersey.server.ResourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class App {
    private static final Logger log = LoggerFactory.getLogger(App.class);
    public static final String BASE_URI = "http://0.0.0.0:8080/";

    public static void main(String[] args) throws Exception {
        // Config from env
        String kafkaServers = env("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");
        String kafkaTopic = env("KAFKA_TOPIC", "client-events");
        String kafkaGroupId = env("KAFKA_GROUP_ID", "sse-server");
        String mysqlUrl = env("MYSQL_URL", "jdbc:mysql://localhost:3306/sse_events");
        String mysqlUser = env("MYSQL_USER", "sseuser");
        String mysqlPassword = env("MYSQL_PASSWORD", "ssepass");
        String jwtSecret = env("JWT_SECRET", "change-me-in-production-use-a-real-secret");

        // Init components
        DatabaseManager dbManager = new DatabaseManager(mysqlUrl, mysqlUser, mysqlPassword);
        EventRepository eventRepository = new EventRepository(dbManager.getDataSource());
        JwtService jwtService = new JwtService(jwtSecret);
        ConnectionManager connectionManager = new ConnectionManager();

        // Jersey config — bind dependencies via HK2 so @Inject works in resources
        ResourceConfig config = new ResourceConfig();
        config.register(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(jwtService).to(JwtService.class);
                bind(connectionManager).to(ConnectionManager.class);
                bind(eventRepository).to(EventRepository.class);
            }
        });
        config.register(SseResource.class);
        config.register(HealthResource.class);

        HttpServer server = GrizzlyHttpServerFactory.createHttpServer(URI.create(BASE_URI), config);

        // Kafka consumer
        EventConsumer eventConsumer = new EventConsumer(
                kafkaServers, kafkaTopic, kafkaGroupId, eventRepository, connectionManager);
        Thread consumerThread = new Thread(eventConsumer, "kafka-consumer");
        consumerThread.setDaemon(true);
        consumerThread.start();

        // Scheduled tasks
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

        // Heartbeat — check for dead connections every 15 seconds
        scheduler.scheduleAtFixedRate(() -> {
            for (Map.Entry<String, SseEventSink> entry : connectionManager.getAll().entrySet()) {
                if (entry.getValue().isClosed()) {
                    connectionManager.remove(entry.getKey());
                }
            }
        }, 15, 15, TimeUnit.SECONDS);

        // Cleanup — purge old events every 30 minutes
        scheduler.scheduleAtFixedRate(() -> {
            try {
                int deleted = eventRepository.purgeOlderThan24h(100_000);
                if (deleted > 0) {
                    log.info("Purged {} expired events", deleted);
                }
            } catch (Exception e) {
                log.error("Event purge failed", e);
            }
        }, 1, 30, TimeUnit.MINUTES);

        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down...");
            eventConsumer.stop();
            scheduler.shutdown();
            server.shutdownNow();
            dbManager.close();
        }));

        log.info("SSE Server started at {}", BASE_URI);
        Thread.currentThread().join();
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null ? value : defaultValue;
    }
}
