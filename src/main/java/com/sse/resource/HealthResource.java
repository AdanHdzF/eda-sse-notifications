package com.sse.resource;

import com.sse.sse.ConnectionManager;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;

@Path("/health")
public class HealthResource {
    private final ConnectionManager connectionManager;

    @Inject
    public HealthResource(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> health() {
        return Map.of(
            "status", "UP",
            "connections", connectionManager.getConnectionCount()
        );
    }
}
