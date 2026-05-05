package com.akincoskun.outreach.integration;

import com.akincoskun.outreach.domain.AiCall;
import com.akincoskun.outreach.domain.Company;
import com.akincoskun.outreach.repository.AiCallRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    public String complete(String agentName, String promptVersion,
                           String systemPrompt, String userPrompt,
                           Company company) {

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
