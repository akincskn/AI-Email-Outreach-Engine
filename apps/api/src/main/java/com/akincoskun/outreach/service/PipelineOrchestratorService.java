package com.akincoskun.outreach.service;

import com.akincoskun.outreach.domain.Company;
import com.akincoskun.outreach.domain.CompanyStatus;
import com.akincoskun.outreach.domain.DiscoveryFilter;
import com.akincoskun.outreach.domain.EmailAccount;
import com.akincoskun.outreach.dto.MatchResult;
import com.akincoskun.outreach.dto.PipelineRunResult;
import com.akincoskun.outreach.dto.RunAllResult;
import com.akincoskun.outreach.exception.ResourceNotFoundException;
import com.akincoskun.outreach.repository.DiscoveryFilterRepository;
import com.akincoskun.outreach.repository.EmailDraftRepository;
import com.akincoskun.outreach.service.agent.AnalyzerService;
import com.akincoskun.outreach.service.agent.MatcherService;
import com.akincoskun.outreach.service.agent.WriterService;
import com.akincoskun.outreach.service.discovery.CompanyDiscoveryService;
import com.akincoskun.outreach.service.discovery.EmailExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Görev 10 — orchestrates the full discovery → extract → analyze → match →
 * write pipeline for a single discovery filter, triggered manually from the
 * dashboard (replaces the old N8N workflow).
 *
 * <p>Deliberately NOT {@code @Transactional}: a run does many slow network
 * calls (OSM, page scraping, two LLM calls per company) and can take a minute
 * or more. A single long transaction would hold a DB connection and locks the
 * whole time. Instead, discovery commits in its own transaction and each agent
 * step commits per-company. One company failing is caught and counted so the
 * rest of the run continues.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PipelineOrchestratorService {

    /** All quota math is done against the Istanbul calendar day (Akın's timezone). */
    private static final ZoneId TZ = ZoneId.of("Europe/Istanbul");

    private final DiscoveryFilterRepository discoveryFilterRepository;
    private final EmailDraftRepository emailDraftRepository;
    private final CompanyDiscoveryService companyDiscoveryService;
    private final EmailExtractionService emailExtractionService;
    private final AnalyzerService analyzerService;
    private final MatcherService matcherService;
    private final WriterService writerService;

    /**
     * Görev 12 — runs every active filter in turn, isolating failures so one bad
     * filter cannot abort the rest, and returns an aggregate summary. Quota is
     * enforced per filter inside {@link #runForFilter}.
     */
    public RunAllResult runAllActive() {
        long start = System.currentTimeMillis();
        List<DiscoveryFilter> activeFilters = discoveryFilterRepository.findAllByActiveTrue();
        log.info("Run-All START: {} active filter(s)", activeFilters.size());

        List<PipelineRunResult> results = new ArrayList<>();
        for (DiscoveryFilter filter : activeFilters) {
            try {
                results.add(runForFilter(filter.getId()));
            } catch (Exception e) {
                log.error("Run-All: filter '{}' failed", filter.getName(), e);
                results.add(PipelineRunResult.failed(filter, e.getMessage()));
            }
        }

        int totalDiscovered = results.stream().mapToInt(PipelineRunResult::discovered).sum();
        int totalDrafts = results.stream().mapToInt(PipelineRunResult::draftsCreated).sum();
        int totalQuotaReached = (int) results.stream().filter(PipelineRunResult::quotaReached).count();
        int totalErrors = (int) results.stream().filter(r -> r.error() != null).count();
        long durationMs = System.currentTimeMillis() - start;

        RunAllResult summary = new RunAllResult(
            activeFilters.size(), totalDiscovered, totalDrafts,
            totalQuotaReached, totalErrors, durationMs, results);
        log.info("Run-All DONE: {}", summary);
        return summary;
    }

    public PipelineRunResult runForFilter(UUID filterId) {
        long start = System.currentTimeMillis();

        DiscoveryFilter filter = discoveryFilterRepository.findById(filterId)
            .orElseThrow(() -> new ResourceNotFoundException("DiscoveryFilter", filterId));

        log.info("Pipeline START filter='{}' ({})", filter.getName(), filterId);

        // Daily quota gate: count drafts already created for this filter today and
        // bail before any (paid) discovery if the cap is met.
        long draftsCreatedToday = countDraftsToday(filter.getId());
        if (draftsCreatedToday >= filter.getDailyQuota()) {
            log.info("Filter '{}' quota reached today ({}/{}), skipping",
                filter.getName(), draftsCreatedToday, filter.getDailyQuota());
            return PipelineRunResult.quotaReached(filter);
        }
        int remainingSlots = (int) (filter.getDailyQuota() - draftsCreatedToday);

        CompanyDiscoveryService.DiscoveryOutcome discovery =
            companyDiscoveryService.discoverFromFilterDetailed(filter);

        int skippedNoEmail = 0;
        int skippedNotTarget = 0;
        int skippedNoMatch = 0;
        int draftsCreated = 0;
        int errors = 0;

        for (Company company : discovery.newCompanies()) {
            // Respect the remaining daily quota: once filled, stop writing drafts.
            if (draftsCreated >= remainingSlots) {
                log.info("Pipeline '{}': daily quota slots filled ({}), stopping draft creation",
                    filter.getName(), remainingSlots);
                break;
            }
            try {
                // a. Emails — no generic address means nothing to send to.
                List<EmailAccount> accounts = emailExtractionService.extractAndSave(company);
                if (accounts.isEmpty()) {
                    log.info("Pipeline '{}': no generic email, skipping", company.getDomain());
                    skippedNoEmail++;
                    continue;
                }

                // b. Analyze — analyzer blacklists non-target countries / NGOs.
                analyzerService.analyze(company);
                if (company.getStatus() == CompanyStatus.BLACKLISTED) {
                    log.info("Pipeline '{}': blacklisted ({}), skipping",
                        company.getDomain(), company.getStatusReason());
                    skippedNotTarget++;
                    continue;
                }

                // c. Match — the gate: below-threshold companies are SKIPPED and
                //    do not get a draft.
                MatchResult match = matcherService.match(company, filter.getTargetProduct());
                if (!match.matched()) {
                    log.info("Pipeline '{}': no product match, skipping", company.getDomain());
                    skippedNoMatch++;
                    continue;
                }

                // d. Write — PENDING draft awaiting Akın's approval.
                writerService.write(company, accounts.get(0));
                draftsCreated++;
            } catch (Exception e) {
                errors++;
                log.error("Pipeline '{}' failed: {}", company.getDomain(), e.getMessage(), e);
            }
        }

        long durationMs = System.currentTimeMillis() - start;
        PipelineRunResult result = new PipelineRunResult(
            filter.getId().toString(),
            filter.getName(),
            discovery.total(),
            discovery.newNoWebsite(),
            discovery.alreadyKnown() + discovery.alreadySkipped(),
            discovery.newCompanies().size(),
            skippedNoEmail,
            skippedNotTarget,
            skippedNoMatch,
            draftsCreated,
            errors,
            durationMs,
            false,
            null
        );
        log.info("Pipeline DONE filter='{}': {}", filter.getName(), result);
        return result;
    }

    /** Drafts created for {@code filterId} within today's Istanbul calendar day. */
    private long countDraftsToday(UUID filterId) {
        LocalDate today = LocalDate.now(TZ);
        return emailDraftRepository.countByCompany_DiscoveryFilterIdAndCreatedAtBetween(
            filterId,
            today.atStartOfDay(TZ).toInstant(),
            today.plusDays(1).atStartOfDay(TZ).toInstant());
    }
}
