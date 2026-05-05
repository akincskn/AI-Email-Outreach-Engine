package com.akincoskun.outreach.integration;

import com.akincoskun.outreach.domain.*;
import com.akincoskun.outreach.exception.BusinessException;
import com.akincoskun.outreach.repository.*;
import com.akincoskun.outreach.service.email.EmailSendOrchestrator;
import com.akincoskun.outreach.service.email.SmtpService;
import com.akincoskun.outreach.service.email.SuppressionService;
import com.akincoskun.outreach.service.email.VolumeLimiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("local")
@Tag("integration")
class PipelineIntegrationTest {

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
        registry.add("app.gmail.account-created-at",
            () -> LocalDate.now().minusWeeks(10).toString()); // 50 cap
    }

    @Autowired SuppressionService suppressionService;
    @Autowired SuppressionEntryRepository suppressionRepo;
    @Autowired VolumeLimiterService volumeLimiterService;
    @Autowired VolumeLogRepository volumeLogRepository;
    @Autowired EmailSendOrchestrator orchestrator;
    @Autowired EmailSendRepository emailSendRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired EmailAccountRepository emailAccountRepository;
    @Autowired EmailDraftRepository emailDraftRepository;

    @MockBean SmtpService smtpService;

    private Company company;
    private EmailAccount account;
    private EmailDraft draft;

    @BeforeEach
    void setUp() {
        emailDraftRepository.deleteAll();
        emailSendRepository.deleteAll();
        emailAccountRepository.deleteAll();
        companyRepository.deleteAll();
        suppressionRepo.deleteAll();
        volumeLogRepository.deleteAll();

        company = companyRepository.save(Company.builder()
            .domain("test-pipeline.com")
            .name("Pipeline Test Corp")
            .source("manual_csv")
            .discoveredAt(Instant.now())
            .status(CompanyStatus.ANALYZED)
            .build());

        account = emailAccountRepository.save(EmailAccount.builder()
            .company(company)
            .email("info@test-pipeline.com")
            .prefixType("info")
            .generic(true)
            .validFormat(true)
            .extractedAt(Instant.now())
            .build());

        draft = emailDraftRepository.save(EmailDraft.builder()
            .company(company)
            .emailAccount(account)
            .subject("Test Email Subject")
            .bodyHtml("<p>Hello</p>")
            .bodyText("Hello")
            .language("en")
            .status(DraftStatus.PENDING)
            .build());

        when(smtpService.send(any(), any(), any(), any(), any(), any(), any())).thenReturn("OK");
    }

    // ── Suppression Flow ──────────────────────────────────────────────────────

    @Test
    void suppressionBlocksSend() {
        suppressionService.suppress("info@test-pipeline.com", "test", null);

        EmailSend result = orchestrator.send(draft);

        assertThat(result.getStatus()).isEqualTo(SendStatus.SUPPRESSED);
        verify(smtpService, never()).send(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void hardBounceLeadsToSuppression() {
        suppressionService.suppress("bounce@test-pipeline.com", "hard_bounce", null);

        boolean isSuppressed = suppressionService.isSuppressed("bounce@test-pipeline.com");

        assertThat(isSuppressed).isTrue();
        assertThat(suppressionRepo.findByEmail("bounce@test-pipeline.com")).isPresent();
    }

    @Test
    void unsubscribeLeadsToSuppression() {
        suppressionService.suppress("unsubscribe@example.com", "unsubscribe", null);

        assertThat(suppressionService.isSuppressed("unsubscribe@example.com")).isTrue();
    }

    // ── Volume Limit Flow ─────────────────────────────────────────────────────

    @Test
    void volumeCapBlocksSendWhenExceeded() {
        int cap = volumeLimiterService.getDailyCap();
        assertThat(cap).isGreaterThan(0);

        VolumeLog log = volumeLogRepository.findBySentDate(LocalDate.now())
            .orElseGet(() -> volumeLogRepository.save(VolumeLog.builder()
                .sentDate(LocalDate.now())
                .sentCount(0)
                .dailyCap(cap)
                .build()));
        log.setSentCount(cap);
        volumeLogRepository.save(log);

        assertThatThrownBy(() -> orchestrator.send(draft))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Daily volume cap");
    }

    @Test
    void successfulSendRecordsVolume() {
        int before = volumeLimiterService.getSentCountToday();

        orchestrator.send(draft);

        assertThat(volumeLimiterService.getSentCountToday()).isEqualTo(before + 1);
    }

    // ── Full Pipeline (mocked AI) ─────────────────────────────────────────────

    @Test
    void fullSendFlowPersistsSentRecord() {
        EmailSend result = orchestrator.send(draft);

        assertThat(result.getStatus()).isEqualTo(SendStatus.SENT);
        assertThat(result.getSentAt()).isNotNull();
        assertThat(result.getMessageId()).isNotNull();
        assertThat(emailSendRepository.findById(result.getId())).isPresent();
    }
}
