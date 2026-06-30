package com.akincoskun.outreach.service.email;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Sends transactional email through the Brevo HTTP API (POST /v3/smtp/email).
 *
 * <p>Replaces Gmail SMTP: Render free tier blocks outbound SMTP ports
 * (25/465/587) since Sept 2025, so JavaMailSender can never connect from prod.
 * Brevo speaks plain HTTPS on 443, which is unblocked. Same WebClient pattern as
 * {@code OsmClient}/{@code GroqClient}; no heavy SDK pulled in.
 *
 * <p>Any non-2xx response or transport failure (timeout, connection reset) is
 * surfaced as a {@link BrevoException} so {@code MailSendService}'s {@code
 * @Retryable} can retry transient failures.
 */
@Component
@Slf4j
public class BrevoMailClient {

    private static final int MAX_BUFFER_BYTES = 2 * 1024 * 1024;

    private final WebClient webClient;
    private final String senderEmail;
    private final String senderName;
    private final int timeoutSeconds;

    public BrevoMailClient(
        WebClient.Builder builder,
        @Value("${brevo.api-key:}") String apiKey,
        @Value("${brevo.sender-email:}") String senderEmail,
        @Value("${brevo.sender-name:}") String senderName,
        @Value("${brevo.timeout-seconds:30}") int timeoutSeconds
    ) {
        this.senderEmail = senderEmail;
        this.senderName = senderName;
        this.timeoutSeconds = timeoutSeconds;
        this.webClient = builder
            .baseUrl("https://api.brevo.com/v3")
            .defaultHeader("api-key", apiKey)
            .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .codecs(c -> c.defaultCodecs().maxInMemorySize(MAX_BUFFER_BYTES))
            .build();
    }

    /**
     * Hands one email to Brevo. Returns the Brevo-assigned messageId on success.
     *
     * @throws BrevoException on any 4xx/5xx response, timeout, or transport error
     */
    public String send(SendRequest req) {
        BrevoRequestBody body = new BrevoRequestBody(
            new Sender(senderName, senderEmail),
            List.of(new Recipient(req.toEmail(), req.toName())),
            req.subject(),
            req.htmlContent(),
            req.textContent(),
            req.headers()
        );

        try {
            BrevoResponse response = webClient.post()
                .uri("/smtp/email")
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp ->
                    resp.bodyToMono(String.class).defaultIfEmpty("")
                        .flatMap(b -> Mono.error(new BrevoException(
                            "Brevo API " + resp.statusCode().value() + ": " + b))))
                .bodyToMono(BrevoResponse.class)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .block();

            return response != null ? response.messageId() : null;
        } catch (BrevoException e) {
            throw e;
        } catch (Exception e) {
            // Timeout, connection reset, JSON decode, etc. — all transient/retryable.
            throw new BrevoException("Brevo send failed: " + e.getMessage(), e);
        }
    }

    /** Transport-agnostic send payload assembled by {@code MailSendService}. */
    public record SendRequest(
        String toEmail,
        String toName,
        String subject,
        String htmlContent,
        String textContent,
        Map<String, String> headers
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record BrevoRequestBody(
        Sender sender,
        List<Recipient> to,
        String subject,
        String htmlContent,
        String textContent,
        Map<String, String> headers
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record Sender(String name, String email) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record Recipient(String email, String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BrevoResponse(String messageId) {}
}
