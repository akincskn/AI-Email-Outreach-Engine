package com.akincoskun.outreach.dto;

import java.util.List;

/**
 * Görev 12 — aggregate summary of a "Run All Active Filters" run. Akın hits one
 * button and gets a single glance at what every active filter produced, plus the
 * per-filter breakdown ({@link PipelineRunResult}).
 */
public record RunAllResult(
    int totalFilters,
    int totalDiscovered,
    int totalDrafts,
    int totalQuotaReached,
    int totalErrors,
    long durationMs,
    List<PipelineRunResult> perFilter
) {}
