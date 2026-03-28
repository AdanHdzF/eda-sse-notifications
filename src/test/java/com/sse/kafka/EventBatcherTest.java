package com.sse.kafka;

import com.sse.model.ClientEvent;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class EventBatcherTest {
    @Test
    void flushesOnSizeThreshold() throws Exception {
        List<List<ClientEvent>> flushed = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        Consumer<List<ClientEvent>> onFlush = batch -> {
            flushed.add(new ArrayList<>(batch));
            latch.countDown();
        };

        var batcher = new EventBatcher(onFlush, 3, 5000);
        batcher.add(new ClientEvent("c1", "TYPE_A", "{}"));
        batcher.add(new ClientEvent("c2", "TYPE_B", "{}"));
        batcher.add(new ClientEvent("c3", "TYPE_C", "{}"));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(1, flushed.size());
        assertEquals(3, flushed.get(0).size());
        batcher.shutdown();
    }

    @Test
    void flushesOnTimeThreshold() throws Exception {
        List<List<ClientEvent>> flushed = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        Consumer<List<ClientEvent>> onFlush = batch -> {
            flushed.add(new ArrayList<>(batch));
            latch.countDown();
        };

        var batcher = new EventBatcher(onFlush, 500, 200);
        batcher.add(new ClientEvent("c1", "TYPE_A", "{}"));

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(1, flushed.size());
        assertEquals(1, flushed.get(0).size());
        batcher.shutdown();
    }
}
