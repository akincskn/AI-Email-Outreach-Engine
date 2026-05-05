package com.akincoskun.outreach.integration;

import com.akincoskun.outreach.repository.AiCallRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LlmRouterTest {

    @Mock GroqClient groqClient;
    @Mock GeminiClient geminiClient;
    @Mock AiCallRepository aiCallRepository;

    LlmRouter router;

    @BeforeEach
    void setUp() {
        when(groqClient.providerName()).thenReturn("groq");
        when(geminiClient.providerName()).thenReturn("gemini");
        router = new LlmRouter(groqClient, geminiClient, aiCallRepository);
    }

    @Test
    void completesSuccessfullyWithGroq() {
        when(groqClient.complete(anyString(), anyString()))
            .thenReturn(Mono.just("{\"result\":\"ok\"}"));

        String result = router.complete("analyzer", "v1", "system", "user", null);

        assertThat(result).isEqualTo("{\"result\":\"ok\"}");
        verify(geminiClient, never()).complete(anyString(), anyString());
        verify(aiCallRepository).save(any());
    }

    @Test
    void fallsBackToGeminiWhenGroqFails() {
        when(groqClient.complete(anyString(), anyString()))
            .thenReturn(Mono.error(new RuntimeException("Groq 429")));
        when(geminiClient.complete(anyString(), anyString()))
            .thenReturn(Mono.just("{\"result\":\"gemini_ok\"}"));

        String result = router.complete("writer", "v1", "system", "user", null);

        assertThat(result).isEqualTo("{\"result\":\"gemini_ok\"}");
        verify(aiCallRepository).save(any());
    }

    @Test
    void throwsLlmExceptionWhenBothFail() {
        when(groqClient.complete(anyString(), anyString()))
            .thenReturn(Mono.error(new RuntimeException("Groq down")));
        when(geminiClient.complete(anyString(), anyString()))
            .thenReturn(Mono.error(new RuntimeException("Gemini down")));

        assertThatThrownBy(() ->
            router.complete("analyzer", "v1", "system", "user", null)
        ).isInstanceOf(LlmException.class);

        verify(aiCallRepository).save(argThat(call -> !call.isSuccess()));
    }
}
