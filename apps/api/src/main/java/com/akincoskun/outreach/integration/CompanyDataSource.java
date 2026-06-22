package com.akincoskun.outreach.integration;

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
     * Provider-agnostic query: an industry slug, an optional location, and an
     * optional free-text {@code keyword} (the filter's first keyword). OSM keys
     * off {@code industry}; Apify prefers {@code keyword} for a natural Google
     * Maps search string (e.g. "apartman yönetimi" beats "property_management").
     */
    record DiscoveryQuery(String industry, String countryCode, String city, String keyword) {}

    /**
     * A single discovered place. {@code website} may be null/blank (no site).
     * {@code osmId} is the provider-unique id (e.g. {@code "node/123"}) used to
     * dedupe across discovery runs — implementations must always set it.
     */
    record DiscoveredPlace(String osmId, String name, String website, String address, String phone) {}
}
