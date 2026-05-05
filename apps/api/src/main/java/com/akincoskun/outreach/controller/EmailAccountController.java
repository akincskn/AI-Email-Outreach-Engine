package com.akincoskun.outreach.controller;

import com.akincoskun.outreach.dto.EmailAccountResponse;
import com.akincoskun.outreach.mapper.EmailAccountMapper;
import com.akincoskun.outreach.repository.EmailAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/email-accounts")
@RequiredArgsConstructor
public class EmailAccountController {

    private final EmailAccountRepository emailAccountRepository;
    private final EmailAccountMapper emailAccountMapper;

    @GetMapping("/by-company/{companyId}")
    public ResponseEntity<List<EmailAccountResponse>> byCompany(@PathVariable UUID companyId) {
        return ResponseEntity.ok(
            emailAccountMapper.toResponseList(
                emailAccountRepository.findAllByCompanyId(companyId)
            )
        );
    }
}
