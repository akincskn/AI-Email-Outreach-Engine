package com.akincoskun.outreach.controller;

import com.akincoskun.outreach.domain.Company;
import com.akincoskun.outreach.domain.EmailAccount;
import com.akincoskun.outreach.exception.ResourceNotFoundException;
import com.akincoskun.outreach.repository.CompanyRepository;
import com.akincoskun.outreach.repository.EmailAccountRepository;
import com.akincoskun.outreach.service.agent.AnalyzerService;
import com.akincoskun.outreach.service.agent.WriterService;
import com.akincoskun.outreach.service.discovery.EmailExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
@Slf4j
public class AgentController {

    private final CompanyRepository companyRepository;
    private final EmailAccountRepository emailAccountRepository;
    private final EmailExtractionService emailExtractionService;
    private final AnalyzerService analyzerService;
    private final WriterService writerService;

    @PostMapping("/extract-emails")
    public ResponseEntity<Map<String, Object>> extractEmails(@RequestBody Map<String, String> body) {
        UUID companyId = UUID.fromString(body.get("companyId"));
        Company company = companyRepository.findById(companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Company", companyId));

        int count = emailExtractionService.extractAndSave(company).size();
        log.info("Email extraction for {}: {} addresses found", company.getDomain(), count);
        return ResponseEntity.ok(Map.of("companyId", companyId.toString(), "emailsFound", count));
    }

    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyze(@RequestBody Map<String, String> body) {
        UUID companyId = UUID.fromString(body.get("companyId"));
        Company company = companyRepository.findById(companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Company", companyId));

        Map<String, Object> analysis = analyzerService.analyze(company);
        log.info("Analysis complete for {}: status={}", company.getDomain(), company.getStatus());
        return ResponseEntity.ok(Map.of(
            "companyId", companyId.toString(),
            "status", company.getStatus().name(),
            "analysis", analysis
        ));
    }

    @PostMapping("/write")
    public ResponseEntity<Map<String, Object>> write(@RequestBody Map<String, String> body) {
        UUID companyId     = UUID.fromString(body.get("companyId"));
        UUID emailAccountId = UUID.fromString(body.get("emailAccountId"));

        Company company = companyRepository.findById(companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Company", companyId));
        EmailAccount account = emailAccountRepository.findById(emailAccountId)
            .orElseThrow(() -> new ResourceNotFoundException("EmailAccount", emailAccountId));

        var draft = writerService.write(company, account);
        log.info("Draft created for {}: draftId={}", company.getDomain(), draft.getId());
        return ResponseEntity.ok(Map.of(
            "draftId", draft.getId().toString(),
            "status", draft.getStatus().name()
        ));
    }
}
