package com.akincoskun.outreach.domain;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "companies")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class Company extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String domain;

    @Column(nullable = false)
    private String name;

    private String websiteUrl;

    @Column(nullable = false)
    private String source;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> sourceMetadata;

    @Column(nullable = false)
    private Instant discoveredAt;

    @Column(length = 2)
    private String countryCode;

    private String city;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> analysis;

    private Instant analysisAt;
    private String analysisVersion;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private CompanyStatus status;

    private String statusReason;

    private Instant archivedAt;
    private Instant deletedAt;
}
