package com.akincoskun.outreach.domain;

/** What an async pipeline job runs (Görev 10.2). */
public enum JobType {
    /** Every active filter, back-to-back (the "Run All Active" button). */
    RUN_ALL,
    /** A single discovery filter. */
    RUN_FILTER
}
