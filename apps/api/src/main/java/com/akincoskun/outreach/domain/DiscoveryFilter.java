package com.akincoskun.outreach.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "discovery_filters")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class DiscoveryFilter {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    private String industry;

    @Column(length = 2)
    private String countryCode;

    /**
     * Single city — DEPRECATED in favour of {@link #cities} (Görev 12). Kept for
     * backward compatibility: OSM discovery still keys off it. New filters should
     * populate {@code cities} instead.
     */
    private String city;

    /**
     * Cities to search in one run (Görev 12 — Apify multi-city scale). Persisted
     * as PostgreSQL {@code TEXT[]}. Falls back to {@link #city} when null/empty.
     */
    @Column(name = "cities", columnDefinition = "TEXT[]")
    private List<String> cities;

    @Column(columnDefinition = "TEXT[]")
    private List<String> keywords;

    /**
     * Max new drafts this filter may produce per day (Görev 12). The pipeline
     * orchestrator counts today's drafts per filter and stops at this cap so the
     * "Run All" button cannot exceed the Gmail-safe daily volume.
     */
    @Column(name = "daily_quota", nullable = false)
    @Builder.Default
    private int dailyQuota = 4;

    /**
     * Product slug this filter biases the Matcher toward (Görev 4). Nullable for
     * backward compatibility with legacy generic filters that target no product.
     */
    @Column(name = "target_product", length = 64)
    private String targetProduct;

    /**
     * Which provider this filter discovers from (Görev 11). Defaults to OSM for
     * backward compatibility; the V23 migration flips TR Property Management to
     * Apify. Persisted as the lowercase code via {@link DiscoverySourceConverter}.
     */
    @Convert(converter = DiscoverySourceConverter.class)
    @Column(name = "source", nullable = false, length = 32)
    @Builder.Default
    private DiscoverySource source = DiscoverySource.OSM;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
