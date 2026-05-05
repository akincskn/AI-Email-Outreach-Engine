package com.akincoskun.outreach.dto;

import java.time.Instant;
import java.util.UUID;

public record EmailSendResponse(
    UUID id,
    UUID draftId,
    UUID companyId,
    String companyName,
    String toEmail,
    String subject,
    String status,
    int retryCount,
    Instant queuedAt,
    Instant sentAt,
    Instant failedAt,
    boolean hasOpen,
    boolean hasReply
) {}
