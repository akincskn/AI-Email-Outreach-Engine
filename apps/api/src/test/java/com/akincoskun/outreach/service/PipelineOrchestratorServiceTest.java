package com.akincoskun.outreach.service;

import com.akincoskun.outreach.domain.DiscoveryFilter;
import com.akincoskun.outreach.dto.PipelineRunResult;
import com.akincoskun.outreach.dto.RunAllResult;
import com.akincoskun.outreach.repository.DiscoveryFilterRepository;
import com.akincoskun.outreach.repository.EmailDraftRepository;
import com.akincoskun.outreach.service.agent.AnalyzerService;
import com.akincoskun.outreach.service.agent.MatcherService;
import com.akincoskun.outreach.service.agent.WriterService;
import com.akincoskun.outreach.service.discovery.CompanyDiscoveryService;
import com.akincoskun.outreach.service.discovery.EmailExtractionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PipelineOrchestratorServiceTest {

    @Mock DiscoveryFilterRepository discoveryFilterRepository;
    @Mock EmailDraftRepository emailDraftRepository;
    @Mock CompanyDiscoveryService companyDiscoveryService;
    @Mock EmailExtractionService emailExtractionService;
    @Mock AnalyzerService analyzerService;
    @Mock MatcherService matcherService;
    @Mock WriterService writerService;

    private PipelineOrchestratorService service() {
        return new PipelineOrchestratorService(
            discoveryFilterRepository, emailDraftRepository, companyDiscoveryService,
            emailExtractionService, analyzerService, matcherService, writerService);
    }

    private DiscoveryFilter filter(String name, int quota) {
        return DiscoveryFilter.builder()
            .id(UUID.randomUUID())
            .name(name)
            .dailyQuota(quota)
            .active(true)
            .build();
    }

    @Test
    void quotaAlreadyReached_skipsDiscoveryAndReportsQuotaReached() {
        DiscoveryFilter f = filter("TR Property Management", 4);
        when(discoveryFilterRepository.findById(f.getId())).thenReturn(Optional.of(f));
        // 4 drafts already created today == quota → must skip.
        when(emailDraftRepository.countByCompany_DiscoveryFilterIdAndCreatedAtBetween(
            eq(f.getId()), any(Instant.class), any(Instant.class))).thenReturn(4L);

        PipelineRunResult result = service().runForFilter(f.getId());

        assertThat(result.quotaReached()).isTrue();
        assertThat(result.draftsCreated()).isZero();
        // The expensive (paid) discovery must never run once quota is reached.
        verify(companyDiscoveryService, never()).discoverFromFilterDetailed(any());
    }

    @Test
    void runAllActive_aggregatesAndIsolatesFailures() {
        DiscoveryFilter ok = filter("TR Property Management", 4);
        DiscoveryFilter boom = filter("TR Restaurants & Cafes", 4);
        when(discoveryFilterRepository.findAllByActiveTrue()).thenReturn(List.of(ok, boom));

        // 'ok' is at quota → quotaReached result (no discovery).
        when(discoveryFilterRepository.findById(ok.getId())).thenReturn(Optional.of(ok));
        when(emailDraftRepository.countByCompany_DiscoveryFilterIdAndCreatedAtBetween(
            eq(ok.getId()), any(), any())).thenReturn(4L);

        // 'boom' throws during its run → must be captured, not propagated.
        when(discoveryFilterRepository.findById(boom.getId())).thenReturn(Optional.of(boom));
        when(emailDraftRepository.countByCompany_DiscoveryFilterIdAndCreatedAtBetween(
            eq(boom.getId()), any(), any())).thenReturn(0L);
        when(companyDiscoveryService.discoverFromFilterDetailed(boom))
            .thenThrow(new RuntimeException("OSM down"));

        RunAllResult summary = service().runAllActive();

        assertThat(summary.totalFilters()).isEqualTo(2);
        assertThat(summary.totalQuotaReached()).isEqualTo(1);
        assertThat(summary.totalErrors()).isEqualTo(1);
        assertThat(summary.perFilter()).hasSize(2);
        assertThat(summary.perFilter())
            .anySatisfy(r -> assertThat(r.error()).isEqualTo("OSM down"));
    }
}
