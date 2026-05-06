package com.akincoskun.outreach.service.email;

import com.akincoskun.outreach.domain.EmailReply;
import com.akincoskun.outreach.repository.EmailReplyRepository;
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
public class ReplyTrackerService {

    private final EmailSendRepository emailSendRepository;
    private final EmailReplyRepository emailReplyRepository;
    private final SlackNotificationService slackNotificationService;

    @Value("${app.gmail.imap-host}")    private String imapHost;
    @Value("${app.gmail.imap-port}")    private int imapPort;
    @Value("${spring.mail.username}")   private String username;
    @Value("${spring.mail.password}")   private String password;

    @Scheduled(fixedDelay = 300_000)
    public void pollReplies() {
        if (username == null || username.isBlank() || username.startsWith("YOUR_")) {
            log.debug("IMAP credentials not configured, skipping reply poll");
            return;
        }

        try (Store store = connect()) {
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);

            Message[] messages = inbox.search(
                new FlagTerm(new Flags(Flags.Flag.SEEN), false));

            for (Message msg : messages) {
                processReply(msg);
                msg.setFlag(Flags.Flag.SEEN, true);
            }
            inbox.close(false);
        } catch (Exception e) {
            log.error("Reply poll failed: {}", e.getMessage());
        }
    }

    @Transactional
    public void processReply(Message msg) {
        try {
            String inReplyTo = extractInReplyTo(msg);
            if (inReplyTo == null) return;

            // Skip bounce messages (handled by BounceTrackerService)
            String subject = msg.getSubject();
            if (subject != null && (subject.toLowerCase().contains("delivery") ||
                                    subject.toLowerCase().contains("undeliverable"))) return;

            String fromEmail = msg.getFrom() != null ? msg.getFrom()[0].toString() : "unknown";
            String bodyText = getTextContent(msg);
            String companyName = null;

            var optSend = emailSendRepository.findByMessageId(inReplyTo);
            if (optSend.isPresent()) {
                companyName = optSend.get().getCompany().getName();
            }

            EmailReply reply = EmailReply.builder()
                .send(optSend.orElse(null))
                .fromEmail(fromEmail)
                .subject(subject)
                .bodyText(bodyText != null && bodyText.length() > 2000
                    ? bodyText.substring(0, 2000) : bodyText)
                .inReplyTo(inReplyTo)
                .handled(false)
                .receivedAt(Instant.now())
                .build();

            emailReplyRepository.save(reply);

            String notif = companyName != null
                ? "Reply from " + companyName + " (" + fromEmail + ")"
                : "Reply from " + fromEmail;
            slackNotificationService.sendAsync("reply", notif);

            log.info("Reply received: {}", fromEmail);

        } catch (Exception e) {
            log.warn("Could not process reply: {}", e.getMessage());
        }
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

    private String getTextContent(Message msg) {
        try {
            Object content = msg.getContent();
            if (content instanceof String s) return s;
            if (content instanceof Multipart mp) {
                for (int i = 0; i < mp.getCount(); i++) {
                    BodyPart part = mp.getBodyPart(i);
                    if (part.getContentType().startsWith("text/plain")) {
                        return part.getContent().toString();
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
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
