package com.akincoskun.outreach.service.email;

import com.akincoskun.outreach.domain.*;
import com.akincoskun.outreach.exception.BusinessException;
import com.akincoskun.outreach.repository.EmailSendRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailSendOrchestratorTest {

    @Mock SuppressionService suppressionService;
    @Mock VolumeLimiterService volumeLimiter;
    @Mock SmtpService smtpService;
    @Mock HmacTokenService hmacTokenService;
    @Mock EmailSendRepository emailSendRepository;
    @InjectMocks EmailSendOrchestrator orchestrator;

    private EmailDraft draft;
    private Company company;
    private EmailAccount emailAccount;

    @BeforeEach
    void setUp() {
        company = new Company();
        ReflectionTestUtils.setField(company, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(company, "name", "Test Corp");
        ReflectionTestUtils.setField(company, "domain", "testcorp.com");

        emailAccount = new EmailAccount();
        ReflectionTestUtils.setField(emailAccount, "email", "info@testcorp.com");

        draft = new EmailDraft();
        ReflectionTestUtils.setField(draft, "company", company);
        ReflectionTestUtils.setField(draft, "emailAccount", emailAccount);
        ReflectionTestUtils.setField(draft, "subject", "Hello from Akın");
        ReflectionTestUtils.setField(draft, "bodyHtml", "<p>Hello</p>");
        ReflectionTestUtils.setField(draft, "bodyText", "Hello");

        when(hmacTokenService.generateToken(anyString())).thenReturn("test-token");
        when(emailSendRepository.save(any())).thenAnswer(inv -> {
            EmailSend s = inv.getArgument(0);
            if (s.getId() == null) ReflectionTestUtils.setField(s, "id", UUID.randomUUID());
            return s;
        });
    }

    @Test
    void suppressedEmailCreatesSupressedSend() {
        when(suppressionService.isSuppressed("info@testcorp.com")).thenReturn(true);

        EmailSend result = orchestrator.send(draft);

        assertThat(result.getStatus()).isEqualTo(SendStatus.SUPPRESSED);
        verify(smtpService, never()).send(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void volumeCapExceededThrowsBusinessException() {
        when(suppressionService.isSuppressed(anyString())).thenReturn(false);
        when(volumeLimiter.canSendNow()).thenReturn(false);
        when(volumeLimiter.getSentCountToday()).thenReturn(5);
        when(volumeLimiter.getDailyCap()).thenReturn(5);

        assertThatThrownBy(() -> orchestrator.send(draft))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("Daily volume cap");
    }

    @Test
    void successfulSendUpdatesSentStatus() {
        when(suppressionService.isSuppressed(anyString())).thenReturn(false);
        when(volumeLimiter.canSendNow()).thenReturn(true);
        when(smtpService.send(any(), any(), any(), any(), any(), any(), any())).thenReturn("OK");

        EmailSend result = orchestrator.send(draft);

        assertThat(result.getStatus()).isEqualTo(SendStatus.SENT);
        assertThat(result.getSentAt()).isNotNull();
        verify(volumeLimiter).recordSend();
    }

    @Test
    void smtpFailureSetsFailedStatus() {
        when(suppressionService.isSuppressed(anyString())).thenReturn(false);
        when(volumeLimiter.canSendNow()).thenReturn(true);
        when(smtpService.send(any(), any(), any(), any(), any(), any(), any()))
            .thenThrow(new SmtpException("Connection refused", new RuntimeException()));

        EmailSend result = orchestrator.send(draft);

        assertThat(result.getStatus()).isEqualTo(SendStatus.FAILED);
        assertThat(result.getErrorMessage()).contains("Connection refused");
        verify(volumeLimiter, never()).recordSend();
    }
}
