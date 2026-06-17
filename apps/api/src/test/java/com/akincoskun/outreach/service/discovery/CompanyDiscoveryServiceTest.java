package com.akincoskun.outreach.service.discovery;

import com.akincoskun.outreach.domain.Company;
import com.akincoskun.outreach.domain.CompanyStatus;
import com.akincoskun.outreach.domain.DiscoveryFilter;
import com.akincoskun.outreach.dto.CompanyDiscoverRequest;
import com.akincoskun.outreach.domain.DiscoveredSkipped;
import com.akincoskun.outreach.integration.CompanyDataSource;
import com.akincoskun.outreach.integration.CompanyDataSource.DiscoveredPlace;
import com.akincoskun.outreach.repository.CompanyRepository;
import com.akincoskun.outreach.repository.DiscoveredSkippedRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompanyDiscoveryServiceTest {

    @Mock CompanyRepository companyRepository;
    @Mock DiscoveredSkippedRepository discoveredSkippedRepository;
    @Mock CompanyDataSource companyDataSource;
    @InjectMocks CompanyDiscoveryService service;

    @Test
    void savesNewCompanyAndNormalizesDomain() {
        when(companyRepository.existsByDomain("example.com")).thenReturn(false);
        when(companyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CompanyDiscoverRequest req = new CompanyDiscoverRequest(
            "https://www.example.com/path", "Example Co", null, "manual_csv", null, "TR", "Istanbul"
        );

        service.discoverOrSkip(req);

        ArgumentCaptor<Company> captor = ArgumentCaptor.forClass(Company.class);
        verify(companyRepository).save(captor.capture());
        assertThat(captor.getValue().getDomain()).isEqualTo("example.com");
    }

    @Test
    void skipsDuplicateDomain() {
        Company existing = Company.builder().domain("dup.com").name("Dup").build();
        when(companyRepository.existsByDomain("dup.com")).thenReturn(true);
        when(companyRepository.findByDomain("dup.com")).thenReturn(Optional.of(existing));

        CompanyDiscoverRequest req = new CompanyDiscoverRequest(
            "dup.com", "Dup", null, "google_maps", null, null, null
        );

        Company result = service.discoverOrSkip(req);

        verify(companyRepository, never()).save(any());
        assertThat(result.getDomain()).isEqualTo("dup.com");
    }

    @Test
    void mapsDiscoveredPlaceToCompanyWithSourceAndMetadata() {
        DiscoveryFilter filter = DiscoveryFilter.builder()
            .id(UUID.randomUUID())
            .name("TR Property Management")
            .industry("property_management")
            .countryCode("TR")
            .city("İstanbul")
            .build();

        when(companyDataSource.sourceName()).thenReturn("osm");
        when(companyDataSource.search(any())).thenReturn(List.of(
            new DiscoveredPlace("node/1", "Acme Yönetim", "https://www.acme-yonetim.com", "Bağdat Cd, İstanbul", "+90 212 000 0000")
        ));
        when(discoveredSkippedRepository.existsByOsmId("node/1")).thenReturn(false);
        when(companyRepository.existsByDomain("acme-yonetim.com")).thenReturn(false);
        when(companyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        int saved = service.discoverFromFilter(filter);

        assertThat(saved).isEqualTo(1);
        ArgumentCaptor<Company> captor = ArgumentCaptor.forClass(Company.class);
        verify(companyRepository).save(captor.capture());
        Company company = captor.getValue();
        assertThat(company.getDomain()).isEqualTo("acme-yonetim.com");
        assertThat(company.getName()).isEqualTo("Acme Yönetim");
        assertThat(company.getSource()).isEqualTo("osm");
        assertThat(company.getStatus()).isEqualTo(CompanyStatus.NEW);
        assertThat(company.getCountryCode()).isEqualTo("TR");
        assertThat(company.getSourceMetadata())
            .containsEntry("filterId", filter.getId().toString())
            .containsEntry("address", "Bağdat Cd, İstanbul")
            .containsEntry("phone", "+90 212 000 0000");
    }

    @Test
    void skipPlaceWithoutWebsite_recordsInDiscoveredSkipped() {
        DiscoveryFilter filter = DiscoveryFilter.builder()
            .id(UUID.randomUUID())
            .name("TR Property Management")
            .industry("property_management")
            .countryCode("TR")
            .city("İstanbul")
            .build();

        when(companyDataSource.sourceName()).thenReturn("osm");
        when(companyDataSource.search(any())).thenReturn(List.of(
            new DiscoveredPlace("node/10", "No Site Yönetim", null, "Some Address", "+90 212 111 1111"),
            new DiscoveredPlace("way/11", "Blank Site Yönetim", "   ", null, null)
        ));
        when(discoveredSkippedRepository.existsByOsmId(any())).thenReturn(false);

        int saved = service.discoverFromFilter(filter);

        assertThat(saved).isZero();
        verify(companyRepository, never()).save(any());

        ArgumentCaptor<DiscoveredSkipped> captor = ArgumentCaptor.forClass(DiscoveredSkipped.class);
        verify(discoveredSkippedRepository, times(2)).save(captor.capture());
        DiscoveredSkipped first = captor.getAllValues().get(0);
        assertThat(first.getOsmId()).isEqualTo("node/10");
        assertThat(first.getName()).isEqualTo("No Site Yönetim");
        assertThat(first.getSkipReason()).isEqualTo("NO_WEBSITE");
        assertThat(first.getPhone()).isEqualTo("+90 212 111 1111");
        assertThat(first.getAddress()).isEqualTo("Some Address");
        assertThat(first.getIndustry()).isEqualTo("property_management");
        assertThat(first.getCountryCode()).isEqualTo("TR");
        assertThat(first.getSource()).isEqualTo("osm");
    }

    @Test
    void skipDuplicateOsmId_doesNotInsertTwice() {
        DiscoveryFilter filter = DiscoveryFilter.builder()
            .id(UUID.randomUUID())
            .name("TR Property Management")
            .industry("property_management")
            .countryCode("TR")
            .city("İstanbul")
            .build();

        when(companyDataSource.search(any())).thenReturn(List.of(
            new DiscoveredPlace("node/20", "Already Skipped Yönetim", null, null, null)
        ));
        // This OSM id was already audited on a previous run.
        when(discoveredSkippedRepository.existsByOsmId("node/20")).thenReturn(true);

        int saved = service.discoverFromFilter(filter);

        assertThat(saved).isZero();
        verify(discoveredSkippedRepository, never()).save(any());
        verify(companyRepository, never()).save(any());
        verify(companyRepository, never()).existsByDomain(any());
    }
}
