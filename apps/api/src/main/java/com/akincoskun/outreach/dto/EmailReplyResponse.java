package com.akincoskun.outreach.dto;

import java.time.Instant;
import java.util.UUID;

public record EmailReplyResponse(
    UUID id,
    UUID sendId,
    String companyName,
    String fromEmail,
    String subject,
    String bodyText,
    String classification,
    boolean handled,
    Instant receivedAt
) {}
