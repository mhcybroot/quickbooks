package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.persistence.ImportBatchEntity;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ImportBatchBackgroundService {

    private static final Logger log = LoggerFactory.getLogger(ImportBatchBackgroundService.class);

    private final ImportBatchService importBatchService;
    private final CurrentCompanyService currentCompanyService;

    public ImportBatchBackgroundService(ImportBatchService importBatchService,
            CurrentCompanyService currentCompanyService) {
        this.importBatchService = importBatchService;
        this.currentCompanyService = currentCompanyService;
    }

    public ImportBatchEntity enqueueForCurrentCompany(Long batchId, List<ImportBatchService.BatchFileRequest> files) {
        Long companyId = currentCompanyService.requireCurrentCompanyId();
        ImportBatchEntity prepared = importBatchService.prepareBatchExecution(batchId, files);
        enqueue(batchId, files, companyId);
        return prepared;
    }

    @Async("importTaskExecutor")
    public void enqueue(Long batchId, List<ImportBatchService.BatchFileRequest> files, Long companyId) {
        currentCompanyService.runWithCompanyContext(companyId, () -> {
            try {
                log.info("Background batch import started: batchId={}, companyId={}", batchId, companyId);
                ImportBatchService.BatchExecutionReport report = importBatchService.executeBatch(batchId, files);
                log.info("Background batch import finished: batchId={}, companyId={}, status={}",
                        batchId, companyId, report.batch().getStatus());
            } catch (Exception ex) {
                log.error("Background batch import failed: batchId={}, companyId={}", batchId, companyId, ex);
                importBatchService.markBatchFailed(batchId);
            }
        });
    }
}
