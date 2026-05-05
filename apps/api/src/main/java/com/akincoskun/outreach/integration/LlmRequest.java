package com.akincoskun.outreach.integration;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record LlmRequest(
    String model,
    List<Message> messages,
    @JsonProperty("max_tokens") int maxTokens,
    double temperature,
    @JsonProperty("response_format") ResponseFormat responseFormat
) {
    public record Message(String role, String content) {}

    public record ResponseFormat(String type) {
        public static ResponseFormat json() {
            return new ResponseFormat("json_object");
        }
    }

    public static LlmRequest of(String model, String systemPrompt, String userPrompt) {
        return new LlmRequest(
            model,
            List.of(
                new Message("system", systemPrompt),
                new Message("user", userPrompt)
            ),
            1024,
            0.3,
            ResponseFormat.json()
        );
    }
}
