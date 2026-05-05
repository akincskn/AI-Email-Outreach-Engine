package com.akincoskun.outreach.service.discovery;

import com.akincoskun.outreach.domain.Company;
import com.akincoskun.outreach.domain.CompanyStatus;
import com.akincoskun.outreach.domain.DiscoveryFilter;
import com.akincoskun.outreach.dto.CompanyDiscoverRequest;
import com.akincoskun.outreach.exception.BusinessException;
import com.akincoskun.outreach.integration.GoogleMapsClient;
import com.akincoskun.outreach.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyDiscoveryService {

    private final CompanyRepository companyRepository;
    private final GoogleMapsClient googleMapsClient;

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

    @Transactional
    public int discoverFromFilter(DiscoveryFilter filter) {
        String query = filter.getIndustry() != null ? filter.getIndustry() : "";
        String location = Optional.ofNullable(filter.getCity())
            .or(() -> Optional.ofNullable(filter.getCountryCode()))
            .orElse("");

        List<GoogleMapsClient.PlaceResult> places = googleMapsClient.searchPlaces(query, location);
        int saved = 0;

        for (GoogleMapsClient.PlaceResult place : places) {
            String domain = extractDomain(place.website());
            if (domain == null) continue;

            if (!companyRepository.existsByDomain(domain)) {
                Company company = Company.builder()
                    .domain(domain)
                    .name(place.name())
                    .websiteUrl(place.website())
                    .source("google_maps")
                    .sourceMetadata(Map.of(
                        "filterId", filter.getId().toString(),
                        "address", Optional.ofNullable(place.formattedAddress()).orElse("")
                    ))
                    .discoveredAt(Instant.now())
                    .countryCode(filter.getCountryCode())
                    .city(filter.getCity())
                    .status(CompanyStatus.NEW)
                    .build();
                companyRepository.save(company);
                saved++;
            }
        }

        log.info("Discovery filter '{}': found {} new companies", filter.getName(), saved);
        return saved;
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
