package com.akincoskun.outreach.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GroqResponse(
    List<Choice> choices,
    Usage usage
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(Message message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(String content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
        int promptTokens,
        int completionTokens,
        int totalTokens
    ) {}

    public String firstContent() {
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("Groq returned empty choices");
        }
        return choices.get(0).message().content();
    }
}
