package com.akincoskun.outreach.controller;

import com.akincoskun.outreach.dto.SuppressionEntryRequest;
import com.akincoskun.outreach.dto.SuppressionEntryResponse;
import com.akincoskun.outreach.mapper.SuppressionMapper;
import com.akincoskun.outreach.repository.SuppressionEntryRepository;
import com.akincoskun.outreach.service.email.SuppressionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/suppression")
@RequiredArgsConstructor
public class SuppressionController {

    private final SuppressionService suppressionService;
    private final SuppressionEntryRepository suppressionEntryRepository;
    private final SuppressionMapper suppressionMapper;

    @GetMapping
    public ResponseEntity<Page<SuppressionEntryResponse>> list(
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(
            suppressionEntryRepository.findAllByOrderBySuppressedAtDesc(pageable)
                .map(suppressionMapper::toResponse)
        );
    }

    @PostMapping
    public ResponseEntity<SuppressionEntryResponse> add(
            @Valid @RequestBody SuppressionEntryRequest request) {
        suppressionService.suppress(request.email(), request.reason(), null);
        return suppressionEntryRepository.findByEmail(request.email().toLowerCase())
            .map(suppressionMapper::toResponse)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.internalServerError().build());
    }
}
