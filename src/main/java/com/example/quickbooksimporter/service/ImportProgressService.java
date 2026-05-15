package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.EntityType;
import com.example.quickbooksimporter.domain.ImportBatchStatus;
import com.example.quickbooksimporter.domain.ImportRunStatus;
import com.example.quickbooksimporter.persistence.ImportBatchEntity;
import com.example.quickbooksimporter.persistence.ImportRunEntity;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ImportProgressService {

    private static final int LIVE_ROWS_THRESHOLD = 10;
    private static final int LIVE_SECONDS_THRESHOLD = 5;
    private static final DateTimeFormatter STARTED_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final ImportHistoryService importHistoryService;

    public ImportProgressService(ImportHistoryService importHistoryService) {
        this.importHistoryService = importHistoryService;
    }

    public Optional<ImportRunProgressSnapshot> findLatestRunProgressForFile(EntityType entityType,
            String sourceFileName) {
        return importHistoryService.findLatestRunForFile(entityType, sourceFileName)
                .map(this::toRunSnapshot);
    }

    public Optional<ImportRunProgressSnapshot> findRunProgress(Long runId) {
        return importHistoryService.findRun(runId).map(this::toRunSnapshot);
    }

    public Optional<ImportBatchProgressSnapshot> findBatchProgress(Long batchId) {
        return importHistoryService.findBatch(batchId).map(this::toBatchSnapshot);
    }

    ImportRunProgressSnapshot toRunSnapshot(ImportRunEntity run) {
        int runnableRows = runnableRows(run);
        int processedRows = Math.min(run.getAttemptedRows(), runnableRows);
        double progressValue = runnableRows <= 0
                ? (isTerminal(run.getStatus()) ? 1d : 0d)
                : Math.min((double) processedRows / runnableRows, 1d);
        EtaEstimate eta = estimateForRun(run, processedRows, runnableRows);
        return new ImportRunProgressSnapshot(
                run.getId(),
                run.getEntityType(),
                run.getSourceFileName(),
                run.getStatus(),
                processedRows,
                runnableRows,
                run.getSkippedRows(),
                run.getImportedRows(),
                progressValue,
                percent(progressValue),
                eta.remainingLabel(),
                eta.throughputLabel(),
                startedLabel(run.getCreatedAt()),
                eta.live(),
                eta.historical());
    }

    ImportBatchProgressSnapshot toBatchSnapshot(ImportBatchEntity batch) {
        List<ImportRunEntity> runs = batch.getRuns() == null ? List.of() : batch.getRuns();
        int processedRows = runs.stream()
                .mapToInt(run -> Math.min(run.getAttemptedRows(), runnableRows(run)))
                .sum();
        int runnableRows = batch.getPlannedRunnableRows();
        double progressValue = runnableRows <= 0
                ? (terminalBatch(batch.getStatus()) ? 1d : 0d)
                : Math.min((double) processedRows / runnableRows, 1d);
        ImportRunEntity activeRun = runs.stream()
                .filter(run -> run.getStatus() == ImportRunStatus.RUNNING)
                .sorted(Comparator
                        .comparing(ImportRunEntity::getBatchOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ImportRunEntity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .findFirst()
                .orElse(null);
        EtaEstimate eta = estimateForBatch(batch, processedRows, runnableRows, activeRun);
        return new ImportBatchProgressSnapshot(
                batch.getId(),
                batch.getBatchName(),
                batch.getStatus(),
                processedRows,
                runnableRows,
                batch.getCompletedFiles(),
                batch.getRunnableFiles(),
                progressValue,
                percent(progressValue),
                eta.remainingLabel(),
                eta.throughputLabel(),
                startedLabel(batch.getStartedAt()),
                activeRun == null ? null : activeRun.getSourceFileName(),
                activeRun == null || activeRun.getEntityType() == null ? null : activeRun.getEntityType().displayName(),
                eta.live(),
                eta.historical());
    }

    EtaEstimate estimateForRun(ImportRunEntity run, int processedRows, int runnableRows) {
        Instant startedAt = run.getCreatedAt();
        Instant endedAt = run.getCompletedAt();
        Instant now = endedAt != null ? endedAt : Instant.now();
        double liveThroughput = throughput(processedRows, startedAt, now);
        boolean canUseLive = processedRows >= LIVE_ROWS_THRESHOLD
                && secondsBetween(startedAt, now) >= LIVE_SECONDS_THRESHOLD && liveThroughput > 0;
        double historicalThroughput = historicalEntityThroughput(run.getEntityType());
        int remainingRows = Math.max(runnableRows - processedRows, 0);
        if (isTerminal(run.getStatus())) {
            return new EtaEstimate(liveThroughput > 0 ? liveThroughput : historicalThroughput, 0L, liveThroughput > 0,
                    false, false, "Completed",
                    throughputLabel(liveThroughput > 0 ? liveThroughput : historicalThroughput));
        }
        if (canUseLive) {
            return estimateFromThroughput(liveThroughput, remainingRows, true, false);
        }
        if (historicalThroughput > 0) {
            return estimateFromThroughput(historicalThroughput, remainingRows, false, true);
        }
        return new EtaEstimate(0, null, false, false, true, "Estimating...", "Estimating throughput...");
    }

    EtaEstimate estimateForBatch(ImportBatchEntity batch, int processedRows, int runnableRows,
            ImportRunEntity activeRun) {
        Instant startedAt = batch.getStartedAt();
        Instant endedAt = batch.getCompletedAt();
        Instant now = endedAt != null ? endedAt : Instant.now();
        double liveThroughput = throughput(processedRows, startedAt, now);
        boolean canUseLive = processedRows >= LIVE_ROWS_THRESHOLD
                && secondsBetween(startedAt, now) >= LIVE_SECONDS_THRESHOLD && liveThroughput > 0;
        double historicalThroughput = activeRun != null && activeRun.getEntityType() != null
                ? historicalEntityThroughput(activeRun.getEntityType())
                : historicalOverallThroughput();
        int remainingRows = Math.max(runnableRows - processedRows, 0);
        if (terminalBatch(batch.getStatus())) {
            return new EtaEstimate(liveThroughput > 0 ? liveThroughput : historicalThroughput, 0L, liveThroughput > 0,
                    false, false, "Completed",
                    throughputLabel(liveThroughput > 0 ? liveThroughput : historicalThroughput));
        }
        if (canUseLive) {
            return estimateFromThroughput(liveThroughput, remainingRows, true, false);
        }
        if (historicalThroughput > 0) {
            return estimateFromThroughput(historicalThroughput, remainingRows, false, true);
        }
        return new EtaEstimate(0, null, false, false, true, "Estimating...", "Estimating throughput...");
    }

    private EtaEstimate estimateFromThroughput(double throughputRowsPerSecond, int remainingRows, boolean live,
            boolean historical) {
        if (throughputRowsPerSecond <= 0) {
            return new EtaEstimate(throughputRowsPerSecond, null, false, false, true, "Estimating...",
                    "Estimating throughput...");
        }
        long remainingSeconds = remainingRows <= 0 ? 0L
                : Math.max(1L, Math.round(remainingRows / throughputRowsPerSecond));
        return new EtaEstimate(
                throughputRowsPerSecond,
                remainingSeconds,
                live,
                historical,
                false,
                remainingSeconds == 0 ? "Almost done" : "about " + formatDuration(remainingSeconds) + " remaining",
                throughputLabel(throughputRowsPerSecond));
    }

    private double historicalEntityThroughput(EntityType entityType) {
        if (entityType == null) {
            return 0d;
        }
        return importHistoryService.recentCompletedRuns().stream()
                .filter(run -> run.getEntityType() == entityType)
                .limit(20)
                .mapToDouble(this::completedThroughput)
                .filter(value -> value > 0)
                .average()
                .orElse(0d);
    }

    private double historicalOverallThroughput() {
        return importHistoryService.recentCompletedRuns().stream()
                .limit(20)
                .mapToDouble(this::completedThroughput)
                .filter(value -> value > 0)
                .average()
                .orElse(0d);
    }

    private double completedThroughput(ImportRunEntity run) {
        return throughput(Math.min(run.getAttemptedRows(), runnableRows(run)), run.getCreatedAt(),
                run.getCompletedAt());
    }

    private double throughput(int processedRows, Instant startedAt, Instant endedAt) {
        long seconds = secondsBetween(startedAt, endedAt);
        if (processedRows <= 0 || seconds <= 0) {
            return 0d;
        }
        return (double) processedRows / seconds;
    }

    private long secondsBetween(Instant startedAt, Instant endedAt) {
        if (startedAt == null || endedAt == null || endedAt.isBefore(startedAt)) {
            return 0L;
        }
        return Math.max(0L, Duration.between(startedAt, endedAt).getSeconds());
    }

    private int runnableRows(ImportRunEntity run) {
        if (run == null) {
            return 0;
        }
        if (run.getValidRows() > 0 && run.getValidRows() < run.getTotalRows()) {
            return run.getValidRows();
        }
        return run.getTotalRows();
    }

    private String percent(double ratio) {
        return String.format("%.0f%%", ratio * 100d);
    }

    private String startedLabel(Instant startedAt) {
        return startedAt == null ? "Not started" : "Started " + STARTED_FORMAT.format(startedAt);
    }

    private String formatDuration(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        long remainder = seconds % 60;
        if (minutes < 60) {
            return remainder == 0 ? minutes + "m" : minutes + "m " + remainder + "s";
        }
        long hours = minutes / 60;
        long minutesRemainder = minutes % 60;
        return minutesRemainder == 0 ? hours + "h" : hours + "h " + minutesRemainder + "m";
    }

    private String throughputLabel(double throughputRowsPerSecond) {
        if (throughputRowsPerSecond <= 0) {
            return "Estimating throughput...";
        }
        if (throughputRowsPerSecond >= 1d) {
            return String.format("%.1f rows/s", throughputRowsPerSecond);
        }
        return String.format("%.1f rows/min", throughputRowsPerSecond * 60d);
    }

    private boolean isTerminal(ImportRunStatus status) {
        return status != null && status != ImportRunStatus.RUNNING && status != ImportRunStatus.QUEUED;
    }

    private boolean terminalBatch(ImportBatchStatus status) {
        return status != null && status != ImportBatchStatus.RUNNING;
    }
}
