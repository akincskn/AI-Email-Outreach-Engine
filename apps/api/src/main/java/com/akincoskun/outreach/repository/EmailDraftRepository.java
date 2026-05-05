package com.akincoskun.outreach.repository;

import com.akincoskun.outreach.domain.DraftStatus;
import com.akincoskun.outreach.domain.EmailDraft;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EmailDraftRepository extends JpaRepository<EmailDraft, UUID> {

    Page<EmailDraft> findAllByStatusOrderByCreatedAtDesc(DraftStatus status, Pageable pageable);

    List<EmailDraft> findAllByCompanyId(UUID companyId);

    long countByStatus(DraftStatus status);
}
