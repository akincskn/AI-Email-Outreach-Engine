package com.akincoskun.outreach.service.email;

/**
 * Raised when a transactional email cannot be handed off to Brevo.
 *
 * <p>Replaces the old {@code SmtpException} after the Gmail SMTP → Brevo HTTP API
 * migration (Render free tier blocks outbound SMTP ports 25/465/587 since Sept
 * 2025). {@code MailSendService.send()} is {@code @Retryable} on this type, so a
 * transient Brevo hiccup (429, 5xx, timeout) gets retried; a permanent failure
 * (401 auth, bad address) still retries but fails fast and surfaces the same
 * error — acceptable for the low send volume here.
 */
public class BrevoException extends RuntimeException {
    public BrevoException(String message) {
        super(message);
    }

    public BrevoException(String message, Throwable cause) {
        super(message, cause);
    }
}
