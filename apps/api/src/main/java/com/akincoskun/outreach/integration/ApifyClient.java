package com.akincoskun.outreach.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Discovers companies via the <a href="https://apify.com">Apify</a> Google Maps
 * scraper actor (default {@code compass/crawler-google-places}). Used as a
 * second {@link CompanyDataSource} where OSM is too sparse (notably Turkey).
 *
 * <p>Registered as a bean only when {@code app.apify.api-token} is configured,
 * so a deployment without an Apify token simply runs OSM-only. Cost control is
 * deliberate: Apify bills per scraped place, so we disable the paid enrichments
 * ({@code scrapeContacts}, images, review personal data) to preserve the free
 * tier (~1250 places/month).
 */
@Component
@ConditionalOnProperty(name = "app.apify.api-token")
@Slf4j
public class ApifyClient implements CompanyDataSource {

    private static final String RUN_SYNC_PATH = "/v2/acts/{actorId}/run-sync-get-dataset-items";

    /** Matches {@code ...&query_place_id=ChIJ...} in a Google Maps place URL. */
    private static final Pattern PLACE_ID_PARAM = Pattern.compile("query_place_id=([^&]+)");

    /** Polite pause between successive Apify calls in a multi-search run. */
    private static final long INTER_CALL_PAUSE_MS = 1000L;

    private final WebClient webClient;
    private final String apiToken;
    private final String actorId;
    private final int timeoutSeconds;
    private final int maxPlacesPerSearch;

    public ApifyClient(
        @Value("${app.apify.api-token:}") String apiToken,
        @Value("${app.apify.google-maps-actor:compass~crawler-google-places}") String actorId,
        @Value("${app.apify.timeout-seconds:120}") int timeoutSeconds,
        @Value("${app.apify.max-places-per-search:50}") int maxPlacesPerSearch
    ) {
        this.apiToken = apiToken;
        this.actorId = actorId;
        this.timeoutSeconds = timeoutSeconds;
        this.maxPlacesPerSearch = maxPlacesPerSearch;
        this.webClient = WebClient.builder()
            .baseUrl("https://api.apify.com")
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
            .build();
    }

    @Override
    public String sourceName() {
        return "apify";
    }

    /**
     * Fans out over {@code keywords × cities} (Görev 12), deduping places by
     * their stable id across all sub-searches. Bounded by
     * {@link DiscoveryQuery#effectiveMaxTotalPlaces()} so a wide filter cannot
     * burn the Apify free tier: once the cap is hit we stop making calls.
     */
    @Override
    public List<DiscoveredPlace> search(DiscoveryQuery query) {
        if (apiToken == null || apiToken.isBlank()) {
            log.error("Apify selected but APIFY_API_TOKEN is not set — returning no results");
            return Collections.emptyList();
        }

        List<String> keywords = query.effectiveKeywords();
        List<String> cities = query.effectiveCities();
        int maxTotal = query.effectiveMaxTotalPlaces();

        List<DiscoveredPlace> allPlaces = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        outer:
        for (String keyword : keywords) {
            for (String city : cities) {
                if (allPlaces.size() >= maxTotal) break outer;

                DiscoveryQuery sub =
                    new DiscoveryQuery(query.industry(), query.countryCode(), city, keyword);
                for (DiscoveredPlace place : singleSearch(sub)) {
                    // Dedup across sub-searches; the same place can match several
                    // keywords or sit on a city boundary.
                    String dedupKey = place.osmId() != null ? place.osmId() : place.name();
                    if (seenIds.add(dedupKey)) {
                        allPlaces.add(place);
                        if (allPlaces.size() >= maxTotal) break;
                    }
                }
                pauseBetweenCalls();
            }
        }

        log.info("Apify multi-search returned {} unique place(s) across {} keyword(s) × {} city(ies)",
            allPlaces.size(), keywords.size(), cities.size());
        return allPlaces;
    }

    /** One actor run for a single keyword+city. Never throws — empty on error. */
    private List<DiscoveredPlace> singleSearch(DiscoveryQuery query) {
        Map<String, Object> input = buildInput(query);
        log.info("Apify search: '{}' in '{}'", input.get("searchStringsArray"), input.get("locationQuery"));

        // run-sync-get-dataset-items runs the actor and returns the dataset items
        // (a JSON array) in a single blocking call.
        List<ApifyPlace> items = webClient.post()
            .uri(uri -> uri.path(RUN_SYNC_PATH)
                .queryParam("memory", 4096)
                .queryParam("timeout", timeoutSeconds)
                .build(actorId))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(input)
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<List<ApifyPlace>>() {})
            .onErrorResume(ex -> {
                log.error("Apify API error: {}", ex.getMessage());
                return Mono.just(Collections.emptyList());
            })
            .timeout(Duration.ofSeconds(timeoutSeconds + 60L))
            .block();

        return parse(items);
    }

    /** Respects Apify rate limits between sub-searches; interrupt-safe. */
    private void pauseBetweenCalls() {
        try {
            Thread.sleep(INTER_CALL_PAUSE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Builds the actor input JSON. Paid enrichments are OFF to save credits. */
    Map<String, Object> buildInput(DiscoveryQuery query) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("searchStringsArray", List.of(searchString(query)));
        input.put("locationQuery", locationQuery(query));
        input.put("language", "tr");
        input.put("maxCrawledPlacesPerSearch", maxPlacesPerSearch);
        input.put("maxImages", 0);
        input.put("includeWebResults", false);
        input.put("scrapeReviewsPersonalData", false);
        input.put("scrapeContacts", false);
        return input;
    }

    /** Search term: prefer the filter keyword, fall back to industry; append city. */
    String searchString(DiscoveryQuery query) {
        String term = firstNonBlank(query.keyword(), query.industry());
        String city = query.city();
        if (city != null && !city.isBlank()) {
            return (term == null ? "" : term + " ") + city.strip();
        }
        return term == null ? "" : term;
    }

    /** Location for the actor: "{city}, {country name}" or just the country name. */
    String locationQuery(DiscoveryQuery query) {
        String country = countryName(query.countryCode());
        if (query.city() != null && !query.city().isBlank()) {
            return query.city().strip() + (country.isBlank() ? "" : ", " + country);
        }
        return country;
    }

    /** Maps an ISO country code to the full name the actor expects. */
    static String countryName(String code) {
        if (code == null) return "";
        return switch (code.toUpperCase()) {
            case "TR" -> "Turkey";
            case "US" -> "United States";
            case "GB" -> "United Kingdom";
            case "DE" -> "Germany";
            default -> code.toUpperCase();
        };
    }

    List<DiscoveredPlace> parse(List<ApifyPlace> items) {
        if (items == null) return Collections.emptyList();
        List<DiscoveredPlace> places = new ArrayList<>();
        for (ApifyPlace item : items) {
            if (item == null) continue;
            String name = blankToNull(item.title());
            if (name == null) continue;
            // Stable dedup id (osmId slot): the short Google place_id (~30 chars).
            // The full Maps URL is 150-250 chars and overflows osm_id VARCHAR(128);
            // the place_id is enough to rebuild the URL when needed.
            String externalId = firstNonBlank(item.placeId(), extractPlaceId(item.url()), name);
            places.add(new DiscoveredPlace(
                externalId,
                name,
                blankToNull(item.website()),
                blankToNull(item.address()),
                blankToNull(item.phone())));
        }
        return places;
    }

    /** Pulls the {@code ChIJ…} place id out of a Maps URL's query_place_id param. */
    static String extractPlaceId(String googleMapsUrl) {
        if (googleMapsUrl == null) return null;
        Matcher matcher = PLACE_ID_PARAM.matcher(googleMapsUrl);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** Treats null, blank, and the literal "undefined"/"null" strings as absent. */
    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.strip();
        if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("undefined")
                || trimmed.equalsIgnoreCase("null")) {
            return null;
        }
        return trimmed;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            String cleaned = blankToNull(value);
            if (cleaned != null) return cleaned;
        }
        return null;
    }

    /** A single place from the actor's dataset. Unknown fields are ignored. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ApifyPlace(String title, String website, String phone, String address,
                      String city, String url, String placeId) {}
}
