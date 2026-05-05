package com.akincoskun.outreach.service.email;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SlackNotificationService {

    private final WebClient.Builder webClientBuilder;

    @Value("${app.slack.webhook-url:}")
    private String webhookUrl;

    @Async
    public void sendAsync(String type, String message) {
        if (webhookUrl == null || webhookUrl.isBlank() || webhookUrl.startsWith("YOUR_")) {
            log.debug("Slack webhook not configured, skipping notification: {}", message);
            return;
        }
        try {
            String text = buildText(type, message);
            webClientBuilder.build()
                .post()
                .uri(webhookUrl)
                .bodyValue(Map.of("text", text))
                .retrieve()
                .toBodilessEntity()
                .block();
            log.debug("Slack notification sent: {}", text);
        } catch (Exception e) {
            log.warn("Slack notification failed: {}", e.getMessage());
        }
    }

    private String buildText(String type, String message) {
        return switch (type) {
            case "draft"   -> "📝 " + message;
            case "reply"   -> "🎉 " + message;
            case "bounce"  -> "⚠️ " + message;
            case "error"   -> "🚨 " + message;
            default        -> message;
        };
    }
}
