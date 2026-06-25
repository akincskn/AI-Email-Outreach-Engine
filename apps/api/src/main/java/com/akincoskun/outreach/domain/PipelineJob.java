package com.akincoskun.outreach.domain;

import com.akincoskun.outreach.dto.RunAllResult;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.UUID;

/**
 * Görev 10.2 — a background "Run All" job. Created PENDING by the controller,
 * flipped to RUNNING by the @Async worker, then COMPLETED (with the
 * {@link RunAllResult} in {@code resultJson}) or FAILED. The dashboard polls
 * {@code GET /pipeline/jobs/{id}} on a 5s interval to render live progress.
 *
 * <p>Standalone entity (no {@link BaseEntity}) because a job has no
 * {@code updated_at} — its mutations are tracked by {@code status} +
 * {@code progressMessage} + {@code completedAt} instead.
 */
@Entity
@Table(name = "pipeline_jobs")
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class PipelineJob {

    @Id
    private UUID id;

    @Column(name = "job_type", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private JobType jobType;

    @Column(nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private JobStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "progress_message", columnDefinition = "TEXT")
    private String progressMessage;

    @Type(JsonBinaryType.class)
    @Column(name = "result_json", columnDefinition = "jsonb")
    private RunAllResult resultJson;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
