package com.akincoskun.outreach.service.email;

import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmtpServiceTest {

    @Mock JavaMailSender mailSender;

    private SmtpService service;

    private static final String UNSUB_URL = "https://outreach.test/unsubscribe?token=abc123";
    private static final String ADDRESS = "Sarıyer, Pınar Mahallesi, Dut Sokak No:15, İstanbul, Türkiye";

    @BeforeEach
    void setUp() {
        service = new SmtpService(mailSender);
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

    // --- end-to-end through send() (footer reaches the MimeMessage) -------------

    @Test
    void sentMessageBodyContainsFooter() throws Exception {
        when(mailSender.createMimeMessage())
            .thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        service.send("info@target.com", "Subject", "<p>Merhaba</p>", "Plain body",
            "msg-id-1", UNSUB_URL, null);

        List<String> parts = capturedStringParts();
        assertThat(parts).anySatisfy(p ->
            assertThat(p).contains(ADDRESS).contains("href=\"" + UNSUB_URL + "\""));
        assertThat(parts).anySatisfy(p ->
            assertThat(p).contains(ADDRESS).contains("Abone olmak istemiyorsanız: " + UNSUB_URL));
    }

    @Test
    void footerStillAppendedWhenTestOverrideActive() throws Exception {
        ReflectionTestUtils.setField(service, "testRecipientOverride", "override@test.local");
        when(mailSender.createMimeMessage())
            .thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        service.send("info@target.com", "Subject", "<p>Merhaba</p>", "Plain body",
            "msg-id-1", UNSUB_URL, null);

        List<String> parts = capturedStringParts();
        assertThat(parts).anyMatch(p -> p.contains(ADDRESS) && p.contains(UNSUB_URL));
    }

    /** Recursively collect every text body part from the sent MimeMessage. */
    private List<String> capturedStringParts() throws Exception {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        List<String> out = new ArrayList<>();
        collect(captor.getValue(), out);
        return out;
    }

    private void collect(Part part, List<String> out) throws Exception {
        Object content = part.getContent();
        if (content instanceof String s) {
            out.add(s);
        } else if (content instanceof MimeMultipart mp) {
            for (int i = 0; i < mp.getCount(); i++) {
                collect(mp.getBodyPart(i), out);
            }
        }
    }
}
