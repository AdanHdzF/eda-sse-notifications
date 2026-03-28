import jwt from "jsonwebtoken";
import EventSource from "eventsource";

const SSE_URL = process.env.SSE_URL || "http://localhost:8080/events";
const JWT_SECRET =
  process.env.JWT_SECRET || "change-me-in-production-use-a-real-secret";
const CLIENT_COUNT = parseInt(process.env.CLIENT_COUNT || "10", 10);

const EVENT_TYPES = [
  "ORDER_CREATED",
  "ORDER_UPDATED",
  "ORDER_SHIPPED",
  "PAYMENT_RECEIVED",
  "PAYMENT_FAILED",
  "INVENTORY_LOW",
  "INVENTORY_RESTOCKED",
  "USER_LOGIN",
  "USER_LOGOUT",
  "NOTIFICATION_SENT",
];

function createSubscriber(clientId) {
  const token = jwt.sign({ sub: clientId }, JWT_SECRET);

  const es = new EventSource(SSE_URL, {
    headers: { Authorization: `Bearer ${token}` },
  });

  let lastId = 0;
  let eventCount = 0;

  for (const eventType of EVENT_TYPES) {
    es.addEventListener(eventType, (e) => {
      const id = Number(e.lastEventId);
      if (id <= lastId) return; // deduplicate
      lastId = id;
      eventCount++;
      const data = JSON.parse(e.data);
      console.log(
        `[${clientId}] #${id} ${eventType} | ${JSON.stringify(data)}`
      );
    });
  }

  es.addEventListener("SYNC_RESET", () => {
    console.log(`[${clientId}] SYNC_RESET — need full state refresh`);
  });

  es.addEventListener("error", () => {
    console.log(`[${clientId}] error`);
  });

  es.onerror = (err) => {
    if (es.readyState === EventSource.CONNECTING) {
      console.log(`[${clientId}] reconnecting...`);
    } else {
      console.error(`[${clientId}] connection error`, err.message || "");
    }
  };

  es.onopen = () => {
    console.log(`[${clientId}] connected`);
  };

  return { clientId, es, getEventCount: () => eventCount };
}

// Launch subscribers
const subscribers = [];
for (let i = 1; i <= CLIENT_COUNT; i++) {
  const clientId = `client-${String(i).padStart(3, "0")}`;
  subscribers.push(createSubscriber(clientId));
}

console.log(`Started ${CLIENT_COUNT} subscribers connecting to ${SSE_URL}`);

// Stats every 10 seconds
setInterval(() => {
  const total = subscribers.reduce((sum, s) => sum + s.getEventCount(), 0);
  console.log(
    `[stats] ${total} events received across ${CLIENT_COUNT} subscribers`
  );
}, 10_000);

// Graceful shutdown
process.on("SIGINT", () => {
  console.log("\nShutting down subscribers...");
  for (const s of subscribers) {
    s.es.close();
  }
  process.exit(0);
});
