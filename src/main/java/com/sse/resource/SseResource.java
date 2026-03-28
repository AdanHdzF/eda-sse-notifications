package com.sse.resource;

import com.sse.auth.JwtService;
import com.sse.db.EventRepository;
import com.sse.model.ClientEvent;
import com.sse.sse.ConnectionManager;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

@Path("/events")
public class SseResource {
    private static final Logger log = LoggerFactory.getLogger(SseResource.class);
    private static final int REPLAY_LIMIT = 10_000;

    private final JwtService jwtService;
    private final ConnectionManager connectionManager;
    private final EventRepository eventRepository;

    @Inject
    public SseResource(JwtService jwtService, ConnectionManager connectionManager,
                       EventRepository eventRepository) {
        this.jwtService = jwtService;
        this.connectionManager = connectionManager;
        this.eventRepository = eventRepository;
    }

    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void subscribe(@Context SseEventSink sink, @Context Sse sse,
                          @Context HttpHeaders headers,
                          @HeaderParam("Last-Event-ID") String lastEventId) {
        String authHeader = headers.getHeaderString("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sink.send(sse.newEventBuilder()
                    .name("error")
                    .data("Missing or invalid Authorization header")
                    .build());
            sink.close();
            return;
        }

        String token = authHeader.substring(7);
        Optional<String> clientId = jwtService.extractClientId(token);
        if (clientId.isEmpty()) {
            sink.send(sse.newEventBuilder()
                    .name("error")
                    .data("Invalid JWT token")
                    .build());
            sink.close();
            return;
        }

        String cid = clientId.get();

        // Capture Sse instance for use by Kafka consumer push
        connectionManager.setSse(sse);

        // Register FIRST, then replay (avoids gap)
        connectionManager.register(cid, sink);

        if (lastEventId != null && !lastEventId.isEmpty()) {
            try {
                long afterId = Long.parseLong(lastEventId);
                List<ClientEvent> missed = eventRepository.replayAfter(cid, afterId, REPLAY_LIMIT);

                if (missed.isEmpty()) {
                    sink.send(sse.newEventBuilder()
                            .name("SYNC_RESET")
                            .data("{\"reason\":\"Events may have expired. Please refresh state.\"}")
                            .build());
                } else {
                    for (ClientEvent event : missed) {
                        OutboundSseEvent sseEvent = sse.newEventBuilder()
                                .id(String.valueOf(event.id()))
                                .name(event.eventType())
                                .data(event.payload())
                                .build();
                        sink.send(sseEvent);
                    }
                }
            } catch (NumberFormatException e) {
                log.warn("Invalid Last-Event-ID: {}", lastEventId);
            } catch (Exception e) {
                log.error("Replay failed for client {}", cid, e);
            }
        }

        log.info("Client {} subscribed (replay from: {})", cid, lastEventId);
    }
}
