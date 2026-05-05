package com.akincoskun.outreach.service.email;

import com.akincoskun.outreach.domain.EmailBounce;
import com.akincoskun.outreach.domain.SendStatus;
import com.akincoskun.outreach.repository.EmailBounceRepository;
import com.akincoskun.outreach.repository.EmailSendRepository;
import jakarta.mail.*;
import jakarta.mail.search.FlagTerm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Properties;

@Service
@RequiredArgsConstructor
@Slf4j
public class BounceTrackerService {

    private final EmailSendRepository emailSendRepository;
    private final EmailBounceRepository emailBounceRepository;
    private final SuppressionService suppressionService;

    @Value("${app.gmail.imap-host}")    private String imapHost;
    @Value("${app.gmail.imap-port}")    private int imapPort;
    @Value("${spring.mail.username}")   private String username;
    @Value("${spring.mail.password}")   private String password;

    @Scheduled(fixedDelay = 300_000) // every 5 minutes
    public void pollBounces() {
        if (username == null || username.isBlank() || username.startsWith("YOUR_")) {
            log.debug("IMAP credentials not configured, skipping bounce poll");
            return;
        }

        try (Store store = connect()) {
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);

            Message[] messages = inbox.search(
                new FlagTerm(new Flags(Flags.Flag.SEEN), false));

            for (Message msg : messages) {
                processMessage(msg);
                msg.setFlag(Flags.Flag.SEEN, true);
            }

            inbox.close(false);
        } catch (Exception e) {
            log.error("Bounce poll failed: {}", e.getMessage());
        }
    }

    @Transactional
    public void processMessage(Message msg) {
        try {
            String subject = msg.getSubject();
            String from = msg.getFrom() != null ? msg.getFrom()[0].toString() : "";

            if (!isBounceMessage(subject, from)) return;

            String content = getContent(msg);
            String bounceType = detectBounceType(content);
            String bounceCode = extractBounceCode(content);
            String originalMessageId = extractInReplyTo(msg);

            emailSendRepository.findByMessageId(originalMessageId).ifPresent(send -> {
                EmailBounce bounce = EmailBounce.builder()
                    .send(send)
                    .bounceType(bounceType)
                    .bounceReason(content.length() > 500 ? content.substring(0, 500) : content)
                    .bounceCode(bounceCode)
                    .detectedAt(Instant.now())
                    .build();
                emailBounceRepository.save(bounce);

                send.setStatus(SendStatus.BOUNCED);
                emailSendRepository.save(send);

                if ("hard".equals(bounceType)) {
                    suppressionService.suppress(send.getToEmail(), "hard_bounce", send);
                    log.info("Hard bounce → suppressed: {}", send.getToEmail());
                }
            });
        } catch (Exception e) {
            log.warn("Could not process bounce message: {}", e.getMessage());
        }
    }

    private boolean isBounceMessage(String subject, String from) {
        if (subject == null) return false;
        String s = subject.toLowerCase();
        return s.contains("delivery") || s.contains("undeliverable") ||
               s.contains("failed") || from.toLowerCase().contains("mailer-daemon");
    }

    private String detectBounceType(String content) {
        if (content.contains("5.1.1") || content.contains("5.1.10") ||
            content.contains("NoSuchUser") || content.contains("does not exist")) {
            return "hard";
        }
        return "soft";
    }

    private String extractBounceCode(String content) {
        java.util.regex.Matcher m =
            java.util.regex.Pattern.compile("\\b(5\\.\\d\\.\\d{1,3}|4\\.\\d\\.\\d{1,3})\\b")
                .matcher(content);
        return m.find() ? m.group() : null;
    }

    private String extractInReplyTo(Message msg) {
        try {
            String[] headers = msg.getHeader("In-Reply-To");
            if (headers != null && headers.length > 0) {
                return headers[0].replaceAll("[<>]", "").trim();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String getContent(Message msg) {
        try {
            Object content = msg.getContent();
            return content != null ? content.toString() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private Store connect() throws MessagingException {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", imapHost);
        props.put("mail.imaps.port", String.valueOf(imapPort));
        props.put("mail.imaps.ssl.enable", "true");

        Session session = Session.getInstance(props);
        Store store = session.getStore("imaps");
        store.connect(imapHost, username, password);
        return store;
    }
}
