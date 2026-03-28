#!/usr/bin/env bash
# Sends test events to Kafka for a given client
# Usage: ./produce-test-events.sh <client-id> [count]
set -euo pipefail

CLIENT_ID="${1:?Usage: $0 <client-id> [count]}"
COUNT="${2:-10}"
TOPIC="client-events"
EVENT_TYPES=("ORDER_CREATED" "ORDER_UPDATED" "ORDER_SHIPPED" "PAYMENT_RECEIVED" "PAYMENT_FAILED" "INVENTORY_LOW" "INVENTORY_RESTOCKED" "USER_LOGIN" "USER_LOGOUT" "NOTIFICATION_SENT")

for i in $(seq 1 "$COUNT"); do
  EVENT_TYPE="${EVENT_TYPES[$((RANDOM % ${#EVENT_TYPES[@]}))]}"
  TS=$(date +%s)000
  PAYLOAD="{\"index\":$i,\"timestamp\":$TS}"
  MESSAGE="{\"clientId\":\"$CLIENT_ID\",\"eventType\":\"$EVENT_TYPE\",\"payload\":$PAYLOAD,\"timestamp\":$TS}"
  echo "$MESSAGE" | docker compose exec -T kafka kafka-console-producer \
    --bootstrap-server localhost:9092 \
    --topic "$TOPIC"
  echo "Sent $EVENT_TYPE for $CLIENT_ID ($i/$COUNT)"
  sleep 0.1
done
