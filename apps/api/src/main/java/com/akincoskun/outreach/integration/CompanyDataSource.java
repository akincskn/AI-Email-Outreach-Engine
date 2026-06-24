package com.akincoskun.outreach.integration;

import java.util.Collections;
import java.util.List;

/**
 * Abstraction over external company discovery providers.
 *
 * <p>Faz 1.5 ships a single {@link OsmClient} (Overpass API) implementation.
 * {@link ApifyClient} is a Faz 2 placeholder kept here so the pipeline can
 * switch providers without touching {@code CompanyDiscoveryService}.
 */
public interface CompanyDataSource {

    /**
     * Search the provider for companies matching the given industry/location.
     * Implementations must never throw on provider errors — return an empty
     * list and log instead, so a single bad query cannot break discovery.
     */
    List<DiscoveredPlace> search(DiscoveryQuery query);

    /** Stable identifier persisted on {@code companies.source} (e.g. {@code "osm"}). */
    String sourceName();

    /**
     * Provider-agnostic query. OSM keys off {@code industry} and the single
     * {@code city}; Apify (Görev 12) fans out over {@code keywords × cities} for
     * scale, preferring keywords for natural Google Maps search strings (e.g.
     * "apartman yönetimi" beats "property_management").
     *
     * <p>The 4-arg constructor builds a single-search query ({@code cities} and
     * {@code keywords} fall back to the single {@code city}/{@code keyword}).
     * {@code maxTotalPlaces} caps the total places a multi-search may return so
     * Apify costs stay bounded; null means {@link #DEFAULT_MAX_TOTAL_PLACES}.
     */
    record DiscoveryQuery(String industry, String countryCode, String city, String keyword,
                          List<String> cities, List<String> keywords, Integer maxTotalPlaces) {

        private static final int DEFAULT_MAX_TOTAL_PLACES = 100;

        /** Single-search query (OSM and unit tests). */
        public DiscoveryQuery(String industry, String countryCode, String city, String keyword) {
            this(industry, countryCode, city, keyword, null, null, null);
        }

        /** Cities to iterate, falling back to the single {@code city} (may be null). */
        public List<String> effectiveCities() {
            return (cities != null && !cities.isEmpty()) ? cities : Collections.singletonList(city);
        }

        /** Keywords to iterate, falling back to the single {@code keyword} (may be null). */
        public List<String> effectiveKeywords() {
            return (keywords != null && !keywords.isEmpty()) ? keywords : Collections.singletonList(keyword);
        }

        public int effectiveMaxTotalPlaces() {
            return maxTotalPlaces != null ? maxTotalPlaces : DEFAULT_MAX_TOTAL_PLACES;
        }
    }

    /**
     * A single discovered place. {@code website} may be null/blank (no site).
     * {@code osmId} is the provider-unique id (e.g. {@code "node/123"}) used to
     * dedupe across discovery runs — implementations must always set it.
     */
    record DiscoveredPlace(String osmId, String name, String website, String address, String phone) {}
}
