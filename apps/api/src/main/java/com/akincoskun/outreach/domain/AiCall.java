package com.akincoskun.outreach.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_calls")
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class AiCall {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(nullable = false)
    private String agentName;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String model;

    private String promptVersion;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer durationMs;

    @Column(nullable = false)
    private boolean success;

    private String errorMessage;

    @Column(length = 512)
    private String promptSnippet;

    @Column(length = 512)
    private String responseSnippet;

    @Column(nullable = false)
    private Instant createdAt;
}
