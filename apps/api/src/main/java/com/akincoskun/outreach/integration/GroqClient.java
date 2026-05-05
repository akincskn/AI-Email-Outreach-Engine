package com.akincoskun.outreach.integration;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
public class GroqClient implements LlmClient {

    private final WebClient webClient;
    private final String model;

    public GroqClient(
        @Value("${app.ai.groq.api-key}") String apiKey,
        @Value("${app.ai.groq.base-url}") String baseUrl,
        @Value("${app.ai.groq.model}") String model,
        @Value("${app.ai.groq.timeout-seconds}") int timeoutSeconds
    ) {
        this.model = model;
        this.webClient = WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    @Override
    public String providerName() {
        return "groq";
    }

    @Override
    @CircuitBreaker(name = "groq")
    public Mono<String> complete(String systemPrompt, String userPrompt) {
        LlmRequest request = LlmRequest.of(model, systemPrompt, userPrompt);
        return webClient.post()
            .uri("/chat/completions")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(GroqResponse.class)
            .map(GroqResponse::firstContent)
            .timeout(Duration.ofSeconds(30));
    }
}
