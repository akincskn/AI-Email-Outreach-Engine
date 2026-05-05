package com.akincoskun.outreach.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_bounces")
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class EmailBounce {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "send_id", nullable = false)
    private EmailSend send;

    @Column(nullable = false)
    private String bounceType;

    private String bounceReason;
    private String bounceCode;

    @Column(columnDefinition = "TEXT")
    private String rawMessage;

    @Column(nullable = false)
    private Instant detectedAt;
}
