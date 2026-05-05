package com.akincoskun.outreach.service.agent;

import com.akincoskun.outreach.domain.Company;
import com.akincoskun.outreach.domain.CompanyStatus;
import com.akincoskun.outreach.integration.LlmRouter;
import com.akincoskun.outreach.repository.CompanyRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyzerService {

    static final String PROMPT_VERSION = "analyzer_v1";

    private static final String SYSTEM_PROMPT = """
        You are a B2B company analyzer. Given the homepage and contact page text
        of a company website, extract structured information about the business.

        Return ONLY a valid JSON object with this exact schema:
        {
          "industry": string,
          "sub_industry": string,
          "size_estimate": "solo" | "small" | "medium" | "large" | "enterprise" | "unknown",
          "country_hint": string,
          "primary_language": "tr" | "en" | "other",
          "tech_stack_hints": string[],
          "potential_problems": string[],
          "target_audience": "consumers" | "businesses" | "both" | "unknown",
          "online_presence_score": number,
          "is_target_country": boolean,
          "skip_reason": string | null
        }

        GUIDELINES:
        - "size_estimate": solo=1, small=2-10, medium=11-50, large=51-500, enterprise=500+
        - "potential_problems": max 3, be specific (no "marketing" — say "no chatbot for FAQs")
        - "is_target_country": true if TR, US, GB, CA, NL, BE, AU, IE, NZ; false if DE, FR, ES
        - "skip_reason": set if government, NGO, or very large enterprise

        Output ONLY the JSON. No preamble, no markdown fences, no explanation.
        """;

    private static final String USER_PROMPT_TEMPLATE = """
        COMPANY INFO:
        - Domain: %s
        - Name: %s

        HOMEPAGE TEXT (truncated to 3000 chars):
        %s

        CONTACT PAGE TEXT (truncated to 1500 chars):
        %s

        Analyze this company.
        """;

    private final LlmRouter llmRouter;
    private final CompanyRepository companyRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public Map<String, Object> analyze(Company company) {
        String homepageText = fetchText(resolveUrl(company, ""), 3000);
        String contactText  = fetchText(resolveUrl(company, "/contact"), 1500);

        String userPrompt = USER_PROMPT_TEMPLATE.formatted(
            company.getDomain(), company.getName(), homepageText, contactText
        );

        String rawJson = llmRouter.complete("analyzer", PROMPT_VERSION, SYSTEM_PROMPT, userPrompt, company);
        Map<String, Object> analysis = parseJson(rawJson);

        company.setAnalysis(analysis);
        company.setAnalysisAt(Instant.now());
        company.setAnalysisVersion(PROMPT_VERSION);

        Boolean isTarget = (Boolean) analysis.get("is_target_country");
        String skipReason = (String) analysis.get("skip_reason");

        if (Boolean.FALSE.equals(isTarget) || (skipReason != null && !skipReason.isBlank())) {
            company.setStatus(CompanyStatus.BLACKLISTED);
            company.setStatusReason(skipReason != null ? skipReason : "not_target_country");
        } else {
            company.setStatus(CompanyStatus.ANALYZED);
        }

        companyRepository.save(company);
        log.info("Analyzed company '{}': industry={}, status={}", company.getDomain(),
            analysis.get("industry"), company.getStatus());
        return analysis;
    }

    private String fetchText(String url, int maxChars) {
        try {
            Document doc = Jsoup.connect(url).timeout(10_000).ignoreHttpErrors(true).get();
            String text = doc.body().text();
            return text.length() > maxChars ? text.substring(0, maxChars) : text;
        } catch (IOException e) {
            log.debug("Could not fetch {}: {}", url, e.getMessage());
            return "";
        }
    }

    private Map<String, Object> parseJson(String raw) {
        try {
            String cleaned = raw.strip()
                .replaceFirst("^```json\\s*", "")
                .replaceFirst("```$", "")
                .strip();
            return objectMapper.readValue(cleaned, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Analyzer returned invalid JSON: " + raw, e);
        }
    }

    private String resolveUrl(Company company, String path) {
        String base = company.getWebsiteUrl() != null
            ? company.getWebsiteUrl().replaceAll("/+$", "")
            : "https://" + company.getDomain();
        return base + path;
    }
}
