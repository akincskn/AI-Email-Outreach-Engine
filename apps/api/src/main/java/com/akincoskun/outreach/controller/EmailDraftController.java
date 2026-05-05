package com.akincoskun.outreach.controller;

import com.akincoskun.outreach.domain.DraftStatus;
import com.akincoskun.outreach.domain.EmailDraft;
import com.akincoskun.outreach.dto.EmailDraftApproveRequest;
import com.akincoskun.outreach.dto.EmailDraftRejectRequest;
import com.akincoskun.outreach.dto.EmailDraftResponse;
import com.akincoskun.outreach.exception.BusinessException;
import com.akincoskun.outreach.exception.ResourceNotFoundException;
import com.akincoskun.outreach.mapper.EmailDraftMapper;
import com.akincoskun.outreach.repository.EmailDraftRepository;
import com.akincoskun.outreach.service.email.EmailSendOrchestrator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/drafts")
@RequiredArgsConstructor
public class EmailDraftController {

    private final EmailDraftRepository emailDraftRepository;
    private final EmailDraftMapper emailDraftMapper;
    private final EmailSendOrchestrator sendOrchestrator;

    @GetMapping("/pending")
    public ResponseEntity<Page<EmailDraftResponse>> pending(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
            emailDraftRepository.findAllByStatusOrderByCreatedAtDesc(DraftStatus.PENDING, pageable)
                .map(emailDraftMapper::toResponse)
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmailDraftResponse> getById(@PathVariable UUID id) {
        EmailDraft draft = emailDraftRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("EmailDraft", id));
        return ResponseEntity.ok(emailDraftMapper.toResponse(draft));
    }

    @PutMapping("/{id}/approve")
    @Transactional
    public ResponseEntity<EmailDraftResponse> approve(
            @PathVariable UUID id,
            @Valid @RequestBody EmailDraftApproveRequest request) {
        EmailDraft draft = emailDraftRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("EmailDraft", id));

        if (draft.getStatus() != DraftStatus.PENDING && draft.getStatus() != DraftStatus.EDITED) {
            throw new BusinessException("Draft is not in approvable state: " + draft.getStatus());
        }

        if (request.editedSubject() != null) draft.setEditedSubject(request.editedSubject());
        if (request.editedBodyHtml() != null) draft.setEditedBodyHtml(request.editedBodyHtml());
        if (request.editedBodyText() != null) draft.setEditedBodyText(request.editedBodyText());

        if (request.editedSubject() != null || request.editedBodyHtml() != null) {
            draft.setStatus(DraftStatus.EDITED);
        }

        draft.setApprovedAt(Instant.now());
        draft.setStatus(DraftStatus.APPROVED);
        emailDraftRepository.save(draft);

        sendOrchestrator.send(draft);

        return ResponseEntity.ok(emailDraftMapper.toResponse(draft));
    }

    @PutMapping("/{id}/reject")
    @Transactional
    public ResponseEntity<EmailDraftResponse> reject(
            @PathVariable UUID id,
            @Valid @RequestBody EmailDraftRejectRequest request) {
        EmailDraft draft = emailDraftRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("EmailDraft", id));

        if (draft.getStatus() != DraftStatus.PENDING && draft.getStatus() != DraftStatus.EDITED) {
            throw new BusinessException("Draft is not in rejectable state: " + draft.getStatus());
        }

        draft.setStatus(DraftStatus.REJECTED);
        draft.setRejectionReason(request.reason());
        draft.setRejectedAt(Instant.now());
        emailDraftRepository.save(draft);

        return ResponseEntity.ok(emailDraftMapper.toResponse(draft));
    }
}
