package com.akincoskun.outreach.dto;

public record AnalyticsSummaryResponse(
    long sentToday,
    long openedToday,
    long repliedToday,
    long bouncedToday,
    long pendingDrafts,
    long unhandledReplies,
    int dailyCap,
    int volumeUsedToday
) {}
