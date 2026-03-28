package com.sse.sse;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConnectionManagerTest {
    @Test
    void registerAndLookup() {
        var manager = new ConnectionManager();
        assertFalse(manager.isConnected("client-1"));
        assertEquals(0, manager.getConnectionCount());
    }
}
