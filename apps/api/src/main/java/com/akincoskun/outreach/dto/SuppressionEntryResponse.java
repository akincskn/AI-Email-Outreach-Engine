package com.akincoskun.outreach.dto;

import java.time.Instant;
import java.util.UUID;

public record SuppressionEntryResponse(
    UUID id,
    String email,
    String reason,
    String notes,
    Instant suppressedAt,
    Instant expiresAt
) {}
