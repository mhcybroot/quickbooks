package com.example.quickbooksimporter.service;

import java.util.List;
import java.util.Map;

public record QboCleanupDryRunPlan(
        int rootCount,
        int linkedCount,
        int operationCount,
        List<QboCleanupExecutionStep> orderedSteps,
        Map<QboCleanupEntityType, Long> linkedCountsByType) {
}
