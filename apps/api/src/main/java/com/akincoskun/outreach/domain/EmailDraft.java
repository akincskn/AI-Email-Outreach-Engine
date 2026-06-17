package com.akincoskun.outreach.domain;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "email_drafts")
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class EmailDraft extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "email_account_id", nullable = false)
    private EmailAccount emailAccount;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String bodyHtml;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String bodyText;

    @Column(nullable = false, length = 2)
    private String language;

    private String modelUsed;
    private String promptVersion;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private List<String> personalizationSignals;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private List<String> warnings;

    @Column(length = 64)
    private String matchedProductSlug;

    @Column(length = 64)
    private String secondaryProductSlug;

    @Column(precision = 3, scale = 2)
    private java.math.BigDecimal matchConfidence;

    @Column(columnDefinition = "TEXT")
    private String matchReasoning;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private DraftStatus status;

    private String editedSubject;

    @Column(columnDefinition = "TEXT")
    private String editedBodyHtml;

    @Column(columnDefinition = "TEXT")
    private String editedBodyText;

    private String rejectionReason;
    private Instant approvedAt;
    private Instant rejectedAt;
}
