package com.akincoskun.outreach.dto;

/**
 * Output of the Matcher Agent: which product(s) best fit a company.
 *
 * <p>{@code matched == false} means the primary confidence fell below the
 * threshold (NO_MATCH) and the company should be skipped, not written to.
 * In that case the secondary fields may be null.
 */
public record MatchResult(
    String primaryProductSlug,
    double primaryConfidence,
    String secondaryProductSlug,
    Double secondaryConfidence,
    String reasoning,
    boolean matched
) {}
