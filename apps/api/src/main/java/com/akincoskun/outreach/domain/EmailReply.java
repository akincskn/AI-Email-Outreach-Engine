package com.akincoskun.outreach.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_replies")
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class EmailReply {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "send_id")
    private EmailSend send;

    @Column(nullable = false)
    private String fromEmail;

    private String subject;

    @Column(columnDefinition = "TEXT")
    private String bodyText;

    private String inReplyTo;

    private String classification;

    @Column(name = "is_handled", nullable = false)
    private boolean handled;

    @Column(nullable = false)
    private Instant receivedAt;
}
