package com.akincoskun.outreach.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_opens")
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class EmailOpen {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "send_id", nullable = false)
    private EmailSend send;

    @Column(nullable = false)
    private Instant openedAt;

    private String userAgent;

    @Column(length = 2)
    private String ipCountryCode;
}
