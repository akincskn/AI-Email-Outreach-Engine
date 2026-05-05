package com.akincoskun.outreach.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class GeminiClient implements LlmClient {

    private final WebClient webClient;
    private final String model;

    public GeminiClient(
        @Value("${app.ai.gemini.api-key}") String apiKey,
        @Value("${app.ai.gemini.base-url}") String baseUrl,
        @Value("${app.ai.gemini.model}") String model,
        @Value("${app.ai.gemini.timeout-seconds}") int timeoutSeconds
    ) {
        this.model = model;
        this.webClient = WebClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultUriVariables(Map.of("apiKey", apiKey))
            .build();
    }

    @Override
    public String providerName() {
        return "gemini";
    }

    @Override
    @CircuitBreaker(name = "gemini")
    public Mono<String> complete(String systemPrompt, String userPrompt) {
        Map<String, Object> body = Map.of(
            "contents", List.of(
                Map.of("role", "user", "parts", List.of(
                    Map.of("text", systemPrompt + "\n\n" + userPrompt)
                ))
            ),
            "generationConfig", Map.of(
                "responseMimeType", "application/json",
                "temperature", 0.3
            )
        );

        return webClient.post()
            .uri("/models/" + model + ":generateContent?key={apiKey}", "placeholder")
            .bodyValue(body)
            .retrieve()
            .bodyToMono(GeminiResponse.class)
            .map(GeminiResponse::firstContent)
            .timeout(Duration.ofSeconds(30));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiResponse(List<Candidate> candidates) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record Candidate(Content content) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Content(List<Part> parts) {}

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Part(String text) {}

        String firstContent() {
            if (candidates == null || candidates.isEmpty()) {
                throw new IllegalStateException("Gemini returned empty candidates");
            }
            return candidates.get(0).content().parts().get(0).text();
        }
    }
}
