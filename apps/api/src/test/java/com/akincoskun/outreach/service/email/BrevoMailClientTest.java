package com.akincoskun.outreach.service.email;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BrevoMailClientTest {

    private static final BrevoMailClient.SendRequest REQ = new BrevoMailClient.SendRequest(
        "info@target.com", null, "Subject", "<p>Hi</p>", "Hi", Map.of());

    private BrevoMailClient clientReturning(HttpStatus status, String body) {
        ExchangeFunction exchange = request -> Mono.just(
            ClientResponse.create(status)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build());
        return new BrevoMailClient(
            WebClient.builder().exchangeFunction(exchange),
            "test-key", "sender@outreach.test", "Akın Coşkun", 30);
    }

    private BrevoMailClient clientFailing(Throwable error) {
        ExchangeFunction exchange = request -> Mono.error(error);
        return new BrevoMailClient(
            WebClient.builder().exchangeFunction(exchange),
            "test-key", "sender@outreach.test", "Akın Coşkun", 30);
    }

    @Test
    void success200ReturnsMessageId() {
        BrevoMailClient client = clientReturning(
            HttpStatus.CREATED, "{\"messageId\":\"<abc@brevo.com>\"}");

        assertThat(client.send(REQ)).isEqualTo("<abc@brevo.com>");
    }

    @Test
    void unauthorized401ThrowsBrevoException() {
        BrevoMailClient client = clientReturning(
            HttpStatus.UNAUTHORIZED, "{\"code\":\"unauthorized\"}");

        assertThatThrownBy(() -> client.send(REQ))
            .isInstanceOf(BrevoException.class)
            .hasMessageContaining("401");
    }

    @Test
    void rateLimited429ThrowsBrevoException() {
        BrevoMailClient client = clientReturning(
            HttpStatus.TOO_MANY_REQUESTS, "{\"code\":\"too_many_requests\"}");

        assertThatThrownBy(() -> client.send(REQ))
            .isInstanceOf(BrevoException.class)
            .hasMessageContaining("429");
    }

    @Test
    void serverError500ThrowsBrevoException() {
        BrevoMailClient client = clientReturning(
            HttpStatus.INTERNAL_SERVER_ERROR, "boom");

        assertThatThrownBy(() -> client.send(REQ))
            .isInstanceOf(BrevoException.class)
            .hasMessageContaining("500");
    }

    @Test
    void transportErrorIsWrappedAsBrevoException() {
        BrevoMailClient client = clientFailing(new RuntimeException("connection reset"));

        assertThatThrownBy(() -> client.send(REQ))
            .isInstanceOf(BrevoException.class)
            .hasMessageContaining("Brevo send failed");
    }
}
