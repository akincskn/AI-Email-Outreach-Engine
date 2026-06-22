package com.akincoskun.outreach.service.discovery;

import com.akincoskun.outreach.domain.Company;
import com.akincoskun.outreach.domain.CompanyStatus;
import com.akincoskun.outreach.domain.DiscoveredSkipped;
import com.akincoskun.outreach.domain.DiscoveryFilter;
import com.akincoskun.outreach.domain.DiscoverySource;
import com.akincoskun.outreach.dto.CompanyDiscoverRequest;
import com.akincoskun.outreach.exception.BusinessException;
import com.akincoskun.outreach.integration.CompanyDataSource;
import com.akincoskun.outreach.repository.CompanyRepository;
import com.akincoskun.outreach.repository.DiscoveredSkippedRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyDiscoveryService {

    private final CompanyRepository companyRepository;
    private final DiscoveredSkippedRepository discoveredSkippedRepository;
    /** All registered providers, keyed by Spring bean name (e.g. {@code osmClient}). */
    private final Map<String, CompanyDataSource> dataSources;

    @Transactional
    public Company discoverOrSkip(CompanyDiscoverRequest request) {
        String domain = normalizeDomain(request.domain());

        if (companyRepository.existsByDomain(domain)) {
            log.debug("Skipping duplicate domain: {}", domain);
            return companyRepository.findByDomain(domain).orElseThrow();
        }

        Company company = Company.builder()
            .domain(domain)
            .name(request.name())
            .websiteUrl(request.websiteUrl())
            .source(request.source())
            .sourceMetadata(request.sourceMetadata())
            .discoveredAt(Instant.now())
            .countryCode(request.countryCode())
            .city(request.city())
            .status(CompanyStatus.NEW)
            .build();

        return companyRepository.save(company);
    }

    /**
     * Outcome of a discovery run: the per-bucket counts plus the actual list of
     * companies newly added to {@code companies}. The pipeline orchestrator needs
     * the entities (not just a count) so it can extract/analyze/match/write on
     * each one. {@code newCompanies.size() == newWithWebsite}.
     */
    public record DiscoveryOutcome(
        int total,
        int alreadyKnown,
        int alreadySkipped,
        int newNoWebsite,
        List<Company> newCompanies
    ) {}

    @Transactional
    public DiscoveryOutcome discoverFromFilterDetailed(DiscoveryFilter filter) {
        CompanyDataSource source = resolveSource(filter);

        CompanyDataSource.DiscoveryQuery query = new CompanyDataSource.DiscoveryQuery(
            filter.getIndustry(),
            filter.getCountryCode(),
            filter.getCity(),
            firstKeyword(filter)
        );

        List<CompanyDataSource.DiscoveredPlace> places = source.search(query);
        int alreadyKnown = 0;       // already in companies
        int alreadySkipped = 0;     // already in discovered_skipped
        int newNoWebsite = 0;       // skipped now (no website)
        List<Company> newCompanies = new ArrayList<>();  // added to companies now

        for (CompanyDataSource.DiscoveredPlace place : places) {
            // Cheapest dedup first: have we already audited this exact OSM id?
            if (place.osmId() != null && discoveredSkippedRepository.existsByOsmId(place.osmId())) {
                alreadySkipped++;
                continue;
            }

            String domain = extractDomain(place.website());

            // No website means no domain to scrape emails from: keep it out of
            // the pipeline (Faz 1.5 spec), but audit it in discovered_skipped.
            if (domain == null) {
                recordSkipped(filter, place, source.sourceName());
                newNoWebsite++;
                continue;
            }

            if (companyRepository.existsByDomain(domain)) {
                alreadyKnown++;
                continue;
            }

            Company company = Company.builder()
                .domain(domain)
                .name(place.name())
                .websiteUrl(place.website())
                .source(source.sourceName())
                .sourceMetadata(buildSourceMetadata(filter, place))
                .discoveredAt(Instant.now())
                .countryCode(filter.getCountryCode())
                .city(filter.getCity())
                .status(CompanyStatus.NEW)
                .build();
            newCompanies.add(companyRepository.save(company));
        }

        // Invariant: total = alreadyKnown + alreadySkipped + newNoWebsite + newWithWebsite
        log.info("Discovery filter '{}': total={} | alreadyKnown={} alreadySkipped={} "
                + "newNoWebsite={} newWithWebsite={}",
            filter.getName(), places.size(), alreadyKnown, alreadySkipped, newNoWebsite, newCompanies.size());
        return new DiscoveryOutcome(places.size(), alreadyKnown, alreadySkipped, newNoWebsite, newCompanies);
    }

    @Transactional
    public int discoverFromFilter(DiscoveryFilter filter) {
        return discoverFromFilterDetailed(filter).newCompanies().size();
    }

    private void recordSkipped(DiscoveryFilter filter, CompanyDataSource.DiscoveredPlace place, String sourceName) {
        DiscoveredSkipped skipped = DiscoveredSkipped.builder()
            .osmId(place.osmId())
            .name(place.name())
            .skipReason("NO_WEBSITE")
            .phone(place.phone())
            .address(place.address())
            .industry(filter.getIndustry())
            .countryCode(filter.getCountryCode())
            .source(sourceName)
            .discoveredAt(Instant.now())
            .build();
        discoveredSkippedRepository.save(skipped);
    }

    /** Resolves the provider bean for a filter's {@link DiscoverySource}. */
    private CompanyDataSource resolveSource(DiscoveryFilter filter) {
        DiscoverySource type = filter.getSource() != null ? filter.getSource() : DiscoverySource.OSM;
        CompanyDataSource source = dataSources.get(type.beanName());
        if (source == null) {
            throw new BusinessException("Data source '" + type.code() + "' is not available"
                + " (bean '" + type.beanName() + "' not registered)."
                + (type == DiscoverySource.APIFY ? " Set APIFY_API_TOKEN to enable Apify." : ""));
        }
        return source;
    }

    /** First non-blank keyword of the filter, used to build a richer search string. */
    private String firstKeyword(DiscoveryFilter filter) {
        if (filter.getKeywords() == null) return null;
        return filter.getKeywords().stream()
            .filter(k -> k != null && !k.isBlank())
            .findFirst()
            .orElse(null);
    }

    private Map<String, Object> buildSourceMetadata(DiscoveryFilter filter, CompanyDataSource.DiscoveredPlace place) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("filterId", filter.getId().toString());
        if (place.address() != null) metadata.put("address", place.address());
        if (place.phone() != null) metadata.put("phone", place.phone());
        return metadata;
    }

    @Transactional
    public List<Company> importFromCsv(List<CompanyDiscoverRequest> rows) {
        return rows.stream()
            .filter(r -> !companyRepository.existsByDomain(normalizeDomain(r.domain())))
            .map(this::discoverOrSkip)
            .toList();
    }

    private String normalizeDomain(String raw) {
        if (raw == null || raw.isBlank()) throw new BusinessException("Domain cannot be blank");
        return raw.toLowerCase()
            .replaceFirst("^https?://", "")
            .replaceFirst("^www\\.", "")
            .replaceFirst("/.*$", "")
            .strip();
    }

    private String extractDomain(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null) return null;
            return host.replaceFirst("^www\\.", "");
        } catch (Exception e) {
            return null;
        }
    }
}
