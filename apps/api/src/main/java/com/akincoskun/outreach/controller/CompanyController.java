package com.akincoskun.outreach.controller;

import com.akincoskun.outreach.domain.Company;
import com.akincoskun.outreach.domain.CompanyStatus;
import com.akincoskun.outreach.dto.CompanyDiscoverRequest;
import com.akincoskun.outreach.dto.CompanyResponse;
import com.akincoskun.outreach.exception.ResourceNotFoundException;
import com.akincoskun.outreach.mapper.CompanyMapper;
import com.akincoskun.outreach.repository.CompanyRepository;
import com.akincoskun.outreach.service.discovery.CompanyDiscoveryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyDiscoveryService discoveryService;
    private final CompanyRepository companyRepository;
    private final CompanyMapper companyMapper;

    @PostMapping("/discover")
    public ResponseEntity<CompanyResponse> discover(@Valid @RequestBody CompanyDiscoverRequest request) {
        Company company = discoveryService.discoverOrSkip(request);
        return ResponseEntity.ok(companyMapper.toResponse(company));
    }

    @PostMapping("/import-csv")
    public ResponseEntity<List<CompanyResponse>> importCsv(@Valid @RequestBody List<CompanyDiscoverRequest> rows) {
        List<Company> saved = discoveryService.importFromCsv(rows);
        return ResponseEntity.ok(companyMapper.toResponseList(saved));
    }

    @GetMapping
    public ResponseEntity<Page<CompanyResponse>> list(
            @RequestParam(required = false) CompanyStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<Company> page = status != null
            ? companyRepository.findAllByStatus(status, pageable)
            : companyRepository.findAll(pageable);
        return ResponseEntity.ok(page.map(companyMapper::toResponse));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CompanyResponse> getById(@PathVariable UUID id) {
        Company company = companyRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Company", id));
        return ResponseEntity.ok(companyMapper.toResponse(company));
    }

    @PostMapping("/{id}/blacklist")
    @Transactional
    public ResponseEntity<CompanyResponse> blacklist(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "manual") String reason) {
        Company company = companyRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Company", id));
        company.setStatus(CompanyStatus.BLACKLISTED);
        company.setStatusReason(reason);
        companyRepository.save(company);
        return ResponseEntity.ok(companyMapper.toResponse(company));
    }
}
