package com.akincoskun.outreach.service.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailSendServiceTest {

    @Mock BrevoMailClient brevoMailClient;

    private MailSendService service;

    private static final String UNSUB_URL = "https://outreach.test/unsubscribe?token=abc123";
    private static final String ADDRESS = "Sarıyer, Pınar Mahallesi, Dut Sokak No:15, İstanbul, Türkiye";

    @BeforeEach
    void setUp() {
        service = new MailSendService(brevoMailClient);
        ReflectionTestUtils.setField(service, "fromName", "Akın Coşkun");
        ReflectionTestUtils.setField(service, "fromEmail", "akin@outreach.test");
        ReflectionTestUtils.setField(service, "physicalAddress", ADDRESS);
        ReflectionTestUtils.setField(service, "testRecipientOverride", "");
    }

    // --- footer composition (unit, structure-independent) ----------------------

    @Test
    void htmlFooterContainsAddressAndClickableUnsubscribeLink() {
        String footer = (String) ReflectionTestUtils.invokeMethod(
            service, "buildFooter", UNSUB_URL);

        assertThat(footer).contains("Akın Coşkun");
        assertThat(footer).contains(ADDRESS);
        assertThat(footer).contains("href=\"" + UNSUB_URL + "\"");
        assertThat(footer).contains("abone listemden çıkabilirsiniz");
    }

    @Test
    void textFooterAppendsSeparatorAddressAndUnsubscribeUrl() {
        String body = "Merhaba\n\nDört paragraf.";
        String withFooter = (String) ReflectionTestUtils.invokeMethod(
            service, "buildTextWithFooter", body, UNSUB_URL);

        assertThat(withFooter).startsWith(body);
        assertThat(withFooter).contains("\n---\n");
        assertThat(withFooter).contains("Akın Coşkun");
        assertThat(withFooter).contains(ADDRESS);
        assertThat(withFooter).contains(UNSUB_URL);
    }

    // --- end-to-end through send() (footer reaches the Brevo request) -----------

    @Test
    void sentRequestBodyContainsFooterInHtmlAndText() {
        when(brevoMailClient.send(any())).thenReturn("<brevo-msg-id>");

        service.send("info@target.com", "Subject", "<p>Merhaba</p>", "Plain body",
            "msg-id-1", UNSUB_URL, null);

        BrevoMailClient.SendRequest req = captureRequest();
        assertThat(req.toEmail()).isEqualTo("info@target.com");
        assertThat(req.subject()).isEqualTo("Subject");
        assertThat(req.htmlContent()).contains(ADDRESS).contains("href=\"" + UNSUB_URL + "\"");
        assertThat(req.textContent()).contains(ADDRESS)
            .contains("Abone olmak istemiyorsanız: " + UNSUB_URL);
    }

    @Test
    void unsubscribeHeadersAreSet() {
        when(brevoMailClient.send(any())).thenReturn("<brevo-msg-id>");

        service.send("info@target.com", "Subject", "<p>Merhaba</p>", "Plain body",
            "msg-id-1", UNSUB_URL, null);

        BrevoMailClient.SendRequest req = captureRequest();
        assertThat(req.headers()).containsKey("List-Unsubscribe");
        assertThat(req.headers().get("List-Unsubscribe")).contains(UNSUB_URL);
        assertThat(req.headers()).containsEntry("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
        assertThat(req.headers()).containsEntry("X-Mailer", "AI-Outreach-Engine/1.0");
    }

    @Test
    void testOverrideRedirectsRecipientButKeepsFooter() {
        ReflectionTestUtils.setField(service, "testRecipientOverride", "override@test.local");
        when(brevoMailClient.send(any())).thenReturn("<brevo-msg-id>");

        service.send("info@target.com", "Subject", "<p>Merhaba</p>", "Plain body",
            "msg-id-1", UNSUB_URL, null);

        BrevoMailClient.SendRequest req = captureRequest();
        assertThat(req.toEmail()).isEqualTo("override@test.local");
        assertThat(req.htmlContent()).contains(ADDRESS).contains(UNSUB_URL);
        assertThat(req.headers()).containsEntry("X-Original-Recipient", "info@target.com");
        assertThat(req.headers()).containsEntry("X-Test-Mode", "true");
    }

    @Test
    void trackingPixelAppendedWhenUrlPresent() {
        when(brevoMailClient.send(any())).thenReturn("<brevo-msg-id>");

        service.send("info@target.com", "Subject", "<p>Merhaba</p>", "Plain body",
            "msg-id-1", UNSUB_URL, "https://outreach.test/track/open?t=pix");

        BrevoMailClient.SendRequest req = captureRequest();
        assertThat(req.htmlContent()).contains("https://outreach.test/track/open?t=pix");
    }

    private BrevoMailClient.SendRequest captureRequest() {
        ArgumentCaptor<BrevoMailClient.SendRequest> captor =
            ArgumentCaptor.forClass(BrevoMailClient.SendRequest.class);
        verify(brevoMailClient).send(captor.capture());
        return captor.getValue();
    }
}
