package com.akincoskun.outreach.dto;

import com.akincoskun.outreach.domain.PipelineJob;

import java.time.Instant;
import java.util.UUID;

/**
 * Görev 10.2 — what the dashboard polls for. While RUNNING, {@code result} is
 * null and {@code progressMessage} carries "Pipeline 3/5: …"; on COMPLETED the
 * full {@link RunAllResult} is populated; on FAILED {@code error} is set.
 */
public record JobStatusResponse(
    UUID id,
    String jobType,
    String status,
    Instant startedAt,
    Instant completedAt,
    String progressMessage,
    RunAllResult result,
    String error
) {

    public static JobStatusResponse from(PipelineJob job) {
        return new JobStatusResponse(
            job.getId(),
            job.getJobType().name(),
            job.getStatus().name(),
            job.getStartedAt(),
            job.getCompletedAt(),
            job.getProgressMessage(),
            job.getResultJson(),
            job.getErrorMessage()
        );
    }
}
