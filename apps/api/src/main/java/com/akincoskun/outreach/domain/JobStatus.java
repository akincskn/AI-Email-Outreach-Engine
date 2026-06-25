package com.akincoskun.outreach.domain;

/** Lifecycle of an async pipeline job (Görev 10.2). */
public enum JobStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}
