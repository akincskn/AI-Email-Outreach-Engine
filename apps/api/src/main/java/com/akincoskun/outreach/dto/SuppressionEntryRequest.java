package com.akincoskun.outreach.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SuppressionEntryRequest(
    @NotBlank @Email @Size(max = 255)
    String email,

    @NotBlank @Size(max = 64)
    String reason,

    @Size(max = 1024)
    String notes
) {}
