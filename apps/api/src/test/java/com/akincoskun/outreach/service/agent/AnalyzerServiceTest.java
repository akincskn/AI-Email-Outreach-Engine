package com.akincoskun.outreach.service.agent;

import com.akincoskun.outreach.domain.Company;
import com.akincoskun.outreach.domain.CompanyStatus;
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
class AnalyzerServiceTest {

    @Mock LlmRouter llmRouter;
    @Mock CompanyRepository companyRepository;
    @Spy  ObjectMapper objectMapper;
    @InjectMocks AnalyzerService service;

    private Company company() {
        return Company.builder()
            .domain("test.com")
            .name("Test Co")
            .source("manual_csv")
            .status(CompanyStatus.NEW)
            .build();
    }

    @Test
    void setsStatusToAnalyzedForTargetCountry() {
        String json = """
            {
              "industry": "restaurant",
              "sub_industry": "italian_food",
              "size_estimate": "small",
              "country_hint": "TR",
              "primary_language": "tr",
              "tech_stack_hints": [],
              "potential_problems": ["no chatbot"],
              "target_audience": "consumers",
              "online_presence_score": 0.5,
              "is_target_country": true,
              "skip_reason": null
            }""";

        when(llmRouter.complete(eq("analyzer"), anyString(), anyString(), anyString(), any()))
            .thenReturn(json);
        when(companyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Company c = company();
        Map<String, Object> result = service.analyze(c);

        assertThat(c.getStatus()).isEqualTo(CompanyStatus.ANALYZED);
        assertThat(result.get("industry")).isEqualTo("restaurant");
    }

    @Test
    void blacklistsNonTargetCountry() {
        String json = """
            {
              "industry": "saas",
              "sub_industry": "b2b",
              "size_estimate": "medium",
              "country_hint": "DE",
              "primary_language": "en",
              "tech_stack_hints": [],
              "potential_problems": [],
              "target_audience": "businesses",
              "online_presence_score": 0.8,
              "is_target_country": false,
              "skip_reason": null
            }""";

        when(llmRouter.complete(any(), anyString(), anyString(), anyString(), any()))
            .thenReturn(json);
        when(companyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Company c = company();
        service.analyze(c);

        assertThat(c.getStatus()).isEqualTo(CompanyStatus.BLACKLISTED);
        assertThat(c.getStatusReason()).isEqualTo("not_target_country");
    }

    @Test
    void blacklistsGovernmentEntity() {
        String json = """
            {
              "industry": "other",
              "sub_industry": "government",
              "size_estimate": "enterprise",
              "country_hint": "TR",
              "primary_language": "tr",
              "tech_stack_hints": [],
              "potential_problems": [],
              "target_audience": "consumers",
              "online_presence_score": 1.0,
              "is_target_country": true,
              "skip_reason": "government_entity"
            }""";

        when(llmRouter.complete(any(), anyString(), anyString(), anyString(), any()))
            .thenReturn(json);
        when(companyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Company c = company();
        service.analyze(c);

        assertThat(c.getStatus()).isEqualTo(CompanyStatus.BLACKLISTED);
        assertThat(c.getStatusReason()).isEqualTo("government_entity");
    }
}
