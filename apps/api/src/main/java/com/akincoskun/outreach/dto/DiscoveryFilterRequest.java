package com.akincoskun.outreach.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record DiscoveryFilterRequest(
    @NotBlank @Size(max = 128)
    String name,

    @Size(max = 64)
    String industry,

    @Pattern(regexp = "[A-Z]{2}")
    String countryCode,

    @Size(max = 128)
    String city,

    List<@Size(max = 64) String> keywords,

    boolean active
) {}
