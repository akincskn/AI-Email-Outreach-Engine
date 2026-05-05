package com.akincoskun.outreach.service.agent;

import com.akincoskun.outreach.domain.*;
import com.akincoskun.outreach.integration.LlmRouter;
import com.akincoskun.outreach.repository.EmailDraftRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WriterService {

    static final String PROMPT_VERSION = "writer_v1";

    private static final String SYSTEM_PROMPT = """
        You are writing a B2B introduction email as Akin Coskun, a full-stack
        developer from Turkey who builds AI-powered tools and free SaaS demos.

        Akin's profile:
        - Full-stack dev (Next.js, Spring Boot, AI/N8N)
        - Built 7 free demo products to solve real business problems
        - Looking to share these tools with businesses that might benefit

        The 7 products to mention (highlight 1-2 based on the company):
        1. RivalRadar — AI competitor analysis (https://rivalradar-three.vercel.app)
        2. AI Chatbot Platform — 24/7 FAQ chatbot from documents (https://chatbot-web-peach.vercel.app)
        3. GEO Analyzer — AI search visibility scoring (https://geo-analyzer-sepia.vercel.app)
        4. LeadPilot — AI SDR agent (https://leadpilot-silk.vercel.app)
        5. KolayAidat — Apartment dues tracking (https://kolayaidat.vercel.app) [Turkey-specific]
        6. FormJet — No-code form builder
        7. Cerezmatik — KVKK cookie consent generator [Turkey-specific]

        GOAL: Send a thoughtful, brief introduction. NOT a sales pitch.
        The email should:
        1. Open with a relevant observation about the company
        2. Briefly introduce who Akin is (1 sentence)
        3. Mention 1-2 products most relevant to this company
        4. Mention the broader portfolio (https://akin-coskun.web.app)
        5. End with a soft optional question: "Is this something you'd find useful?"

        CRITICAL RULES:
        - TONE: Professional but human. Not corporate, not salesy.
        - LENGTH: 100-180 words for the body. SHORT.
        - LANGUAGE: Match the company's primary_language (tr or en)
        - DO NOT promise outcomes or use marketing buzzwords
        - DO mention these are FREE tools
        - For TR: use formal "siz", write in Turkish
        - For EN: informal but professional English

        HTML body must include:
        - Intro paragraph with company name + observation
        - Brief Akin intro
        - 1-2 highlighted products with links
        - Full portfolio link
        - Soft closing question + signature
        - <hr> followed by physical address {{PHYSICAL_ADDRESS}} and unsubscribe {{UNSUBSCRIBE_URL}}

        OUTPUT FORMAT — Return ONLY a valid JSON object:
        {
          "subject": string (40-70 chars),
          "body_html": string (HTML),
          "body_text": string (plain text),
          "language": "tr" | "en",
          "personalization_signals": string[],
          "highlighted_products": string[],
          "warnings": string[]
        }

        Output ONLY the JSON. No preamble, no markdown fences.
        """;

    private static final String USER_PROMPT_TEMPLATE = """
        TARGET COMPANY:
        - Name: %s
        - Domain: %s
        - Email destination: %s

        COMPANY ANALYSIS:
        - Industry: %s (%s)
        - Size: %s
        - Country: %s
        - Language: %s
        - Target audience: %s
        - Potential problems: %s

        Write a personalized introduction email.
        """;

    private final LlmRouter llmRouter;
    private final EmailDraftRepository emailDraftRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.sender.physical-address}")
    private String physicalAddress;

    @Transactional
    public EmailDraft write(Company company, EmailAccount emailAccount) {
        Map<String, Object> analysis = company.getAnalysis() != null
            ? company.getAnalysis() : Map.of();

        String userPrompt = USER_PROMPT_TEMPLATE.formatted(
            company.getName(),
            company.getDomain(),
            emailAccount.getEmail(),
            analysis.getOrDefault("industry", "unknown"),
            analysis.getOrDefault("sub_industry", ""),
            analysis.getOrDefault("size_estimate", "unknown"),
            analysis.getOrDefault("country_hint", company.getCountryCode()),
            analysis.getOrDefault("primary_language", "en"),
            analysis.getOrDefault("target_audience", "unknown"),
            analysis.getOrDefault("potential_problems", List.of())
        );

        String rawJson = llmRouter.complete("writer", PROMPT_VERSION, SYSTEM_PROMPT, userPrompt, company);
        Map<String, Object> result = parseJson(rawJson);

        String bodyHtml = injectFooter((String) result.get("body_html"), emailAccount);
        String bodyText = injectFooterText((String) result.get("body_text"), emailAccount);

        validateContent(result, bodyHtml, bodyText);

        @SuppressWarnings("unchecked")
        List<String> signals = (List<String>) result.getOrDefault("personalization_signals", List.of());
        @SuppressWarnings("unchecked")
        List<String> warnings = (List<String>) result.getOrDefault("warnings", List.of());

        DraftStatus status = warnings.stream().anyMatch(w -> w.startsWith("VALIDATION_FAIL"))
            ? DraftStatus.WRITER_FAILED : DraftStatus.PENDING;

        EmailDraft draft = EmailDraft.builder()
            .company(company)
            .emailAccount(emailAccount)
            .subject((String) result.get("subject"))
            .bodyHtml(bodyHtml)
            .bodyText(bodyText)
            .language((String) result.getOrDefault("language", "en"))
            .modelUsed("llama-3.3-70b-versatile")
            .promptVersion(PROMPT_VERSION)
            .personalizationSignals(signals)
            .warnings(warnings)
            .status(status)
            .build();

        emailDraftRepository.save(draft);
        log.info("Draft created for '{}' → {}: status={}", company.getDomain(),
            emailAccount.getEmail(), status);
        return draft;
    }

    private String injectFooter(String html, EmailAccount account) {
        if (html == null) return "";
        return html
            .replace("{{PHYSICAL_ADDRESS}}", physicalAddress)
            .replace("{{UNSUBSCRIBE_URL}}", "/unsubscribe?token=PLACEHOLDER");
    }

    private String injectFooterText(String text, EmailAccount account) {
        if (text == null) return "";
        return text
            .replace("{{PHYSICAL_ADDRESS}}", physicalAddress)
            .replace("{{UNSUBSCRIBE_URL}}", "/unsubscribe?token=PLACEHOLDER");
    }

    private void validateContent(Map<String, Object> result, String bodyHtml, String bodyText) {
        List<String> warnings = castList(result.get("warnings"));

        String subject = (String) result.get("subject");
        if (subject == null || subject.length() < 10 || subject.length() > 120) {
            warnings.add("VALIDATION_FAIL:subject_length");
        }
        if (bodyText == null || bodyText.isBlank()) {
            warnings.add("VALIDATION_FAIL:missing_body_text");
        }
        if (bodyHtml != null && bodyHtml.contains("{{")) {
            warnings.add("VALIDATION_FAIL:placeholder_leakage");
        }

        List<String> forbidden = List.of("guaranteed", "act now", "limited time", "FREE!!!");
        for (String phrase : forbidden) {
            if (bodyHtml != null && bodyHtml.toLowerCase().contains(phrase.toLowerCase())) {
                warnings.add("VALIDATION_FAIL:forbidden_phrase:" + phrase);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> castList(Object obj) {
        if (obj instanceof List<?> list) return (List<String>) list;
        return new java.util.ArrayList<>();
    }

    private Map<String, Object> parseJson(String raw) {
        try {
            String cleaned = raw.strip()
                .replaceFirst("^```json\\s*", "")
                .replaceFirst("```$", "")
                .strip();
            return objectMapper.readValue(cleaned, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Writer returned invalid JSON: " + raw, e);
        }
    }
}
