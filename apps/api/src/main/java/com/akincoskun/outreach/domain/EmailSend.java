package com.akincoskun.outreach.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "email_sends")
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class EmailSend extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "draft_id", nullable = false)
    private EmailDraft draft;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false)
    private String toEmail;

    @Column(nullable = false)
    private String fromEmail;

    @Column(nullable = false)
    private String subject;

    @Column(unique = true)
    private String messageId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private SendStatus status;

    private String smtpResponse;
    private String errorMessage;

    @Column(nullable = false)
    private int retryCount;

    private String trackingPixelToken;
    private String unsubscribeToken;

    @Column(nullable = false)
    private Instant queuedAt;

    private Instant sentAt;
    private Instant failedAt;
}
