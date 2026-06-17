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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WriterService {

    /** Active sector-aware prompt: focuses the email on the matched product. */
    static final String PROMPT_VERSION = "writer_v2";

    /** Legacy portfolio-overview prompt, kept as a fallback when there is no match. */
    static final String PROMPT_VERSION_V1 = "writer_v1";

    // ── writer_v2 — sector-aware, single matched product in focus ──────────────
    private static final String SYSTEM_PROMPT_V2 = """
        You are writing a B2B introduction email as Akın Coşkun, a full-stack
        developer from Turkey who builds free AI-powered tools and SaaS demos.

        Unlike a generic portfolio blast, THIS email is focused: a matcher has
        already chosen the single product that best fits this company. Lead with
        that product.

        CRITICAL DATA INTEGRITY RULES (HIGHEST PRIORITY — override everything else):
        1. URLS: Use the EXACT URL strings from the user prompt fields. Do not
           modify, abbreviate, complete, or guess URLs. Copy them
           character-by-character (e.g. sender.portfolio_url is
           "https://akin-coskun.web.app" — never "akincskn.web.app").
        2. NAMES: Use EXACT names as provided:
           - Company name: user prompt "company_name"
           - Product names: "primary_product.name" / "secondary_product.name"
           - Sender name: "sender.name"
           - GitHub handle: "akincskn" (NOT "akincoskun", NOT "akin-coskun",
             NOT any other variation) — it is given as sender.github_url.
        3. FACTS: Use only data from the user prompt. Do not invent product
           features, statistics, or claims that are not in the input.
        If a URL or name is missing from the user prompt, OMIT it (don't guess).
        The response is INVALID if any URL or name is modified.

        GOAL: A thoughtful, human introduction — NOT a sales pitch.

        EMAIL STRUCTURE (MANDATORY — exactly 4 paragraphs, in this order):

        PARAGRAPH 1 — MUST be exactly 2 sentences:
        - Sentence 1: a warm greeting using company_name, e.g. "Merhaba
          {company_name} ekibi, sitenizi inceledim." (Missing name = INVALID.)
        - Sentence 2: an industry-specific observation that mentions the pain
          point, e.g. "Apartman yönetiminde aidat takibinin Excel ile yapılmasının
          yarattığı sorunları biliyorum."
        If P1 is only 1 sentence, the response is INVALID.

        PARAGRAPH 2 (Self-introduction, 1 sentence):
        - "Ben Akın Coşkun, küçük işletmeler için ücretsiz araçlar geliştiren bir
          yazılım geliştiriciyim." or a natural variation.

        PARAGRAPH 3 (Primary product, 3-4 sentences):
        - Introduce the PRIMARY product with its full URL.
        - Explain WHAT it does in concrete terms (about 3 specific features).
        - Say it is free and quick to try.
        - Example for KolayAidat: "Tam bu iş için KolayAidat'ı geliştirdim.
          Aidatları otomatik takip eder, sakinlere SMS/e-posta ile hatırlatma
          gönderir ve online ödeme imkânı sunar. Excel'e veda etmek için 5 dakikada
          kurulabilir, tamamen ücretsizdir."

        PARAGRAPH 4 (Soft CTA + portfolio, 2 sentences):
        - A soft question + the full portfolio mention.
        - Example: "İhtiyacınıza uyuyor mu? Tüm 7 ücretsiz aracımı
          akin-coskun.web.app adresinde görebilirsiniz. Cevap yazmanıza gerek yok,
          dilerseniz deneyin."

        Following these 4 paragraphs yields a natural ~110-150 words. DO NOT count
        words — just follow the paragraph structure faithfully.

        CRITICAL RULES:
        - TONE: Professional but warm and human. Not corporate, not salesy.
        - LANGUAGE: Match primary_language (tr or en).
        - For TR: natural, native Turkish with formal "siz". It must NOT read like
          an English email translated to Turkish. Use the product name as-is
          (KolayAidat, Çerezmatik).
        - For EN: informal but professional.
        - These are FREE tools — say so.
        - DO NOT promise outcomes or use marketing buzzwords ("devrim", "10x",
          "game-changer", "revolutionize").
        - Turkey-only products (KolayAidat, Çerezmatik) are only sent to TR companies.

        INDUSTRY-SPECIFIC PAIN POINTS (MUST mention naturally in the body, keyed by
        the PRIMARY product — this is mandatory, not "use if you can"):
        - kolayaidat (property management) → at least ONE of these phrases MUST
          appear: "Excel ile aidat takibi", "Excel'e veda", "manuel takip". Make it
          concrete, e.g. "kim ödedi kim ödemedi unutuluyor", "aylık raporlama elle".
        - ai-chatbot-platform (restaurant/FAQ) → a specific question-handling pain,
          e.g. "menü ve saat soruları gece geç saatte geliyor", "WhatsApp DM'lerine
          yetişmek zor".
        - rivalradar (saas/competitor) → "rakip analizi saatler alıyor",
          "rakipleri elle takip etmek".
        - geo-analyzer (visibility) → "ChatGPT/Perplexity sizi öneriyor mu
          bilinmiyor", "AI aramalarında görünmüyorsunuz".
        - leadpilot (sales/marketing) → "lead research manuel", "outreach
          mailleri tek tek elle yazılıyor".
        - cerezmatik (KVKK cookie consent) → "KVKK çerez uyumu elle yönetiliyor",
          "çerez izni banner'ı eksik veya hatalı".
        - formjet (forms) → "her form için kod yazmak", "form verileri dağınık".

        TURKISH OUTPUT QUALITY RULES (proofread the body before finalizing):
        - Use proper Turkish words. NOT "demostrarı" — use "demoları"/"demoyu".
        - NOT "tools" (anglicism) — use "araçlar"/"uygulamalar".
        - NOT "feature" — use "özellik". NOT "platform" when "sistem"/"uygulama"
          fits better in context.
        - Watch for common AI mistakes in Turkish: wrong declension suffixes, and
          foreign root + Turkish suffix mashups (e.g. "demostrarı"). Reread once.

        FINAL STEP — TURKISH SELF-REVIEW (mandatory before returning the JSON):
        Mentally re-read your body content and check:
        1. Every Turkish word is spelled correctly. Common AI errors:
           - "geliyorum" (wrong, = I am coming) → "geliştiriciyim" (developer)
           - "demostrarı" (wrong) → "demoları"
           - "tools" (anglicism) → "araçlar"
           - mismatched suffixes after foreign roots.
        2. Every sentence reads naturally — not AI-translated. If a sentence feels
           awkward, rephrase it.
        3. Verb forms are correct (e.g. NOT "geliştirme yapıyorum" → "geliştiriyorum").
        If you find ANY error during review, FIX it before returning. The output
        you return must be PUBLISHABLE quality.

        SUBJECT: 40-70 chars, reference the company + the primary product's core
        benefit. Not salesy. Example for KolayAidat (TR):
        "{company} için aidat takibini kolaylaştıran bir araç".

        HTML body must include:
        - Intro <p> with company name + observation
        - Brief Akın intro
        - The primary product with its EXACT primary_product.url, tied to the pain
        - The portfolio link using the EXACT sender.portfolio_url
        - Soft closing question + signature (sender.name, sender.github_url)
        - <hr> then physical address {{PHYSICAL_ADDRESS}} and unsubscribe {{UNSUBSCRIBE_URL}}

        Refer to the primary product by its NAME in the sentence (e.g. "KolayAidat'ı
        geliştirdim"), and put its full https:// URL in the anchor href. Do not
        replace the product name with the bare URL.

        OUTPUT FORMAT — Return ONLY a valid JSON object:
        {
          "subject": string (40-70 chars),
          "body_html": string (HTML),
          "body_text": string (plain text),
          "language": "tr" | "en",
          "personalization_signals": string[],
          "highlighted_products": string[] (product slugs actually mentioned),
          "warnings": string[]
        }

        Output ONLY the JSON. No preamble, no markdown fences.
        """;

    private static final String USER_PROMPT_TEMPLATE_V2 = """
        INPUT DATA — use ONLY these exact values. Copy every URL and name
        character-by-character; do not alter them.

        {
          "company_name": "%s",
          "industry": "%s (%s)",
          "country": "%s",
          "language": "%s",
          "target_audience": "%s",
          "potential_problems": %s,
          "primary_product": {
            "name": "%s",
            "slug": "%s",
            "url": "%s",
            "description": "%s"
          },
          "secondary_product": %s,
          "sender": {
            "name": "%s",
            "portfolio_url": "%s",
            "github_url": "%s",
            "total_products_count": 7
          },
          "match_reasoning": "%s"
        }

        Write the focused introduction email now, following the mandatory
        4-paragraph structure.
        """;

    // ── writer_v1 — legacy portfolio overview (fallback when no match) ──────────
    private static final String SYSTEM_PROMPT_V1 = """
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
        - For EN: informal but professional

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

    private static final String USER_PROMPT_TEMPLATE_V1 = """
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

    @Value("${app.sender.from-name}")
    private String senderName;

    @Value("${app.sender.portfolio-url}")
    private String portfolioUrl;

    @Value("${app.sender.github-url}")
    private String githubUrl;

    @Transactional
    public EmailDraft write(Company company, EmailAccount emailAccount) {
        Map<String, Object> analysis = company.getAnalysis() != null
            ? company.getAnalysis() : Map.of();
        Map<String, Object> match = extractMatch(analysis);
        Product primary = match != null
            ? Product.fromSlug((String) match.get("primary_product_slug")).orElse(null)
            : null;

        if (primary != null) {
            return writeSectorAware(company, emailAccount, analysis, match, primary);
        }
        // No usable match → fall back to the legacy portfolio-overview email.
        log.info("No matched product for '{}', using writer_v1 fallback", company.getDomain());
        return writePortfolioOverview(company, emailAccount, analysis);
    }

    private EmailDraft writeSectorAware(Company company, EmailAccount emailAccount,
                                        Map<String, Object> analysis, Map<String, Object> match,
                                        Product primary) {
        Product secondary = Product.fromSlug((String) match.get("secondary_product_slug")).orElse(null);

        String secondaryJson = secondary != null
            ? "{ \"name\": \"" + secondary.displayName() + "\", \"slug\": \"" + secondary.slug()
                + "\", \"url\": \"" + secondary.url() + "\" }"
            : "null";

        String userPrompt = USER_PROMPT_TEMPLATE_V2.formatted(
            company.getName(),
            analysis.getOrDefault("industry", "unknown"),
            analysis.getOrDefault("sub_industry", ""),
            analysis.getOrDefault("country_hint", company.getCountryCode()),
            analysis.getOrDefault("primary_language", "en"),
            analysis.getOrDefault("target_audience", "unknown"),
            analysis.getOrDefault("potential_problems", List.of()),
            primary.displayName(), primary.slug(), primary.url(), primary.description(),
            secondaryJson,
            senderName, portfolioUrl, githubUrl,
            match.getOrDefault("reasoning", "")
        );

        String rawJson = llmRouter.complete("writer", PROMPT_VERSION, SYSTEM_PROMPT_V2, userPrompt, company);
        return buildDraft(company, emailAccount, rawJson, PROMPT_VERSION, match);
    }

    private EmailDraft writePortfolioOverview(Company company, EmailAccount emailAccount,
                                              Map<String, Object> analysis) {
        String userPrompt = USER_PROMPT_TEMPLATE_V1.formatted(
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

        String rawJson = llmRouter.complete("writer", PROMPT_VERSION_V1, SYSTEM_PROMPT_V1, userPrompt, company);
        return buildDraft(company, emailAccount, rawJson, PROMPT_VERSION_V1, null);
    }

    private EmailDraft buildDraft(Company company, EmailAccount emailAccount, String rawJson,
                                  String promptVersion, Map<String, Object> match) {
        Map<String, Object> result = parseJson(rawJson);

        String bodyHtml = cleanAnchorText(injectFooter((String) result.get("body_html")));
        String bodyText = injectFooter((String) result.get("body_text"));

        String primaryProductSlug = match != null ? (String) match.get("primary_product_slug") : null;
        validateContent(result, bodyHtml, bodyText, primaryProductSlug);

        @SuppressWarnings("unchecked")
        List<String> signals = (List<String>) result.getOrDefault("personalization_signals", List.of());
        @SuppressWarnings("unchecked")
        List<String> warnings = (List<String>) result.getOrDefault("warnings", List.of());

        DraftStatus status = warnings.stream().anyMatch(w -> w.startsWith("VALIDATION_FAIL"))
            ? DraftStatus.WRITER_FAILED : DraftStatus.PENDING;

        EmailDraft.EmailDraftBuilder builder = EmailDraft.builder()
            .company(company)
            .emailAccount(emailAccount)
            .subject((String) result.get("subject"))
            .bodyHtml(bodyHtml)
            .bodyText(bodyText)
            .language((String) result.getOrDefault("language", "en"))
            .modelUsed("llama-3.3-70b-versatile")
            .promptVersion(promptVersion)
            .personalizationSignals(signals)
            .warnings(warnings)
            .status(status);

        if (match != null) {
            builder.matchedProductSlug((String) match.get("primary_product_slug"))
                .secondaryProductSlug((String) match.get("secondary_product_slug"))
                .matchConfidence(toBigDecimal(match.get("primary_confidence")))
                .matchReasoning((String) match.get("reasoning"));
        }

        EmailDraft draft = builder.build();
        emailDraftRepository.save(draft);
        log.info("Draft created for '{}' → {}: status={}, prompt={}, product={}", company.getDomain(),
            emailAccount.getEmail(), status, promptVersion, draft.getMatchedProductSlug());
        return draft;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMatch(Map<String, Object> analysis) {
        Object match = analysis.get("match");
        return match instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue()).setScale(2, RoundingMode.HALF_UP);
        }
        return null;
    }

    private String injectFooter(String content) {
        if (content == null) return "";
        return content
            .replace("{{PHYSICAL_ADDRESS}}", physicalAddress)
            .replace("{{UNSUBSCRIBE_URL}}", "/unsubscribe?token=PLACEHOLDER");
    }

    /**
     * Görev 6.2 — cosmetic: strip the scheme from VISIBLE anchor text only, so
     * links render as "akin-coskun.web.app" instead of "https://akin-coskun.web.app".
     * The href is untouched (anchor text starts right after '>', hrefs use ="...").
     * The relative unsubscribe link has no scheme, so it is unaffected.
     */
    private String cleanAnchorText(String html) {
        if (html == null) return "";
        return html
            .replace(">https://", ">")
            .replace(">http://", ">");
    }

    private void validateContent(Map<String, Object> result, String bodyHtml, String bodyText,
                                 String primaryProductSlug) {
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

        // ── Soft quality signals (QUALITY_WARN → does NOT fail the draft) ──────
        // Word count on the raw body (pre-footer), a rough proxy for the 110-140
        // target. Flags an over-compressed or bloated email for Akın's review.
        String rawBody = (String) result.get("body_text");
        if (rawBody != null && !rawBody.isBlank()) {
            int words = rawBody.trim().split("\\s+").length;
            if (words < 90 || words > 160) {
                warnings.add("QUALITY_WARN:body_word_count=" + words + " (target ~110-150)");
            }
        }
        // KolayAidat emails should surface the concrete Excel/manual-tracking pain.
        if ("kolayaidat".equals(primaryProductSlug)
                && rawBody != null && !rawBody.toLowerCase().contains("excel")) {
            warnings.add("QUALITY_WARN:kolayaidat_missing_excel_pain");
        }

        result.put("warnings", warnings);
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
