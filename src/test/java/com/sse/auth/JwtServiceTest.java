package com.sse.auth;

import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    private final JwtService jwt = new JwtService("test-secret-key-that-is-long-enough-for-hmac");

    @Test
    void generateAndExtractClientId() {
        String token = jwt.generateToken("client-42");
        Optional<String> clientId = jwt.extractClientId(token);
        assertTrue(clientId.isPresent());
        assertEquals("client-42", clientId.get());
    }

    @Test
    void invalidTokenReturnsEmpty() {
        Optional<String> clientId = jwt.extractClientId("garbage");
        assertTrue(clientId.isEmpty());
    }

    @Test
    void tamperedTokenReturnsEmpty() {
        String token = jwt.generateToken("client-42");
        Optional<String> clientId = jwt.extractClientId(token + "x");
        assertTrue(clientId.isEmpty());
    }
}
