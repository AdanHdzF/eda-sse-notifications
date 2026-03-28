package com.sse.db;

import com.sse.model.ClientEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class EventRepositoryTest {
    @Test
    void insertAndReplay() throws Exception {
        var db = new DatabaseManager(
            "jdbc:mysql://localhost:3306/sse_events", "sseuser", "ssepass");
        var repo = new EventRepository(db.getDataSource());

        var events = List.of(
            new ClientEvent("test-client", "ORDER_CREATED", "{\"id\":1}"),
            new ClientEvent("test-client", "ORDER_UPDATED", "{\"id\":1,\"status\":\"shipped\"}"),
            new ClientEvent("other-client", "ORDER_CREATED", "{\"id\":2}")
        );

        List<Long> ids = repo.insertBatch(events);
        assertEquals(3, ids.size());

        var replayed = repo.replayAfter("test-client", ids.get(0), 100);
        assertEquals(1, replayed.size());
        assertEquals("ORDER_UPDATED", replayed.get(0).eventType());

        var otherReplayed = repo.replayAfter("other-client", 0, 100);
        assertEquals(1, otherReplayed.size());

        db.close();
    }
}
