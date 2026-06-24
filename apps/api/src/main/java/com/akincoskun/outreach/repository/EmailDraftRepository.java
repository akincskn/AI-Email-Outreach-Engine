package com.akincoskun.outreach.repository;

import com.akincoskun.outreach.domain.DraftStatus;
import com.akincoskun.outreach.domain.EmailDraft;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EmailDraftRepository extends JpaRepository<EmailDraft, UUID> {

    Page<EmailDraft> findAllByStatusOrderByCreatedAtDesc(DraftStatus status, Pageable pageable);

    List<EmailDraft> findAllByCompanyId(UUID companyId);

    long countByStatus(DraftStatus status);

    /**
     * Görev 12 — drafts created for a given discovery filter within a time window
     * (the Istanbul day boundary), used to enforce the filter's daily quota.
     */
    long countByCompany_DiscoveryFilterIdAndCreatedAtBetween(UUID filterId, Instant start, Instant end);
}
