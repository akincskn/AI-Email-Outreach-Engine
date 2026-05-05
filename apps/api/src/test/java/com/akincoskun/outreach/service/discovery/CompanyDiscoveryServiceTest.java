package com.akincoskun.outreach.service.discovery;

import com.akincoskun.outreach.domain.Company;
import com.akincoskun.outreach.dto.CompanyDiscoverRequest;
import com.akincoskun.outreach.integration.GoogleMapsClient;
import com.akincoskun.outreach.repository.CompanyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompanyDiscoveryServiceTest {

    @Mock CompanyRepository companyRepository;
    @Mock GoogleMapsClient googleMapsClient;
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
}
