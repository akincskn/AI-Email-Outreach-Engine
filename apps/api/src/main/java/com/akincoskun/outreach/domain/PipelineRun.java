package com.akincoskun.outreach.domain;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "pipeline_runs")
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class PipelineRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String workflowName;

    private String n8nExecutionId;

    @Column(nullable = false)
    private Instant startedAt;

    private Instant completedAt;
    private Integer durationMs;

    @Column(nullable = false)
    private String status;

    private int itemsProcessed;
    private int itemsSucceeded;
    private int itemsFailed;
    private String errorSummary;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;
}
