package com.akincoskun.outreach.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EmailDraftResponse(
    UUID id,
    UUID companyId,
    String companyName,
    String companyDomain,
    UUID emailAccountId,
    String toEmail,
    String subject,
    String bodyHtml,
    String bodyText,
    String language,
    String modelUsed,
    List<String> personalizationSignals,
    List<String> warnings,
    String status,
    String editedSubject,
    String editedBodyHtml,
    Instant createdAt,
    Instant approvedAt
) {}
