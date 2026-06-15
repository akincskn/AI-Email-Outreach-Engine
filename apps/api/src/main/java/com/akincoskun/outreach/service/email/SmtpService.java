package com.akincoskun.outreach.service.email;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmtpService {

    private final JavaMailSender mailSender;

    @Value("${app.sender.from-name}")
    private String fromName;

    @Value("${app.sender.from-email}")
    private String fromEmail;

    @Value("${app.test.recipient-override:}")
    private String testRecipientOverride;

    public String send(String toEmail, String subject,
                       String bodyHtml, String bodyText,
                       String messageId, String unsubscribeUrl,
                       String trackingPixelUrl) {
        try {
            // SAFETY: Local/test ortamında tüm mail'leri override adresine yönlendir
            String actualRecipient = resolveRecipient(toEmail);

            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");

            helper.setFrom(fromEmail, fromName);
            helper.setTo(actualRecipient);
            helper.setSubject(subject);

            String htmlWithPixel = bodyHtml + buildTrackingPixel(trackingPixelUrl);
            helper.setText(bodyText, htmlWithPixel);

            msg.setHeader("Message-ID", "<" + messageId + ">");
            msg.setHeader("List-Unsubscribe",
                "<mailto:" + fromEmail + "?subject=unsubscribe>, <" + unsubscribeUrl + ">");
            msg.setHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
            msg.setHeader("X-Mailer", "AI-Outreach-Engine/1.0");

            // Test mode için ek header (debug için)
            if (isTestOverrideActive()) {
                msg.setHeader("X-Original-Recipient", toEmail);
                msg.setHeader("X-Test-Mode", "true");
            }

            mailSender.send(msg);

            if (isTestOverrideActive()) {
                log.warn("TEST MODE: Email redirected from={} to={} subject={}",
                    toEmail, actualRecipient, subject);
            } else {
                log.info("Sent email to={} subject={}", actualRecipient, subject);
            }

            return "OK";
        } catch (Exception e) {
            log.error("SMTP send failed to={}: {}", toEmail, e.getMessage());
            throw new SmtpException("Failed to send to " + toEmail, e);
        }
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

    private String buildTrackingPixel(String url) {
        if (url == null || url.isBlank()) return "";
        return "<img src=\"" + url + "\" width=\"1\" height=\"1\" " +
               "style=\"display:none\" alt=\"\" />";
    }
}