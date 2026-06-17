package com.akincoskun.outreach.integration;

import java.util.List;

/**
 * Faz 2 placeholder for an <a href="https://apify.com">Apify</a>-backed data
 * source (Google Maps scrapers, etc.).
 *
 * <p>Deliberately <b>not</b> a Spring {@code @Component}: registering it as a
 * bean would create an ambiguous {@link CompanyDataSource} alongside
 * {@link OsmClient}. It exists only to lock the interface contract in place so
 * Faz 2 can wire a real implementation without reshaping the pipeline.
 */
public class ApifyClient implements CompanyDataSource {

    @Override
    public List<DiscoveredPlace> search(DiscoveryQuery query) {
        throw new UnsupportedOperationException("ApifyClient is a Faz 2 placeholder");
    }

    @Override
    public String sourceName() {
        return "apify";
    }
}
