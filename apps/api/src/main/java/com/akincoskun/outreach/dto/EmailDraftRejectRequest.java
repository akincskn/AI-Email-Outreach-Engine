package com.akincoskun.outreach.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailDraftRejectRequest(
    @NotBlank @Size(max = 512)
    String reason
) {}
