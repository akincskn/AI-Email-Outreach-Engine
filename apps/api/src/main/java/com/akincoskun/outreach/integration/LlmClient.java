package com.akincoskun.outreach.integration;

import reactor.core.publisher.Mono;

public interface LlmClient {

    String providerName();

    Mono<String> complete(String systemPrompt, String userPrompt);
}
