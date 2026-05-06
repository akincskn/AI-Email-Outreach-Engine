package com.akincoskun.outreach.service.email;

import com.akincoskun.outreach.domain.*;
import com.akincoskun.outreach.exception.BusinessException;
import com.akincoskun.outreach.repository.EmailSendRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailSendOrchestrator {

    private final SuppressionService suppressionService;
    private final VolumeLimiterService volumeLimiter;
    private final SmtpService smtpService;
    private final HmacTokenService hmacTokenService;
    private final EmailSendRepository emailSendRepository;

    @Value("${server.port:8080}")
    private String serverPort;

    @Transactional
    public EmailSend send(EmailDraft draft) {
        String toEmail = draft.getEmailAccount().getEmail();

        // 1. Suppression check — bypass not possible
        if (suppressionService.isSuppressed(toEmail)) {
            log.warn("Send blocked by suppression list: {}", toEmail);
            EmailSend send = buildSend(draft, toEmail);
            send.setStatus(SendStatus.SUPPRESSED);
            return emailSendRepository.save(send);
        }

        // 2. Volume limit check
        if (!volumeLimiter.canSendNow()) {
            throw new BusinessException("Daily volume cap reached (" +
                volumeLimiter.getSentCountToday() + "/" + volumeLimiter.getDailyCap() + ")");
        }

        String messageId = UUID.randomUUID() + "@outreach.local";
        String pixelToken = hmacTokenService.generateToken("pixel");
        String unsubToken = hmacTokenService.generateToken(toEmail);

        String pixelUrl   = "/api/v1/track/open?t=" + pixelToken;
        String unsubUrl   = "/unsubscribe?token=" + unsubToken;

        EmailSend send = buildSend(draft, toEmail);
        send.setMessageId(messageId);
        send.setTrackingPixelToken(pixelToken);
        send.setUnsubscribeToken(unsubToken);
        send.setStatus(SendStatus.SENDING);
        emailSendRepository.save(send);

        try {
            // 3. SMTP send — replace PLACEHOLDER set by WriterService.injectFooter()
            String subject = draft.getEditedSubject() != null ? draft.getEditedSubject() : draft.getSubject();
            String bodyHtml = (draft.getEditedBodyHtml() != null ? draft.getEditedBodyHtml() : draft.getBodyHtml())
                .replace("/unsubscribe?token=PLACEHOLDER", unsubUrl);
            String bodyText = (draft.getEditedBodyText() != null ? draft.getEditedBodyText() : draft.getBodyText())
                .replace("/unsubscribe?token=PLACEHOLDER", unsubUrl);

            smtpService.send(toEmail, subject, bodyHtml, bodyText, messageId, unsubUrl, pixelUrl);

            // 4. Record volume
            volumeLimiter.recordSend();

            // 5. Update status
            send.setStatus(SendStatus.SENT);
            send.setSentAt(Instant.now());

        } catch (SmtpException e) {
            send.setStatus(SendStatus.FAILED);
            send.setErrorMessage(e.getMessage());
            send.setFailedAt(Instant.now());
        }

        return emailSendRepository.save(send);
    }

    private EmailSend buildSend(EmailDraft draft, String toEmail) {
        return EmailSend.builder()
            .draft(draft)
            .company(draft.getCompany())
            .toEmail(toEmail)
            .fromEmail("")
            .subject(draft.getSubject())
            .status(SendStatus.QUEUED)
            .retryCount(0)
            .queuedAt(Instant.now())
            .build();
    }
}
