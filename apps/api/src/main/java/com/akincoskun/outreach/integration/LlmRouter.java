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

    private static final String MOCK_WRITER_RESPONSE = """
        {
          "subject": "Akın'dan kısa tanışma — 7 ücretsiz SaaS aracı",
          "body_html": "<p>Merhaba,</p><p>Akın Coşkun, full-stack developer. Ücretsiz geliştirdiğim <a href='https://rivalradar-three.vercel.app'>RivalRadar</a> ve <a href='https://chatbot-web-peach.vercel.app'>AI Chatbot</a> araçları şirketinize faydalı olabilir.</p><p>Tüm portföy: <a href='https://akin-coskun.web.app'>akin-coskun.web.app</a></p><p>İlginizi çeker mi?</p><p>Saygılarımla,<br>Akın Coşkun</p><hr><p>{{PHYSICAL_ADDRESS}}</p><p><a href='{{UNSUBSCRIBE_URL}}'>Aboneliği iptal et</a></p>",
          "body_text": "Merhaba, Akın Coşkun, full-stack developer. Ücretsiz geliştirdiğim RivalRadar ve AI Chatbot araçları şirketinize faydalı olabilir. Tüm portföy: https://akin-coskun.web.app İlginizi çeker mi? Saygılarımla, Akın Coşkun {{PHYSICAL_ADDRESS}} Aboneliği iptal et: {{UNSUBSCRIBE_URL}}",
          "language": "tr",
          "personalization_signals": ["TR şirketi", "teknoloji sektörü"],
          "highlighted_products": ["RivalRadar", "AI Chatbot Platform"],
          "warnings": []
        }
        """;

    public String complete(String agentName, String promptVersion,
                           String systemPrompt, String userPrompt,
                           Company company) {

        if (mockEnabled) {
            log.info("[MOCK LLM] Returning fixture response for agent={}", agentName);
            return agentName.equals("writer") ? MOCK_WRITER_RESPONSE : MOCK_ANALYZER_RESPONSE;
        }

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
