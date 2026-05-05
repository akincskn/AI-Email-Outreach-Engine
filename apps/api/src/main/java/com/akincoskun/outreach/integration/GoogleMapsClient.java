package com.akincoskun.outreach.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class GoogleMapsClient {

    private final WebClient webClient;
    private final String apiKey;

    public GoogleMapsClient(
        @Value("${app.google-maps.api-key}") String apiKey
    ) {
        this.apiKey = apiKey;
        this.webClient = WebClient.builder()
            .baseUrl("https://maps.googleapis.com/maps/api")
            .build();
    }

    public List<PlaceResult> searchPlaces(String query, String location) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Google Maps API key not configured, returning empty results");
            return Collections.emptyList();
        }

        String textQuery = query + " " + location;
        return webClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/place/textsearch/json")
                .queryParam("query", textQuery)
                .queryParam("key", apiKey)
                .build())
            .retrieve()
            .bodyToMono(PlacesResponse.class)
            .map(PlacesResponse::results)
            .onErrorResume(ex -> {
                log.error("Google Maps API error: {}", ex.getMessage());
                return Mono.just(Collections.emptyList());
            })
            .timeout(Duration.ofSeconds(10))
            .block();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlacesResponse(List<PlaceResult> results) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PlaceResult(
        String name,
        String website,
        String formattedAddress
    ) {}
}
