package com.akincoskun.outreach.repository;

import com.akincoskun.outreach.domain.EmailReply;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmailReplyRepository extends JpaRepository<EmailReply, UUID> {

    Page<EmailReply> findAllByHandledFalseOrderByReceivedAtDesc(Pageable pageable);

    List<EmailReply> findAllBySendId(UUID sendId);

    Optional<EmailReply> findByInReplyTo(String inReplyTo);

    long countByHandledFalse();
}
