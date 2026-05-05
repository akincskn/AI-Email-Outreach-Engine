package com.akincoskun.outreach.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "suppression_list")
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class SuppressionEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_send_id")
    private EmailSend sourceSend;

    private String notes;

    @Column(nullable = false)
    private Instant suppressedAt;

    private Instant expiresAt;
}
