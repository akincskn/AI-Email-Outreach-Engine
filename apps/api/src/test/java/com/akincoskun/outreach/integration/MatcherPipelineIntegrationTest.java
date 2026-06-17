package com.akincoskun.outreach.integration;

import com.akincoskun.outreach.domain.*;
import com.akincoskun.outreach.dto.MatchResult;
import com.akincoskun.outreach.repository.CompanyRepository;
import com.akincoskun.outreach.repository.EmailAccountRepository;
import com.akincoskun.outreach.repository.EmailDraftRepository;
import com.akincoskun.outreach.service.agent.AnalyzerService;
import com.akincoskun.outreach.service.agent.MatcherService;
import com.akincoskun.outreach.service.agent.WriterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the Analyzer → Matcher slice against a real Postgres (Flyway
 * migrations incl. V20/V21) with the local profile's mock LLM. Verifies the
 * Matcher persists its result and transitions company status.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("local")
@Tag("integration")
class MatcherPipelineIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("outreach_test")
            .withUsername("outreach")
            .withPassword("outreach");

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired AnalyzerService analyzerService;
    @Autowired MatcherService matcherService;
    @Autowired WriterService writerService;
    @Autowired CompanyRepository companyRepository;
    @Autowired EmailAccountRepository emailAccountRepository;
    @Autowired EmailDraftRepository emailDraftRepository;

    private Company company;

    @BeforeEach
    void setUp() {
        emailDraftRepository.deleteAll();
        emailAccountRepository.deleteAll();
        companyRepository.deleteAll();
        company = companyRepository.save(Company.builder()
            .domain("acme-yonetim.com")
            .name("Acme Apartman Yönetimi")
            .source("osm")
            .countryCode("TR")
            .discoveredAt(Instant.now())
            .status(CompanyStatus.NEW)
            .build());
    }

    @Test
    void analyzerThenMatcher_setsMatchedAndPersistsMatch() {
        analyzerService.analyze(company);
        assertThat(company.getStatus()).isEqualTo(CompanyStatus.ANALYZED);

        MatchResult result = matcherService.match(company, "kolayaidat");

        assertThat(result.matched()).isTrue();
        assertThat(result.primaryProductSlug()).isEqualTo("kolayaidat");

        Company reloaded = companyRepository.findById(company.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(CompanyStatus.MATCHED);
        @SuppressWarnings("unchecked")
        Map<String, Object> match = (Map<String, Object>) reloaded.getAnalysis().get("match");
        assertThat(match).containsEntry("primary_product_slug", "kolayaidat");
    }

    @Test
    void fullSlice_analyzeMatchWrite_producesKolayAidatFocusedDraft() {
        analyzerService.analyze(company);
        matcherService.match(company, "kolayaidat");

        EmailAccount account = emailAccountRepository.save(EmailAccount.builder()
            .company(company)
            .email("info@acme-yonetim.com")
            .prefixType("info")
            .generic(true)
            .validFormat(true)
            .extractedAt(Instant.now())
            .build());

        EmailDraft draft = writerService.write(company, account);

        assertThat(draft.getPromptVersion()).isEqualTo("writer_v2");
        assertThat(draft.getMatchedProductSlug()).isEqualTo("kolayaidat");
        assertThat(draft.getMatchConfidence()).isNotNull();
        // The matched product must be the one in focus.
        assertThat(draft.getBodyHtml())
            .contains("KolayAidat")
            .contains("kolayaidat.vercel.app");
        // No placeholder leakage after footer injection.
        assertThat(draft.getBodyHtml()).doesNotContain("{{");
    }
}
