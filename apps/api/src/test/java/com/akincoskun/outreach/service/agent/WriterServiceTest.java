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
        ReflectionTestUtils.setField(service, "physicalAddress", "Istanbul, Turkey");
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

    private String validDraftJson(String lang) {
        return """
            {
              "subject": "Restoranlar için ücretsiz AI araçları",
              "body_html": "<p>Merhaba Mario ekibi,</p><p>Akın Coşkun.</p><hr><p>{{PHYSICAL_ADDRESS}}</p><a href=\\"{{UNSUBSCRIBE_URL}}\\">Unsubscribe</a>",
              "body_text": "Merhaba Mario ekibi,\\n\\nAkın Coşkun.\\n\\n---\\n{{PHYSICAL_ADDRESS}}\\n{{UNSUBSCRIBE_URL}}",
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
        assertThat(draft.getBodyHtml()).contains("Istanbul, Turkey");
        assertThat(draft.getBodyHtml()).doesNotContain("{{PHYSICAL_ADDRESS}}");
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
