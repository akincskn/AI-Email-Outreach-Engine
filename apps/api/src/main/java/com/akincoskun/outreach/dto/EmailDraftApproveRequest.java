package com.akincoskun.outreach.dto;

import jakarta.validation.constraints.Size;

public record EmailDraftApproveRequest(
    @Size(max = 255)
    String editedSubject,

    String editedBodyHtml,
    String editedBodyText
) {}
