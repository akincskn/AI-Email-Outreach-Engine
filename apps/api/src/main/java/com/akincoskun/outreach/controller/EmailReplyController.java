package com.akincoskun.outreach.controller;

import com.akincoskun.outreach.domain.EmailReply;
import com.akincoskun.outreach.dto.EmailReplyResponse;
import com.akincoskun.outreach.exception.ResourceNotFoundException;
import com.akincoskun.outreach.mapper.EmailReplyMapper;
import com.akincoskun.outreach.repository.EmailReplyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/replies")
@RequiredArgsConstructor
public class EmailReplyController {

    private final EmailReplyRepository emailReplyRepository;
    private final EmailReplyMapper emailReplyMapper;

    @GetMapping("/unhandled")
    public ResponseEntity<Page<EmailReplyResponse>> unhandled(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
            emailReplyRepository.findAllByHandledFalseOrderByReceivedAtDesc(pageable)
                .map(emailReplyMapper::toResponse)
        );
    }

    @PutMapping("/{id}/mark-handled")
    @Transactional
    public ResponseEntity<EmailReplyResponse> markHandled(@PathVariable UUID id) {
        EmailReply reply = emailReplyRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("EmailReply", id));
        reply.setHandled(true);
        emailReplyRepository.save(reply);
        return ResponseEntity.ok(emailReplyMapper.toResponse(reply));
    }
}
