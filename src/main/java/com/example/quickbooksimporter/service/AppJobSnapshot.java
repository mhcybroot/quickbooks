package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.AppJobStatus;
import com.example.quickbooksimporter.domain.AppJobType;
import java.time.Instant;

public record AppJobSnapshot(
        Long jobId,
        AppJobType type,
        AppJobStatus status,
        String description,
        int totalUnits,
        int completedUnits,
        double progressValue,
        String percentLabel,
        String summaryMessage,
        String resultPayload,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt) {
}
