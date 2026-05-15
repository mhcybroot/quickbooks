package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.persistence.ImportRunEntity;
import com.example.quickbooksimporter.repository.ImportRunRepository;
import java.time.Duration;
import java.time.Instant;

final class ImportRunProgressFlusher {

    private ImportRunProgressFlusher() {
    }

    static ProgressFlushResult flushProgress(ImportRunRepository importRunRepository,
                                             ImportRunEntity run,
                                             int attempted,
                                             int skipped,
                                             int imported,
                                             int processedSinceFlush,
                                             Instant lastFlushAt) {
        Instant now = Instant.now();
        if (processedSinceFlush < 5 && (lastFlushAt == null || Duration.between(lastFlushAt, now).getSeconds() < 1)) {
            return new ProgressFlushResult(lastFlushAt == null ? now : lastFlushAt, false);
        }
        run.setAttemptedRows(attempted);
        run.setSkippedRows(skipped);
        run.setImportedRows(imported);
        importRunRepository.save(run);
        return new ProgressFlushResult(now, true);
    }

    record ProgressFlushResult(Instant lastFlushAt, boolean flushed) {
    }
}
