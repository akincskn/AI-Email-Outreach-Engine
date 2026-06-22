package com.akincoskun.outreach.domain;

/**
 * Which provider a {@link DiscoveryFilter} discovers companies from (Görev 11).
 *
 * <p>{@code beanName} matches the Spring bean name of the corresponding
 * {@link com.akincoskun.outreach.integration.CompanyDataSource} implementation,
 * so {@code CompanyDiscoveryService} can resolve the right source from the
 * injected bean map. {@code code} is the lowercase value persisted in
 * {@code discovery_filters.source} (see {@link DiscoverySourceConverter}).
 */
public enum DiscoverySource {

    /** OpenStreetMap Overpass — free, key-less, but sparse outside big cities. */
    OSM("osm", "osmClient"),

    /** Apify Google Maps scraper — paid ($4/1000 places) but rich and dense. */
    APIFY("apify", "apifyClient");

    private final String code;
    private final String beanName;

    DiscoverySource(String code, String beanName) {
        this.code = code;
        this.beanName = beanName;
    }

    public String code() {
        return code;
    }

    public String beanName() {
        return beanName;
    }

    /** Resolves the DB code to an enum, defaulting to {@link #OSM} for null. */
    public static DiscoverySource fromCode(String code) {
        if (code == null || code.isBlank()) return OSM;
        for (DiscoverySource source : values()) {
            if (source.code.equalsIgnoreCase(code.strip())) return source;
        }
        throw new IllegalArgumentException("Unknown discovery source code: " + code);
    }
}
