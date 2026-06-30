package com.akincoskun.outreach.service.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Composes the outbound email (footer, headers, test-mode redirect) and hands it
 * to {@link BrevoMailClient} for delivery over HTTPS.
 *
 * <p>Formerly {@code SmtpService} (JavaMailSender). Only the transport changed in
 * the Brevo migration — footer composition, the test-recipient override, and the
 * {@code @Retryable} contract are preserved verbatim.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MailSendService {

    private final BrevoMailClient brevoMailClient;

    @Value("${app.sender.from-name}")
    private String fromName;

    @Value("${app.sender.from-email}")
    private String fromEmail;

    @Value("${app.sender.physical-address}")
    private String physicalAddress;

    @Value("${app.test.recipient-override:}")
    private String testRecipientOverride;

    // send() catches every failure and wraps it in BrevoException, so retry must
    // key on BrevoException. Transient Brevo hiccups (429, 5xx, timeout) get 3
    // attempts: immediate, +5s, +10s. A permanent failure (401 auth, bad address)
    // still retries but fails fast enough and surfaces the same error —
    // acceptable for the low send volume here.
    @Retryable(
        retryFor = BrevoException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 5000, multiplier = 2)
    )
    public String send(String toEmail, String subject,
                       String bodyHtml, String bodyText,
                       String messageId, String unsubscribeUrl,
                       String trackingPixelUrl) {
        // SAFETY: Local/test ortamında tüm mail'leri override adresine yönlendir
        String actualRecipient = resolveRecipient(toEmail);

        // KVKK + CAN-SPAM: visible footer (physical address + clickable
        // unsubscribe) is required in the body. List-Unsubscribe header
        // alone is insufficient for all clients.
        String htmlWithFooter = bodyHtml
            + buildFooter(unsubscribeUrl)
            + buildTrackingPixel(trackingPixelUrl);
        String textWithFooter = buildTextWithFooter(bodyText, unsubscribeUrl);

        // Brevo assigns its own Message-ID; we still send List-Unsubscribe /
        // List-Unsubscribe-Post so one-click unsubscribe works in Gmail/Outlook.
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("List-Unsubscribe",
            "<mailto:" + fromEmail + "?subject=unsubscribe>, <" + unsubscribeUrl + ">");
        headers.put("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
        headers.put("X-Mailer", "AI-Outreach-Engine/1.0");
        if (isTestOverrideActive()) {
            headers.put("X-Original-Recipient", toEmail);
            headers.put("X-Test-Mode", "true");
        }

        BrevoMailClient.SendRequest req = new BrevoMailClient.SendRequest(
            actualRecipient, null, subject, htmlWithFooter, textWithFooter, headers);

        // BrevoMailClient throws BrevoException on any failure; let it propagate
        // so @Retryable can act on it.
        String brevoMessageId = brevoMailClient.send(req);

        if (isTestOverrideActive()) {
            log.warn("TEST MODE: Email redirected from={} to={} subject={} brevoMessageId={}",
                toEmail, actualRecipient, subject, brevoMessageId);
        } else {
            log.info("Sent email to={} subject={} brevoMessageId={}",
                actualRecipient, subject, brevoMessageId);
        }

        return brevoMessageId != null ? brevoMessageId : "OK";
    }

    private String resolveRecipient(String originalRecipient) {
        if (isTestOverrideActive()) {
            return testRecipientOverride.trim();
        }
        return originalRecipient;
    }

    private boolean isTestOverrideActive() {
        return testRecipientOverride != null && !testRecipientOverride.isBlank();
    }

    private String buildFooter(String unsubscribeUrl) {
        return """
            <hr style="border:none;border-top:1px solid #ddd;margin:20px 0">
            <p style="font-size:12px;color:#666;line-height:1.4">
              %s<br>
              %s
            </p>
            <p style="font-size:12px;color:#666">
              Bu mailleri almak istemiyorsanız \
            <a href="%s" style="color:#666">buraya tıklayarak</a> abone listemden çıkabilirsiniz.
            </p>
            """.formatted(fromName, physicalAddress, unsubscribeUrl);
    }

    private String buildTextWithFooter(String bodyText, String unsubscribeUrl) {
        return bodyText + "\n\n---\n" + fromName + "\n" + physicalAddress
            + "\n\nAbone olmak istemiyorsanız: " + unsubscribeUrl;
    }

    private String buildTrackingPixel(String url) {
        if (url == null || url.isBlank()) return "";
        return "<img src=\"" + url + "\" width=\"1\" height=\"1\" " +
               "style=\"display:none\" alt=\"\" />";
    }
}
