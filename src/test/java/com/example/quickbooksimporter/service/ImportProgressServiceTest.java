package com.example.quickbooksimporter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.quickbooksimporter.domain.EntityType;
import com.example.quickbooksimporter.domain.ImportBatchStatus;
import com.example.quickbooksimporter.domain.ImportRunStatus;
import com.example.quickbooksimporter.persistence.ImportBatchEntity;
import com.example.quickbooksimporter.persistence.ImportRunEntity;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ImportProgressServiceTest {

    @Test
    void usesHistoricalEstimateWhenLiveSignalIsTooWeak() {
        ImportHistoryService historyService = mock(ImportHistoryService.class);
        ImportProgressService service = new ImportProgressService(historyService);
        when(historyService.recentCompletedRuns()).thenReturn(List.of(completedRun(EntityType.INVOICE, 40, 20)));

        ImportRunProgressSnapshot snapshot = service.toRunSnapshot(runningRun(EntityType.INVOICE, 5, 20, 0, 2));

        assertTrue(snapshot.historicalEstimate());
        assertTrue(!snapshot.liveEstimate());
        assertTrue(snapshot.remainingLabel().contains("remaining"));
    }

    @Test
    void switchesToLiveEstimateAfterThreshold() {
        ImportHistoryService historyService = mock(ImportHistoryService.class);
        ImportProgressService service = new ImportProgressService(historyService);
        when(historyService.recentCompletedRuns()).thenReturn(List.of(completedRun(EntityType.PAYMENT, 20, 20)));

        ImportRunProgressSnapshot snapshot = service.toRunSnapshot(runningRun(EntityType.PAYMENT, 20, 40, 0, 10));

        assertTrue(snapshot.liveEstimate());
        assertTrue(!snapshot.historicalEstimate());
        assertEquals("50%", snapshot.percentLabel());
    }

    @Test
    void fallsBackToEstimatingWhenNoHistoryAndNoLiveSignalExist() {
        ImportHistoryService historyService = mock(ImportHistoryService.class);
        ImportProgressService service = new ImportProgressService(historyService);
        when(historyService.recentCompletedRuns()).thenReturn(List.of());

        ImportRunProgressSnapshot snapshot = service.toRunSnapshot(runningRun(EntityType.EXPENSE, 0, 10, 0, 1));

        assertEquals("Estimating...", snapshot.remainingLabel());
        assertEquals("Estimating throughput...", snapshot.throughputLabel());
    }

    @Test
    void batchSnapshotAggregatesProcessedRows() {
        ImportHistoryService historyService = mock(ImportHistoryService.class);
        ImportProgressService service = new ImportProgressService(historyService);
        when(historyService.recentCompletedRuns()).thenReturn(List.of(completedRun(EntityType.BILL, 30, 30)));

        ImportBatchEntity batch = new ImportBatchEntity();
        batch.setBatchName("Ops");
        batch.setStatus(ImportBatchStatus.RUNNING);
        batch.setStartedAt(Instant.now().minusSeconds(30));
        batch.setPlannedRunnableRows(50);
        batch.setRunnableFiles(2);
        batch.setCompletedFiles(1);
        ImportRunEntity first = runningRun(EntityType.BILL, 20, 30, 0, 12);
        first.setSourceFileName("bills.csv");
        batch.getRuns().add(first);

        ImportBatchProgressSnapshot snapshot = service.toBatchSnapshot(batch);

        assertEquals(20, snapshot.processedRows());
        assertEquals(50, snapshot.plannedRunnableRows());
        assertEquals("40%", snapshot.percentLabel());
    }

    @Test
    void readyOnlyRunUsesValidRowsAsProgressDenominator() {
        ImportHistoryService historyService = mock(ImportHistoryService.class);
        ImportProgressService service = new ImportProgressService(historyService);
        when(historyService.recentCompletedRuns()).thenReturn(List.of());

        ImportRunEntity run = runningRun(EntityType.INVOICE, 1, 6, 4, 2);
        run.setValidRows(2);

        ImportRunProgressSnapshot snapshot = service.toRunSnapshot(run);

        assertEquals(1, snapshot.processedRows());
        assertEquals(2, snapshot.runnableRows());
        assertEquals("50%", snapshot.percentLabel());
    }

    private ImportRunEntity runningRun(EntityType entityType, int attemptedRows, int totalRows, int skippedRows, long elapsedSeconds) {
        ImportRunEntity run = new ImportRunEntity();
        run.setEntityType(entityType);
        run.setStatus(ImportRunStatus.RUNNING);
        run.setAttemptedRows(attemptedRows);
        run.setImportedRows(Math.min(attemptedRows, totalRows));
        run.setSkippedRows(skippedRows);
        run.setTotalRows(totalRows);
        run.setValidRows(totalRows - skippedRows);
        run.setCreatedAt(Instant.now().minusSeconds(elapsedSeconds));
        run.setSourceFileName("run.csv");
        return run;
    }

    private ImportRunEntity completedRun(EntityType entityType, int attemptedRows, long elapsedSeconds) {
        ImportRunEntity run = new ImportRunEntity();
        Instant completedAt = Instant.now().minusSeconds(10);
        run.setEntityType(entityType);
        run.setStatus(ImportRunStatus.IMPORTED);
        run.setAttemptedRows(attemptedRows);
        run.setImportedRows(attemptedRows);
        run.setSkippedRows(0);
        run.setTotalRows(attemptedRows);
        run.setValidRows(attemptedRows);
        run.setCreatedAt(completedAt.minusSeconds(elapsedSeconds));
        run.setCompletedAt(completedAt);
        run.setSourceFileName("done.csv");
        return run;
    }
}
