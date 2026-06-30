package com.akincoskun.outreach.service.agent;

import com.akincoskun.outreach.domain.*;
import com.akincoskun.outreach.integration.LlmRouter;
import com.akincoskun.outreach.repository.EmailDraftRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WriterServiceTest {

    @Mock LlmRouter llmRouter;
    @Mock EmailDraftRepository emailDraftRepository;
    @Spy  ObjectMapper objectMapper;
    @InjectMocks WriterService service;

    @BeforeEach
    void inject() {
        ReflectionTestUtils.setField(service, "senderName", "Akın Coşkun");
        ReflectionTestUtils.setField(service, "portfolioUrl", "https://akin-coskun.web.app");
        ReflectionTestUtils.setField(service, "githubUrl", "https://github.com/akincskn");
    }

    private Company company() {
        return Company.builder()
            .domain("mariospizza.com.tr")
            .name("Mario's Pizza")
            .source("google_maps")
            .status(CompanyStatus.ANALYZED)
            .analysis(Map.of(
                "industry", "restaurant",
                "sub_industry", "italian_food",
                "size_estimate", "small",
                "country_hint", "TR",
                "primary_language", "tr",
                "target_audience", "consumers",
                "potential_problems", java.util.List.of("no chatbot")
            ))
            .build();
    }

    private EmailAccount emailAccount(Company c) {
        return EmailAccount.builder()
            .company(c)
            .email("info@mariospizza.com.tr")
            .prefixType("info")
            .extractedAt(java.time.Instant.now())
            .build();
    }

    // Writer no longer emits any footer (signature/address/unsubscribe) — that is
    // appended by MailSendService. The body is pure content.
    private String validDraftJson(String lang) {
        return """
            {
              "subject": "Restoranlar için ücretsiz AI araçları",
              "body_html": "<p>Merhaba Mario ekibi, sitenizi inceledim.</p><p>Ben Akın Coşkun, ücretsiz araçlar geliştiriyorum.</p><p>İhtiyacınıza uyuyor mu?</p>",
              "body_text": "Merhaba Mario ekibi, sitenizi inceledim.\\n\\nBen Akın Coşkun, ücretsiz araçlar geliştiriyorum.\\n\\nİhtiyacınıza uyuyor mu?",
              "language": "%s",
              "personalization_signals": ["industry_mentioned", "language_match"],
              "highlighted_products": ["ai-chatbot-platform"],
              "warnings": []
            }""".formatted(lang);
    }

    @Test
    void createsDraftWithPendingStatusForValidOutput() {
        Company c = company();
        EmailAccount a = emailAccount(c);
        when(llmRouter.complete(eq("writer"), anyString(), anyString(), anyString(), any()))
            .thenReturn(validDraftJson("tr"));
        when(emailDraftRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EmailDraft draft = service.write(c, a);

        assertThat(draft.getStatus()).isEqualTo(DraftStatus.PENDING);
        assertThat(draft.getLanguage()).isEqualTo("tr");
        // Writer body is pure content — no footer markers, no leaked placeholders.
        assertThat(draft.getBodyHtml()).doesNotContain("{{");
        assertThat(draft.getBodyHtml()).doesNotContain("<hr");
        assertThat(draft.getBodyHtml()).doesNotContain("unsubscribe");
    }

    private Company propertyMgmtCompanyWithMatch() {
        return Company.builder()
            .domain("acme-yonetim.com")
            .name("Acme Apartman Yönetimi")
            .source("osm")
            .countryCode("TR")
            .status(CompanyStatus.MATCHED)
            .analysis(new java.util.HashMap<>(Map.of(
                "industry", "real_estate",
                "sub_industry", "property_management",
                "primary_language", "tr",
                "country_hint", "TR",
                "target_audience", "businesses",
                "potential_problems", java.util.List.of("Manual aidat takibi"),
                "match", new java.util.HashMap<>(Map.of(
                    "primary_product_slug", "kolayaidat",
                    "primary_confidence", 0.92,
                    "secondary_product_slug", "cerezmatik",
                    "secondary_confidence", 0.55,
                    "reasoning", "Property management firm in Turkey."
                ))
            )))
            .build();
    }

    private EmailAccount yonetimAccount(Company c) {
        return EmailAccount.builder()
            .company(c)
            .email("info@acme-yonetim.com")
            .prefixType("info")
            .extractedAt(java.time.Instant.now())
            .build();
    }

    @Test
    void usesWriterV2AndPopulatesMatchColumnsWhenCompanyHasMatch() {
        Company c = propertyMgmtCompanyWithMatch();
        EmailAccount a = yonetimAccount(c);
        // The matched-product user prompt must carry the primary product details.
        when(llmRouter.complete(eq("writer"), eq("writer_v2"), contains("KolayAidat"), anyString(), any()))
            .thenReturn(validDraftJson("tr"));
        when(emailDraftRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EmailDraft draft = service.write(c, a);

        assertThat(draft.getStatus()).isEqualTo(DraftStatus.PENDING);
        assertThat(draft.getPromptVersion()).isEqualTo("writer_v2");
        assertThat(draft.getMatchedProductSlug()).isEqualTo("kolayaidat");
        assertThat(draft.getSecondaryProductSlug()).isEqualTo("cerezmatik");
        assertThat(draft.getMatchConfidence()).isEqualByComparingTo("0.92");
        assertThat(draft.getMatchReasoning()).isEqualTo("Property management firm in Turkey.");
    }

    @Test
    void fallsBackToWriterV1WhenNoMatchPresent() {
        Company c = company(); // analysis has no "match" key
        EmailAccount a = emailAccount(c);
        when(llmRouter.complete(eq("writer"), eq("writer_v1"), anyString(), anyString(), any()))
            .thenReturn(validDraftJson("tr"));
        when(emailDraftRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EmailDraft draft = service.write(c, a);

        assertThat(draft.getStatus()).isEqualTo(DraftStatus.PENDING);
        assertThat(draft.getPromptVersion()).isEqualTo("writer_v1");
        assertThat(draft.getMatchedProductSlug()).isNull();
    }

    @Test
    void convertsMarkdownLinksInHtmlToAnchors() {
        Company c = company();
        EmailAccount a = emailAccount(c);
        // Writer emitted a markdown link in body_html — must become a real anchor,
        // with the scheme stripped from the visible text by cleanAnchorText.
        String json = """
            {
              "subject": "Restoranlar için ücretsiz AI araçları",
              "body_html": "<p>Bkz [AI Chatbot Platform](https://chatbot-web-peach.vercel.app/) aracı.</p>",
              "body_text": "Bkz https://chatbot-web-peach.vercel.app/",
              "language": "tr",
              "personalization_signals": ["industry_mentioned"],
              "highlighted_products": ["ai-chatbot-platform"],
              "warnings": []
            }""";
        when(llmRouter.complete(any(), anyString(), anyString(), anyString(), any()))
            .thenReturn(json);
        when(emailDraftRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EmailDraft draft = service.write(c, a);

        assertThat(draft.getBodyHtml())
            .contains("<a href=\"https://chatbot-web-peach.vercel.app/\">AI Chatbot Platform</a>")
            .doesNotContain("[AI Chatbot Platform]")
            .doesNotContain("](https://");
    }

    @Test
    void marksWriterFailedWhenValidationFails() {
        Company c = company();
        EmailAccount a = emailAccount(c);
        String badJson = """
            {
              "subject": "x",
              "body_html": "<p>Merhaba.</p>",
              "body_text": "",
              "language": "tr",
              "personalization_signals": [],
              "highlighted_products": [],
              "warnings": []
            }""";
        when(llmRouter.complete(any(), anyString(), anyString(), anyString(), any()))
            .thenReturn(badJson);
        when(emailDraftRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        EmailDraft draft = service.write(c, a);

        assertThat(draft.getStatus()).isEqualTo(DraftStatus.WRITER_FAILED);
    }
}
