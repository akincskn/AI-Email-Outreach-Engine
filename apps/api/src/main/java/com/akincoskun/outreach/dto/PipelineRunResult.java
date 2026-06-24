package com.akincoskun.outreach.dto;

import com.akincoskun.outreach.domain.DiscoveryFilter;

/**
 * Summary of a single dashboard-triggered pipeline run (Görev 10). Returned
 * synchronously so Akın sees, in one glance, what the run produced.
 *
 * <p>Bucket invariant on discovery:
 * {@code discovered = skippedNoWebsite + alreadyExists + newCompanies}.
 * Each new company then falls into exactly one of:
 * {@code skippedNoEmail + skippedNotTarget + skippedNoMatch + draftsCreated + errors}.
 *
 * <p>Görev 12 adds two terminal states reported by the "Run All" flow:
 * {@code quotaReached} (filter hit its daily draft cap, skipped without
 * discovering) and {@code error} (the run threw; message captured).
 */
public record PipelineRunResult(
    String filterId,
    String filterName,
    int discovered,        // total places returned by the data source
    int skippedNoWebsite,  // had no website → audited, not added
    int alreadyExists,     // already in companies or already audited as skipped
    int newCompanies,      // newly added to companies this run
    int skippedNoEmail,    // new company with no generic email found
    int skippedNotTarget,  // analyzer blacklisted (not a target country / skip_reason)
    int skippedNoMatch,    // matcher found no product above the confidence threshold
    int draftsCreated,     // PENDING drafts ready for approval
    int errors,            // companies that threw during processing
    long durationMs,
    boolean quotaReached,  // daily quota already met → run skipped before discovery
    String error           // non-null when the run failed outright
) {

    /** The filter already hit its daily draft quota; nothing was discovered. */
    public static PipelineRunResult quotaReached(DiscoveryFilter filter) {
        return new PipelineRunResult(filter.getId().toString(), filter.getName(),
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0L, true, null);
    }

    /** The run threw; {@code message} is surfaced to the dashboard. */
    public static PipelineRunResult failed(DiscoveryFilter filter, String message) {
        return new PipelineRunResult(filter.getId().toString(), filter.getName(),
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0L, false, message);
    }
}
