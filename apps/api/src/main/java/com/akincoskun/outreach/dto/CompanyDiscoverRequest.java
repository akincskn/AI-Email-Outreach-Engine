package com.akincoskun.outreach.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record CompanyDiscoverRequest(
    @NotBlank @Size(max = 255)
    String domain,

    @NotBlank @Size(max = 255)
    String name,

    @Size(max = 512)
    String websiteUrl,

    @NotBlank @Pattern(regexp = "google_maps|crunchbase|manual_csv|sitemap_scrape")
    String source,

    Map<String, Object> sourceMetadata,

    @Pattern(regexp = "[A-Z]{2}")
    String countryCode,

    @Size(max = 128)
    String city
) {}
