package com.akincoskun.outreach.integration;

import com.akincoskun.outreach.domain.AiCall;
import com.akincoskun.outreach.domain.Company;
import com.akincoskun.outreach.repository.AiCallRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class LlmRouter {

    private final GroqClient groqClient;
    private final GeminiClient geminiClient;
    private final AiCallRepository aiCallRepository;

    // Set to true in local profile to skip real API calls (no API keys needed)
    @Value("${app.ai.mock-enabled:false}")
    private boolean mockEnabled;

    private static final String MOCK_ANALYZER_RESPONSE = """
        {
          "industry": "technology",
          "sub_industry": "saas",
          "size_estimate": "small",
          "country_hint": "TR",
          "primary_language": "tr",
          "tech_stack_hints": ["wordpress", "google-analytics"],
          "potential_problems": ["no FAQ chatbot", "no competitor tracking", "manual lead process"],
          "target_audience": "businesses",
          "online_presence_score": 6,
          "is_target_country": true,
          "skip_reason": null
        }
        """;

    private static final String MOCK_MATCHER_RESPONSE = """
        {
          "primary_product_slug": "kolayaidat",
          "primary_confidence": 0.92,
          "secondary_product_slug": "cerezmatik",
          "secondary_confidence": 0.55,
          "reasoning": "Türkiye'de apartman/site yönetimi yapan bir şirket — aidat takibi en büyük operasyonel yükleri. KolayAidat doğrudan bu sorunu çözüyor; web siteleri için Çerezmatik ikincil uyum sağlar."
        }
        """;

    // writer_v2 fixture: focused on the matched product (KolayAidat for TR property mgmt).
    // {company_name} is substituted with the real company name in the mock branch below,
    // so the fixture faithfully mirrors what the live LLM produces (named intro).
    private static final String MOCK_WRITER_RESPONSE = """
        {
          "subject": "Aidat takibini kolaylaştıran ücretsiz bir araç",
          "body_html": "<p>Merhaba {company_name} ekibi, sitenizi inceledim — apartman/site yönetimi yaptığınızı gördüm. Aidat takibinin çoğu yerde hâlâ Excel'le yürütüldüğünü ve bunun zaman aldığını biliyorum.</p><p>Ben Akın Coşkun, full-stack developer'ım; küçük işletmeler için ücretsiz araçlar geliştiriyorum.</p><p>Tam bu iş için <a href='https://kolayaidat.vercel.app'>KolayAidat</a>'ı yaptım: aidatları otomatik takip eder, sakinlere hatırlatma gönderir ve online ödeme imkânı sunar — Excel'e veda. Tamamen ücretsiz.</p><p>İsterseniz web siteniz için KVKK çerez uyumunu çözen <a href='https://cerezmatik.vercel.app'>Çerezmatik</a>'e de bakabilirsiniz. Toplam 7 ücretsiz aracım için: <a href='https://akin-coskun.web.app'>akin-coskun.web.app</a></p><p>İhtiyacınıza uyuyor mu? Cevap yazmanıza gerek yok, dilerseniz deneyin.</p><p>İyi günler,<br>Akın Coşkun<br><a href='https://github.com/akincskn'>github.com/akincskn</a></p><hr><p style='font-size:11px;color:#888;'>{{PHYSICAL_ADDRESS}}<br>Aboneliği iptal etmek için <a href='{{UNSUBSCRIBE_URL}}'>buraya</a> tıklayın.</p>",
          "body_text": "Merhaba {company_name} ekibi, sitenizi inceledim — apartman/site yönetimi yaptığınızı gördüm. Aidat takibinin çoğu yerde hâlâ Excel'le yürütüldüğünü ve bunun zaman aldığını biliyorum.\\n\\nBen Akın Coşkun, full-stack developer'ım; küçük işletmeler için ücretsiz araçlar geliştiriyorum.\\n\\nTam bu iş için KolayAidat'ı yaptım: aidatları otomatik takip eder, sakinlere hatırlatma gönderir ve online ödeme imkânı sunar — Excel'e veda. Tamamen ücretsiz. https://kolayaidat.vercel.app\\n\\nİsterseniz web siteniz için KVKK çerez uyumunu çözen Çerezmatik'e de bakabilirsiniz: https://cerezmatik.vercel.app\\nToplam 7 ücretsiz aracım: https://akin-coskun.web.app\\n\\nİhtiyacınıza uyuyor mu? Cevap yazmanıza gerek yok, dilerseniz deneyin.\\n\\nİyi günler,\\nAkın Coşkun\\nhttps://github.com/akincskn\\n\\n---\\n{{PHYSICAL_ADDRESS}}\\nAboneliği iptal: {{UNSUBSCRIBE_URL}}",
          "language": "tr",
          "personalization_signals": ["sektör (apartman yönetimi)", "dil eşleşmesi", "Excel aidat sorunu"],
          "highlighted_products": ["kolayaidat", "cerezmatik"],
          "warnings": []
        }
        """;

    public String complete(String agentName, String promptVersion,
                           String systemPrompt, String userPrompt,
                           Company company) {

        if (mockEnabled) {
            log.info("[MOCK LLM] Returning fixture response for agent={}", agentName);
            return switch (agentName) {
                case "writer" -> MOCK_WRITER_RESPONSE.replace("{company_name}", mockCompanyName(company));
                case "matcher" -> MOCK_MATCHER_RESPONSE;
                default -> MOCK_ANALYZER_RESPONSE;
            };
        }

        return liveComplete(agentName, promptVersion, systemPrompt, userPrompt, company);
    }

    /** Mock-only: substitute the fixture's {company_name} so the named intro mirrors live output. */
    private String mockCompanyName(Company company) {
        if (company != null && company.getName() != null && !company.getName().isBlank()) {
            return company.getName();
        }
        return "ekibiniz";
    }

    private String liveComplete(String agentName, String promptVersion,
                                String systemPrompt, String userPrompt, Company company) {
        long start = System.currentTimeMillis();

        try {
            String result = groqClient.complete(systemPrompt, userPrompt)
                .onErrorResume(ex -> {
                    log.warn("Groq failed for agent={}, falling back to Gemini: {}", agentName, ex.getMessage());
                    return geminiClient.complete(systemPrompt, userPrompt);
                })
                .block();

            logCall(agentName, promptVersion, groqClient.providerName(),
                    systemPrompt, result, true, null, company,
                    System.currentTimeMillis() - start);
            return result;

        } catch (Exception ex) {
            logCall(agentName, promptVersion, "both_failed",
                    systemPrompt, null, false, ex.getMessage(), company,
                    System.currentTimeMillis() - start);
            throw new LlmException("Both Groq and Gemini failed for agent=" + agentName, ex);
        }
    }

    private void logCall(String agentName, String promptVersion, String provider,
                         String systemPrompt, String response, boolean success,
                         String errorMessage, Company company, long durationMs) {
        AiCall call = AiCall.builder()
            .company(company)
            .agentName(agentName)
            .provider(provider)
            .model(provider.equals("groq") ? "llama-3.3-70b-versatile" : "gemini-2.0-flash")
            .promptVersion(promptVersion)
            .success(success)
            .errorMessage(errorMessage)
            .durationMs((int) durationMs)
            .promptSnippet(systemPrompt.length() > 512 ? systemPrompt.substring(0, 512) : systemPrompt)
            .responseSnippet(response != null
                ? (response.length() > 512 ? response.substring(0, 512) : response)
                : null)
            .createdAt(Instant.now())
            .build();
        aiCallRepository.save(call);
    }
}
