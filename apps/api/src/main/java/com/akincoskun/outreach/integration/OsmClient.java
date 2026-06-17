package com.akincoskun.outreach.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Discovers companies via the OpenStreetMap Overpass API.
 *
 * <p>Overpass is free and key-less but politely rate limited; we enforce a
 * minimum interval between requests (default 1 req/s) so a tight discovery
 * loop cannot get us throttled or banned.
 */
@Component
@Slf4j
public class OsmClient implements CompanyDataSource {

    /** Maps our industry slugs to Overpass tag selectors (key=value). */
    private static final Map<String, List<String>> INDUSTRY_TAGS = Map.of(
        "property_management", List.of("office=property_management"),
        "restaurant",          List.of("amenity=restaurant", "amenity=cafe"),
        "marketing",           List.of("office=advertising_agency", "office=marketing"),
        "saas",                List.of("office=it", "office=company"),
        "accounting",          List.of("office=accountant"),
        "law",                 List.of("office=lawyer"),
        "real_estate",         List.of("office=estate_agent")
    );

    /** Fallback selector when an industry slug has no explicit mapping. */
    private static final List<String> DEFAULT_TAGS = List.of("office=company");

    private final WebClient webClient;
    private final long minIntervalMs;
    private final int timeoutSeconds;

    private final ReentrantLock rateLock = new ReentrantLock();
    private long lastRequestAtMs = 0L;

    public OsmClient(
        @Value("${app.osm.base-url}") String baseUrl,
        @Value("${app.osm.min-request-interval-ms:1000}") long minIntervalMs,
        @Value("${app.osm.timeout-seconds:25}") int timeoutSeconds
    ) {
        this.minIntervalMs = minIntervalMs;
        this.timeoutSeconds = timeoutSeconds;
        this.webClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    @Override
    public String sourceName() {
        return "osm";
    }

    @Override
    public List<DiscoveredPlace> search(DiscoveryQuery query) {
        String overpassQl = buildQuery(query);
        log.debug("OSM Overpass query:\n{}", overpassQl);

        throttle();

        OverpassResponse response = webClient.post()
            .body(BodyInserters.fromFormData("data", overpassQl))
            .retrieve()
            .bodyToMono(OverpassResponse.class)
            .onErrorResume(ex -> {
                log.error("OSM Overpass API error: {}", ex.getMessage());
                return Mono.just(new OverpassResponse(Collections.emptyList()));
            })
            .timeout(Duration.ofSeconds(timeoutSeconds + 5))
            .block();

        return parse(response);
    }

    /** Builds Overpass QL for an industry within a city (preferred) or country. */
    String buildQuery(DiscoveryQuery query) {
        List<String> selectors = INDUSTRY_TAGS.getOrDefault(
            query.industry() == null ? "" : query.industry().toLowerCase(),
            DEFAULT_TAGS
        );

        StringBuilder union = new StringBuilder();
        for (String selector : selectors) {
            int eq = selector.indexOf('=');
            String key = selector.substring(0, eq);
            String value = selector.substring(eq + 1);
            union.append("  node[\"").append(key).append("\"=\"").append(value)
                 .append("\"](area.searchArea);\n");
            union.append("  way[\"").append(key).append("\"=\"").append(value)
                 .append("\"](area.searchArea);\n");
        }

        return "[out:json][timeout:" + timeoutSeconds + "];\n"
            + areaClause(query)
            + "(\n" + union + ");\n"
            + "out body center;\n";
    }

    private String areaClause(DiscoveryQuery query) {
        if (query.city() != null && !query.city().isBlank()) {
            return "area[\"name\"=\"" + escape(query.city()) + "\"]->.searchArea;\n";
        }
        if (query.countryCode() != null && !query.countryCode().isBlank()) {
            return "area[\"ISO3166-1\"=\"" + escape(query.countryCode().toUpperCase())
                + "\"]->.searchArea;\n";
        }
        // No location filter: search the whole planet (rarely useful, but valid QL).
        return "area->.searchArea;\n";
    }

    List<DiscoveredPlace> parse(OverpassResponse response) {
        if (response == null || response.elements() == null) {
            return Collections.emptyList();
        }
        List<DiscoveredPlace> places = new ArrayList<>();
        for (OverpassElement element : response.elements()) {
            Map<String, String> tags = element.tags();
            if (tags == null) continue;

            String name = tags.get("name");
            if (name == null || name.isBlank()) continue;

            String website = firstNonBlank(tags.get("website"), tags.get("contact:website"));
            String phone = firstNonBlank(tags.get("phone"), tags.get("contact:phone"));
            String osmId = element.type() + "/" + element.id();
            places.add(new DiscoveredPlace(osmId, name.strip(), website, buildAddress(tags), phone));
        }
        return places;
    }

    private String buildAddress(Map<String, String> tags) {
        StringBuilder sb = new StringBuilder();
        append(sb, tags.get("addr:street"));
        append(sb, tags.get("addr:housenumber"));
        append(sb, tags.get("addr:postcode"));
        append(sb, tags.get("addr:city"));
        return sb.isEmpty() ? null : sb.toString();
    }

    private void append(StringBuilder sb, String part) {
        if (part == null || part.isBlank()) return;
        if (!sb.isEmpty()) sb.append(", ");
        sb.append(part.strip());
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a.strip();
        if (b != null && !b.isBlank()) return b.strip();
        return null;
    }

    private static String escape(String value) {
        return value.replace("\"", "\\\"");
    }

    /** Blocks until at least {@code minIntervalMs} has passed since the last request. */
    private void throttle() {
        rateLock.lock();
        try {
            long now = System.currentTimeMillis();
            long wait = minIntervalMs - (now - lastRequestAtMs);
            if (wait > 0) {
                Thread.sleep(wait);
            }
            lastRequestAtMs = System.currentTimeMillis();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            rateLock.unlock();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OverpassResponse(List<OverpassElement> elements) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OverpassElement(String type, long id, Map<String, String> tags) {}
}
