package com.akincoskun.outreach.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record CompanyResponse(
    UUID id,
    String domain,
    String name,
    String websiteUrl,
    String source,
    Instant discoveredAt,
    String countryCode,
    String city,
    Map<String, Object> analysis,
    Instant analysisAt,
    String status,
    String statusReason,
    Instant createdAt
) {}
