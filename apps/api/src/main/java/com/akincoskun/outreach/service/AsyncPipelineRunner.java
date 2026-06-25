package com.akincoskun.outreach.service;

import com.akincoskun.outreach.dto.RunAllResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Görev 10.2 — fire-and-forget worker for the "Run All" button. A "Run All" can
 * take 5-13 minutes (Apify + OSM + two LLM calls per company), which trips
 * Next.js's 60s server-action fetch timeout if run synchronously. Instead the
 * controller creates a PENDING job, kicks off {@link #runAll}, and the dashboard
 * polls the job for progress.
 *
 * <p>Separate from {@link PipelineOrchestratorService} on purpose: {@code @Async}
 * only works through Spring's proxy, so the async entry point must be invoked
 * from a <em>different</em> bean (no self-invocation). It also keeps the
 * orchestrator's pure unit tests free of job/async concerns.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AsyncPipelineRunner {

    private final PipelineOrchestratorService orchestrator;
    private final PipelineJobService jobService;

    /**
     * Runs every active filter on a background thread, publishing per-filter
     * progress to the job, then marks it COMPLETED (with the {@link RunAllResult})
     * or FAILED. Never throws to the caller — failures are recorded on the job.
     */
    @Async
    public void runAll(UUID jobId) {
        log.info("Async Run-All START job={}", jobId);
        try {
            jobService.markRunning(jobId, "Başlatılıyor…");
            RunAllResult result = orchestrator.runAllActive((index, total, filter) ->
                jobService.updateProgress(jobId,
                    "Pipeline %d/%d: %s".formatted(index + 1, total, filter.getName())));
            jobService.markCompleted(jobId, result);
        } catch (Exception e) {
            log.error("Async Run-All job={} failed", jobId, e);
            jobService.markFailed(jobId, e.getMessage());
        }
    }
}
