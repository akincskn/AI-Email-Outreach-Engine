package com.akincoskun.outreach.dto;

/**
 * Summary of a single dashboard-triggered pipeline run (Görev 10). Returned
 * synchronously so Akın sees, in one glance, what the run produced.
 *
 * <p>Bucket invariant on discovery:
 * {@code discovered = skippedNoWebsite + alreadyExists + newCompanies}.
 * Each new company then falls into exactly one of:
 * {@code skippedNoEmail + skippedNotTarget + skippedNoMatch + draftsCreated + errors}.
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
    long durationMs
) {}
