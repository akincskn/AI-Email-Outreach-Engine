package com.akincoskun.outreach.service.agent;

import com.akincoskun.outreach.domain.Company;
import com.akincoskun.outreach.domain.CompanyStatus;
import com.akincoskun.outreach.dto.MatchResult;
import com.akincoskun.outreach.integration.LlmRouter;
import com.akincoskun.outreach.repository.CompanyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MatcherServiceTest {

    @Mock LlmRouter llmRouter;
    @Mock CompanyRepository companyRepository;
    @Spy  ObjectMapper objectMapper;
    @InjectMocks MatcherService service;

    private Company propertyMgmtCompany() {
        return Company.builder()
            .domain("acme-yonetim.com")
            .name("Acme Apartman Yönetimi")
            .source("osm")
            .countryCode("TR")
            .status(CompanyStatus.ANALYZED)
            .analysis(new java.util.HashMap<>(Map.of(
                "industry", "real_estate",
                "sub_industry", "property_management",
                "country_hint", "TR",
                "target_audience", "businesses",
                "potential_problems", java.util.List.of("Manual aidat takibi")
            )))
            .build();
    }

    @Test
    void matchAboveThreshold_setsMatchedAndPersistsMatchOnAnalysis() {
        String json = """
            {
              "primary_product_slug": "kolayaidat",
              "primary_confidence": 0.92,
              "secondary_product_slug": "cerezmatik",
              "secondary_confidence": 0.55,
              "reasoning": "Property management firm in Turkey."
            }""";
        when(llmRouter.complete(eq("matcher"), anyString(), anyString(), anyString(), any()))
            .thenReturn(json);
        when(companyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Company c = propertyMgmtCompany();
        MatchResult result = service.match(c, "kolayaidat");

        assertThat(result.matched()).isTrue();
        assertThat(result.primaryProductSlug()).isEqualTo("kolayaidat");
        assertThat(result.primaryConfidence()).isEqualTo(0.92);
        assertThat(result.secondaryProductSlug()).isEqualTo("cerezmatik");
        assertThat(c.getStatus()).isEqualTo(CompanyStatus.MATCHED);

        @SuppressWarnings("unchecked")
        Map<String, Object> match = (Map<String, Object>) c.getAnalysis().get("match");
        assertThat(match).isNotNull();
        assertThat(match.get("primary_product_slug")).isEqualTo("kolayaidat");
        assertThat(match.get("reasoning")).isEqualTo("Property management firm in Turkey.");
    }

    @Test
    void matchBelowThreshold_skipsCompany() {
        String json = """
            {
              "primary_product_slug": "none",
              "primary_confidence": 0.2,
              "secondary_product_slug": null,
              "secondary_confidence": null,
              "reasoning": "No product fits a UK consultancy."
            }""";
        when(llmRouter.complete(any(), anyString(), anyString(), anyString(), any()))
            .thenReturn(json);
        when(companyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Company c = propertyMgmtCompany();
        MatchResult result = service.match(c, "kolayaidat");

        assertThat(result.matched()).isFalse();
        assertThat(c.getStatus()).isEqualTo(CompanyStatus.SKIPPED);
        assertThat(c.getStatusReason())
            .startsWith("MATCH_BELOW_THRESHOLD:")
            .contains("confidence 0.20")
            .contains("below 0.60");
        assertThat(c.getAnalysis()).doesNotContainKey("match");
    }

    @Test
    void unknownPrimarySlugAboveThreshold_isTreatedAsNoMatch() {
        // AI hallucinated a non-existent product — must not be accepted.
        String json = """
            {
              "primary_product_slug": "magic-crm",
              "primary_confidence": 0.95,
              "secondary_product_slug": null,
              "secondary_confidence": null,
              "reasoning": "hallucinated"
            }""";
        when(llmRouter.complete(any(), anyString(), anyString(), anyString(), any()))
            .thenReturn(json);
        when(companyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Company c = propertyMgmtCompany();
        MatchResult result = service.match(c, null);

        assertThat(result.matched()).isFalse();
        assertThat(c.getStatus()).isEqualTo(CompanyStatus.SKIPPED);
    }

    @Test
    void unknownSecondarySlug_isDropped() {
        String json = """
            {
              "primary_product_slug": "kolayaidat",
              "primary_confidence": 0.8,
              "secondary_product_slug": "not-a-real-product",
              "secondary_confidence": 0.5,
              "reasoning": "ok"
            }""";
        when(llmRouter.complete(any(), anyString(), anyString(), anyString(), any()))
            .thenReturn(json);
        when(companyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MatchResult result = service.match(propertyMgmtCompany(), null);

        assertThat(result.matched()).isTrue();
        assertThat(result.secondaryProductSlug()).isNull();
        assertThat(result.secondaryConfidence()).isNull();
    }
}
