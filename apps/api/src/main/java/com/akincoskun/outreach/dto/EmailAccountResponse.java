package com.akincoskun.outreach.dto;

import java.time.Instant;
import java.util.UUID;

public record EmailAccountResponse(
    UUID id,
    UUID companyId,
    String email,
    String prefixType,
    String extractedFrom,
    boolean validFormat,
    boolean generic,
    Instant extractedAt
) {}
