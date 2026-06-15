package com.akincoskun.outreach.controller;

import com.akincoskun.outreach.domain.EmailSend;
import com.akincoskun.outreach.domain.SendStatus;
import com.akincoskun.outreach.dto.EmailSendResponse;
import com.akincoskun.outreach.exception.ResourceNotFoundException;
import com.akincoskun.outreach.mapper.EmailSendMapper;
import com.akincoskun.outreach.repository.EmailSendRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sends")
@RequiredArgsConstructor
public class EmailSendController {

    private final EmailSendRepository emailSendRepository;
    private final EmailSendMapper emailSendMapper;

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<Page<EmailSendResponse>> list(
            @RequestParam(required = false) SendStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<EmailSend> page = status != null
            ? emailSendRepository.findAllByStatusOrderBySentAtDesc(status, pageable)
            : emailSendRepository.findAll(pageable);
        return ResponseEntity.ok(page.map(emailSendMapper::toResponse));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<EmailSendResponse> getById(@PathVariable UUID id) {
        EmailSend send = emailSendRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("EmailSend", id));
        return ResponseEntity.ok(emailSendMapper.toResponse(send));
    }
}
