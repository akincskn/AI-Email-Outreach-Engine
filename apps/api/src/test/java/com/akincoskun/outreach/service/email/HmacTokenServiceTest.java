package com.akincoskun.outreach.service.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class HmacTokenServiceTest {

    private HmacTokenService service;

    @BeforeEach
    void setUp() {
        service = new HmacTokenService("test-secret-32-chars-long-enough");
    }

    @Test
    void tokenVerifiesWithOriginalPayload() {
        String token = service.generateToken("info@example.com");
        assertThat(service.verifyToken(token, "info@example.com")).isTrue();
    }

    @Test
    void tokenFailsWithWrongPayload() {
        String token = service.generateToken("info@example.com");
        assertThat(service.verifyToken(token, "other@example.com")).isFalse();
    }

    @Test
    void tokenFailsWhenTampered() {
        String token = service.generateToken("info@example.com");
        String tampered = token.substring(0, token.length() - 4) + "0000";
        assertThat(service.verifyToken(tampered, "info@example.com")).isFalse();
    }

    @Test
    void twoDifferentTokensForSamePayload() {
        String t1 = service.generateToken("pixel");
        String t2 = service.generateToken("pixel");
        assertThat(t1).isNotEqualTo(t2); // nonce makes them unique
        assertThat(service.verifyToken(t1, "pixel")).isTrue();
        assertThat(service.verifyToken(t2, "pixel")).isTrue();
    }

    @Test
    void emptyTokenReturnsFalse() {
        assertThat(service.verifyToken("", "any")).isFalse();
    }

    @Test
    void malformedHexReturnsFalse() {
        assertThat(service.verifyToken("ZZZZZZ", "any")).isFalse();
    }
}
