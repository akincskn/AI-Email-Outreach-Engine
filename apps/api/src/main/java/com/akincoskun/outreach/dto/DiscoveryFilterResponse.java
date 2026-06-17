package com.akincoskun.outreach.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DiscoveryFilterResponse(
    UUID id,
    String name,
    String industry,
    String countryCode,
    String city,
    List<String> keywords,
    String targetProduct,
    boolean active,
    Instant createdAt
) {}
