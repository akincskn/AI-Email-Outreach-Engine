package com.akincoskun.outreach.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit record for OSM places that were discovered but kept out of the
 * pipeline (e.g. no website to scrape emails from). Lets us dedupe repeat
 * discovery runs and report on what was dropped without polluting
 * {@code companies}.
 */
@Entity
@Table(name = "discovered_skipped")
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class DiscoveredSkipped {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Provider-unique id (e.g. {@code "node/123"} / {@code "way/456"}). */
    @Column(name = "osm_id", nullable = false, unique = true)
    private String osmId;

    @Column(nullable = false)
    private String name;

    @Column(name = "skip_reason", nullable = false, length = 32)
    private String skipReason;

    private String phone;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(length = 64)
    private String industry;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(nullable = false, length = 32)
    private String source;

    @Column(name = "discovered_at", nullable = false)
    private Instant discoveredAt;
}
