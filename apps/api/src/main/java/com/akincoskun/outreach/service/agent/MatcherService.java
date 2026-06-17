package com.akincoskun.outreach.service.agent;

import com.akincoskun.outreach.domain.Company;
import com.akincoskun.outreach.domain.CompanyStatus;
import com.akincoskun.outreach.domain.Product;
import com.akincoskun.outreach.dto.MatchResult;
import com.akincoskun.outreach.integration.LlmRouter;
import com.akincoskun.outreach.repository.CompanyRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Matcher Agent (reintroduced in Faz 1.5): given a company's analysis, picks
 * the best-fitting product(s) from the fixed catalog. A discovery filter can
 * bias the match toward a target product, but the AI may still return NO_MATCH
 * if the analysis contradicts it (e.g. a "consulting" firm under a property
 * management filter).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MatcherService {

    static final String PROMPT_VERSION = "matcher_v1";

    /** Below this primary confidence the company is skipped (NO_MATCH). */
    static final double CONFIDENCE_THRESHOLD = 0.6;

    private static final String SYSTEM_PROMPT = """
        You are a B2B product-fit matcher. Given a company's analyzed profile,
        decide which of Akın's products best fits their needs.

        THE PRODUCT CATALOG (choose by slug):
        %s

        Return ONLY a valid JSON object with this exact schema:
        {
          "primary_product_slug": string (one slug from the catalog, or "none"),
          "primary_confidence": number (0.0 to 1.0),
          "secondary_product_slug": string | null (a different slug, or null),
          "secondary_confidence": number | null (0.0 to 1.0, or null),
          "reasoning": string (1-2 sentences, why this product fits)
        }

        RULES:
        - Pick the SINGLE best product as primary. Optionally a weaker secondary.
        - Confidence reflects how clearly the analysis supports the fit.
        - Turkey-only products (kolayaidat, cerezmatik) ONLY fit companies in Turkey.
        - If NO product genuinely fits, set primary_product_slug="none" and
          primary_confidence below 0.6 — do NOT force a match.
        - A bias hint may be provided, but you must verify it against the analysis.
          If the analysis contradicts the hint, ignore the hint.

        Output ONLY the JSON. No preamble, no markdown fences, no explanation.
        """;

    private static final String USER_PROMPT_TEMPLATE = """
        COMPANY:
        - Name: %s
        - Domain: %s
        - Country: %s

        ANALYSIS:
        - Industry: %s (%s)
        - Size: %s
        - Target audience: %s
        - Potential problems: %s

        BIAS HINT (from discovery filter, may be empty): %s

        Which product best fits this company?
        """;

    private final LlmRouter llmRouter;
    private final CompanyRepository companyRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public MatchResult match(Company company, String biasProductSlug) {
        Map<String, Object> analysis = company.getAnalysis() != null
            ? company.getAnalysis() : Map.of();

        String systemPrompt = SYSTEM_PROMPT.formatted(renderCatalog());
        String userPrompt = USER_PROMPT_TEMPLATE.formatted(
            company.getName(),
            company.getDomain(),
            analysis.getOrDefault("country_hint", company.getCountryCode()),
            analysis.getOrDefault("industry", "unknown"),
            analysis.getOrDefault("sub_industry", ""),
            analysis.getOrDefault("size_estimate", "unknown"),
            analysis.getOrDefault("target_audience", "unknown"),
            analysis.getOrDefault("potential_problems", java.util.List.of()),
            biasProductSlug != null ? biasProductSlug : "(none)"
        );

        String rawJson = llmRouter.complete("matcher", PROMPT_VERSION, systemPrompt, userPrompt, company);
        Map<String, Object> parsed = parseJson(rawJson);

        MatchResult result = toResult(parsed);

        if (result.matched()) {
            persistMatch(company, result);
            company.setStatus(CompanyStatus.MATCHED);
            company.setStatusReason(null);
            log.info("Matched '{}' → primary={} ({}), secondary={}", company.getDomain(),
                result.primaryProductSlug(), result.primaryConfidence(), result.secondaryProductSlug());
        } else {
            company.setStatus(CompanyStatus.SKIPPED);
            // Audit-friendly reason so a glance at the DB later answers
            // "why was this SKIPPED?" without digging through ai_calls.
            company.setStatusReason(String.format(
                "MATCH_BELOW_THRESHOLD: best=%s confidence %.2f below %.2f",
                result.primaryProductSlug(), result.primaryConfidence(), CONFIDENCE_THRESHOLD));
            log.info("No product match for '{}' ({}), skipping", company.getDomain(),
                company.getStatusReason());
        }

        companyRepository.save(company);
        return result;
    }

    public MatchResult match(Company company) {
        return match(company, null);
    }

    private MatchResult toResult(Map<String, Object> parsed) {
        String primarySlug = asString(parsed.get("primary_product_slug"));
        double primaryConfidence = asDouble(parsed.get("primary_confidence"), 0.0);
        String secondarySlug = asString(parsed.get("secondary_product_slug"));
        Double secondaryConfidence = parsed.get("secondary_confidence") != null
            ? asDouble(parsed.get("secondary_confidence"), 0.0) : null;
        String reasoning = asString(parsed.get("reasoning"));

        boolean known = Product.fromSlug(primarySlug).isPresent();
        boolean matched = known && primaryConfidence >= CONFIDENCE_THRESHOLD;

        // Drop a secondary that is unknown or "none".
        if (Product.fromSlug(secondarySlug).isEmpty()) {
            secondarySlug = null;
            secondaryConfidence = null;
        }

        return new MatchResult(
            matched ? primarySlug : (known ? primarySlug : "none"),
            primaryConfidence,
            secondarySlug,
            secondaryConfidence,
            reasoning,
            matched
        );
    }

    /** Stashes the match on the company's analysis JSONB so the Writer can read it. */
    private void persistMatch(Company company, MatchResult result) {
        Map<String, Object> analysis = company.getAnalysis() != null
            ? new HashMap<>(company.getAnalysis()) : new HashMap<>();
        Map<String, Object> match = new HashMap<>();
        match.put("primary_product_slug", result.primaryProductSlug());
        match.put("primary_confidence", result.primaryConfidence());
        match.put("secondary_product_slug", result.secondaryProductSlug());
        match.put("secondary_confidence", result.secondaryConfidence());
        match.put("reasoning", result.reasoning());
        analysis.put("match", match);
        company.setAnalysis(analysis);
    }

    private String renderCatalog() {
        return Arrays.stream(Product.values())
            .map(p -> "- " + p.slug() + " (" + p.displayName() + ")"
                + (p.turkeyOnly() ? " [Turkey only]" : "") + ": " + p.description())
            .collect(Collectors.joining("\n"));
    }

    private Map<String, Object> parseJson(String raw) {
        try {
            String cleaned = raw.strip()
                .replaceFirst("^```json\\s*", "")
                .replaceFirst("```$", "")
                .strip();
            return objectMapper.readValue(cleaned, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Matcher returned invalid JSON: " + raw, e);
        }
    }

    private String asString(Object value) {
        return value != null ? value.toString() : null;
    }

    private double asDouble(Object value, double fallback) {
        if (value instanceof Number number) return number.doubleValue();
        if (value instanceof String s && !s.isBlank()) {
            try { return Double.parseDouble(s); } catch (NumberFormatException ignored) { }
        }
        return fallback;
    }
}
