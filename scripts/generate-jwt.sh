#!/usr/bin/env bash
# Generates a JWT for testing SSE endpoint
set -euo pipefail

CLIENT_ID="${1:?Usage: $0 <client-id>}"
SECRET="change-me-in-production-use-a-real-secret"

# Base64url encode
b64url() { openssl base64 -e -A | tr '+/' '-_' | tr -d '='; }

HEADER=$(echo -n '{"alg":"HS256","typ":"JWT"}' | b64url)
PAYLOAD=$(echo -n "{\"sub\":\"$CLIENT_ID\"}" | b64url)
SIGNATURE=$(echo -n "$HEADER.$PAYLOAD" | openssl dgst -sha256 -hmac "$SECRET" -binary | b64url)

echo "$HEADER.$PAYLOAD.$SIGNATURE"
echo ""
echo "Test with:"
echo "  curl -N -H 'Authorization: Bearer $HEADER.$PAYLOAD.$SIGNATURE' http://localhost:8080/events"
