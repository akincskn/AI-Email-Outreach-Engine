package com.akincoskun.outreach.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;

@Entity
@Table(name = "email_accounts")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class EmailAccount extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String prefixType;

    private String extractedFrom;

    @Column(nullable = false)
    private boolean validFormat = true;

    @Column(nullable = false)
    private boolean generic = true;

    private String validationNote;

    @Column(nullable = false)
    private Instant extractedAt;

    private Instant deletedAt;
}
