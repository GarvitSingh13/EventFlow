package com.eventflow.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HmacServiceTest {

    private HmacService hmacService;

    @BeforeEach
    void setUp() {
        hmacService = new HmacService();
    }

    @Test
    @DisplayName("Should generate deterministic signature for identical secret, timestamp, and payload")
    void testDeterministicSignature() {
        String secret = "whsec_test_secret_12345";
        long timestamp = 1700000000L;
        String payload = "{\"orderId\":\"ord_123\",\"amount\":49.99}";

        String sig1 = hmacService.computeSignature(secret, timestamp, payload);
        String sig2 = hmacService.computeSignature(secret, timestamp, payload);

        assertNotNull(sig1);
        assertEquals(64, sig1.length(), "HMAC-SHA256 hex string must be exactly 64 characters");
        assertEquals(sig1, sig2, "Signatures must match for identical inputs");
    }

    @Test
    @DisplayName("Tampering payload or timestamp must alter the signature")
    void testSignatureTampering() {
        String secret = "whsec_test_secret_12345";
        long timestamp = 1700000000L;
        String payload1 = "{\"status\":\"approved\"}";
        String payload2 = "{\"status\":\"declined\"}";

        String sig1 = hmacService.computeSignature(secret, timestamp, payload1);
        String sig2 = hmacService.computeSignature(secret, timestamp, payload2);
        String sig3 = hmacService.computeSignature(secret, timestamp + 1, payload1);

        assertNotEquals(sig1, sig2, "Changing payload must change signature");
        assertNotEquals(sig1, sig3, "Changing timestamp must change signature");
    }

    @Test
    @DisplayName("Should format signature header correctly according to spec")
    void testFormatSignatureHeader() {
        String secret = "whsec_test_secret_12345";
        long timestamp = 1700000000L;
        String payload = "{\"test\":true}";

        String header = hmacService.formatSignatureHeader(secret, timestamp, payload);

        assertTrue(header.startsWith("t=" + timestamp + ",v1="), "Header must start with t={timestamp},v1=");
        assertEquals(2 + String.valueOf(timestamp).length() + 4 + 64, header.length());
    }
}
