package com.akincoskun.outreach.controller;

import com.akincoskun.outreach.domain.DiscoveryFilter;
import com.akincoskun.outreach.dto.DiscoveryFilterRequest;
import com.akincoskun.outreach.dto.DiscoveryFilterResponse;
import com.akincoskun.outreach.exception.ResourceNotFoundException;
import com.akincoskun.outreach.mapper.DiscoveryFilterMapper;
import com.akincoskun.outreach.repository.DiscoveryFilterRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/discovery-filters")
@RequiredArgsConstructor
public class DiscoveryFilterController {

    private final DiscoveryFilterRepository discoveryFilterRepository;
    private final DiscoveryFilterMapper discoveryFilterMapper;

    @GetMapping
    public ResponseEntity<List<DiscoveryFilterResponse>> list() {
        return ResponseEntity.ok(
            discoveryFilterMapper.toResponseList(discoveryFilterRepository.findAll())
        );
    }

    @GetMapping("/active")
    public ResponseEntity<List<DiscoveryFilterResponse>> listActive() {
        return ResponseEntity.ok(
            discoveryFilterMapper.toResponseList(discoveryFilterRepository.findAllByActiveTrue())
        );
    }

    @PostMapping
    public ResponseEntity<DiscoveryFilterResponse> create(@Valid @RequestBody DiscoveryFilterRequest request) {
        DiscoveryFilter filter = DiscoveryFilter.builder()
            .name(request.name())
            .industry(request.industry())
            .countryCode(request.countryCode())
            .city(request.city())
            .keywords(request.keywords())
            .active(request.active())
            .build();
        return ResponseEntity.ok(discoveryFilterMapper.toResponse(discoveryFilterRepository.save(filter)));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<DiscoveryFilterResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody DiscoveryFilterRequest request) {
        DiscoveryFilter filter = discoveryFilterRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("DiscoveryFilter", id));
        filter.setName(request.name());
        filter.setIndustry(request.industry());
        filter.setCountryCode(request.countryCode());
        filter.setCity(request.city());
        filter.setKeywords(request.keywords());
        filter.setActive(request.active());
        return ResponseEntity.ok(discoveryFilterMapper.toResponse(discoveryFilterRepository.save(filter)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        if (!discoveryFilterRepository.existsById(id)) {
            throw new ResourceNotFoundException("DiscoveryFilter", id);
        }
        discoveryFilterRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
