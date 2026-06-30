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
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailSendOrchestratorTest {

    @Mock SuppressionService suppressionService;
    @Mock VolumeLimiterService volumeLimiter;
    @Mock MailSendService mailSendService;
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
        verify(mailSendService, never()).send(any(), any(), any(), any(), any(), any(), any());
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
        when(mailSendService.send(any(), any(), any(), any(), any(), any(), any())).thenReturn("OK");

        EmailSend result = orchestrator.send(draft);

        assertThat(result.getStatus()).isEqualTo(SendStatus.SENT);
        assertThat(result.getSentAt()).isNotNull();
        verify(volumeLimiter).recordSend();
    }

    @Test
    void unsubscribePlaceholderIsReplacedWithRealToken() {
        String draftHtml = "<p>Hello</p><a href=\"/unsubscribe?token=PLACEHOLDER\">Unsubscribe</a>";
        String draftText = "Hello\nUnsubscribe: /unsubscribe?token=PLACEHOLDER";
        ReflectionTestUtils.setField(draft, "bodyHtml", draftHtml);
        ReflectionTestUtils.setField(draft, "bodyText", draftText);

        when(suppressionService.isSuppressed(anyString())).thenReturn(false);
        when(volumeLimiter.canSendNow()).thenReturn(true);
        when(hmacTokenService.generateToken("info@testcorp.com")).thenReturn("real-unsub-token");
        when(hmacTokenService.generateToken("pixel")).thenReturn("real-pixel-token");
        when(mailSendService.send(any(), any(), any(), any(), any(), any(), any())).thenReturn("OK");

        orchestrator.send(draft);

        ArgumentCaptor<String> htmlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailSendService).send(any(), any(), htmlCaptor.capture(), textCaptor.capture(), any(), any(), any());

        assertThat(htmlCaptor.getValue()).doesNotContain("PLACEHOLDER");
        assertThat(htmlCaptor.getValue()).contains("real-unsub-token");
        assertThat(textCaptor.getValue()).doesNotContain("PLACEHOLDER");
        assertThat(textCaptor.getValue()).contains("real-unsub-token");
    }

    @Test
    void normalizeBaseUrlStripsStrayKeyPrefixAndTrailingSlash() {
        ReflectionTestUtils.setField(orchestrator, "baseUrl",
            "BASE_URL=https://ai-email-outreach-engine.onrender.com/");
        ReflectionTestUtils.invokeMethod(orchestrator, "normalizeBaseUrl");

        assertThat((String) ReflectionTestUtils.getField(orchestrator, "baseUrl"))
            .isEqualTo("https://ai-email-outreach-engine.onrender.com");
    }

    @Test
    void normalizeBaseUrlLeavesCleanValueUnchanged() {
        ReflectionTestUtils.setField(orchestrator, "baseUrl", "https://outreach.test");
        ReflectionTestUtils.invokeMethod(orchestrator, "normalizeBaseUrl");

        assertThat((String) ReflectionTestUtils.getField(orchestrator, "baseUrl"))
            .isEqualTo("https://outreach.test");
    }

    @Test
    void smtpFailureSetsFailedStatus() {
        when(suppressionService.isSuppressed(anyString())).thenReturn(false);
        when(volumeLimiter.canSendNow()).thenReturn(true);
        when(mailSendService.send(any(), any(), any(), any(), any(), any(), any()))
            .thenThrow(new BrevoException("Connection refused", new RuntimeException()));

        EmailSend result = orchestrator.send(draft);

        assertThat(result.getStatus()).isEqualTo(SendStatus.FAILED);
        assertThat(result.getErrorMessage()).contains("Connection refused");
        verify(volumeLimiter, never()).recordSend();
    }
}
