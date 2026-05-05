package com.akincoskun.outreach.repository;

import com.akincoskun.outreach.domain.EmailSend;
import com.akincoskun.outreach.domain.SendStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmailSendRepository extends JpaRepository<EmailSend, UUID> {

    Optional<EmailSend> findByMessageId(String messageId);

    Optional<EmailSend> findByUnsubscribeToken(String token);

    Optional<EmailSend> findByTrackingPixelToken(String token);

    Page<EmailSend> findAllByStatusOrderBySentAtDesc(SendStatus status, Pageable pageable);

    @Query("SELECT COUNT(s) FROM EmailSend s WHERE s.sentAt >= :from AND s.sentAt < :to AND s.status = 'SENT'")
    long countSentBetween(Instant from, Instant to);

    @Query("SELECT COUNT(s) FROM EmailSend s WHERE s.status = 'BOUNCED' AND s.sentAt >= :from")
    long countBouncedSince(Instant from);

    List<EmailSend> findAllByStatus(SendStatus status);
}
