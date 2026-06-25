package com.akincoskun.outreach.service;

import com.akincoskun.outreach.domain.JobStatus;
import com.akincoskun.outreach.domain.JobType;
import com.akincoskun.outreach.domain.PipelineJob;
import com.akincoskun.outreach.dto.JobStatusResponse;
import com.akincoskun.outreach.dto.RunAllResult;
import com.akincoskun.outreach.exception.ResourceNotFoundException;
import com.akincoskun.outreach.repository.PipelineJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Görev 10.2 — lifecycle of async pipeline jobs. Every mutation commits in its
 * own short transaction so the polling {@code GET /pipeline/jobs/{id}} (a
 * separate request, on a separate thread) always reads the latest progress while
 * the long-running @Async worker is still mid-run.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PipelineJobService {

    private final PipelineJobRepository jobRepository;

    /** Inserts a PENDING job and returns its id. The @Async worker takes it from here. */
    @Transactional
    public UUID createJob(JobType jobType) {
        Instant now = Instant.now();
        PipelineJob job = PipelineJob.builder()
            .id(UUID.randomUUID())
            .jobType(jobType)
            .status(JobStatus.PENDING)
            .startedAt(now)
            .createdAt(now)
            .build();
        jobRepository.save(job);
        log.info("Created {} job {}", jobType, job.getId());
        return job.getId();
    }

    /** Flips PENDING → RUNNING when the worker actually starts. */
    @Transactional
    public void markRunning(UUID jobId, String message) {
        PipelineJob job = get(jobId);
        job.setStatus(JobStatus.RUNNING);
        job.setProgressMessage(message);
    }

    /** Updates the human-readable progress line, e.g. "Pipeline 3/5: TR Marketing". */
    @Transactional
    public void updateProgress(UUID jobId, String message) {
        PipelineJob job = get(jobId);
        job.setProgressMessage(message);
        log.info("Job {} progress: {}", jobId, message);
    }

    @Transactional
    public void markCompleted(UUID jobId, RunAllResult result) {
        PipelineJob job = get(jobId);
        job.setStatus(JobStatus.COMPLETED);
        job.setResultJson(result);
        job.setProgressMessage("Tamamlandı");
        job.setCompletedAt(Instant.now());
        log.info("Job {} COMPLETED: {} drafts across {} filters",
            jobId, result.totalDrafts(), result.totalFilters());
    }

    @Transactional
    public void markFailed(UUID jobId, String error) {
        PipelineJob job = get(jobId);
        job.setStatus(JobStatus.FAILED);
        job.setErrorMessage(error);
        job.setCompletedAt(Instant.now());
        log.error("Job {} FAILED: {}", jobId, error);
    }

    @Transactional(readOnly = true)
    public JobStatusResponse getJob(UUID jobId) {
        return JobStatusResponse.from(get(jobId));
    }

    @Transactional(readOnly = true)
    public List<JobStatusResponse> recentJobs() {
        return jobRepository.findTop20ByOrderByCreatedAtDesc().stream()
            .map(JobStatusResponse::from)
            .toList();
    }

    private PipelineJob get(UUID jobId) {
        return jobRepository.findById(jobId)
            .orElseThrow(() -> new ResourceNotFoundException("PipelineJob", jobId));
    }
}
