package com.akincoskun.outreach.controller;

import com.akincoskun.outreach.domain.JobType;
import com.akincoskun.outreach.dto.JobStatusResponse;
import com.akincoskun.outreach.dto.PipelineRunResult;
import com.akincoskun.outreach.service.AsyncPipelineRunner;
import com.akincoskun.outreach.service.PipelineJobService;
import com.akincoskun.outreach.service.PipelineOrchestratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Görev 10 / 10.2 — dashboard pipeline trigger. Replaces the N8N workflow: Akın
 * hits "Run Pipeline" on a single filter (synchronous), or "Run All Active"
 * which now runs asynchronously (a "Run All" takes 5-13 min and would otherwise
 * trip Next.js's 60s server-action timeout). The async flow returns a jobId the
 * dashboard polls via {@code GET /pipeline/jobs/{id}}.
 */
@RestController
@RequestMapping("/api/v1/pipeline")
@RequiredArgsConstructor
public class PipelineController {

    private final PipelineOrchestratorService pipelineOrchestratorService;
    private final AsyncPipelineRunner asyncPipelineRunner;
    private final PipelineJobService pipelineJobService;

    @PostMapping("/run/{filterId}")
    public ResponseEntity<PipelineRunResult> runPipeline(@PathVariable UUID filterId) {
        return ResponseEntity.ok(pipelineOrchestratorService.runForFilter(filterId));
    }

    /**
     * Görev 10.2 — "Run All Active" button. Creates a PENDING job, kicks off the
     * background run (fire-and-forget), and returns 202 + jobId immediately. The
     * dashboard polls {@link #getJob} until COMPLETED/FAILED.
     */
    @PostMapping("/run-all-async")
    public ResponseEntity<Map<String, UUID>> runAllAsync() {
        UUID jobId = pipelineJobService.createJob(JobType.RUN_ALL);
        asyncPipelineRunner.runAll(jobId);
        return ResponseEntity.accepted().body(Map.of("jobId", jobId));
    }

    /** Görev 10.2 — poll a single job's status, progress, and (on completion) result. */
    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<JobStatusResponse> getJob(@PathVariable UUID jobId) {
        return ResponseEntity.ok(pipelineJobService.getJob(jobId));
    }

    /** Görev 10.2 — the last 20 jobs, most recent first. */
    @GetMapping("/jobs")
    public ResponseEntity<List<JobStatusResponse>> recentJobs() {
        return ResponseEntity.ok(pipelineJobService.recentJobs());
    }
}
