package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.EntityType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ImportBackgroundService {

    private static final Logger log = LoggerFactory.getLogger(ImportBackgroundService.class);

    private final ImportWorkflowFacade workflowFacade;

    public ImportBackgroundService(ImportWorkflowFacade workflowFacade) {
        this.workflowFacade = workflowFacade;
    }

    @Async("importTaskExecutor")
    public void enqueue(EntityType entityType,
                        String fileName,
                        String mappingProfileName,
                        Object rawPreview,
                        ImportExecutionOptions options) {
        try {
            log.info("Background import started: entityType={}, fileName={}", entityType, fileName);
            ImportExecutionResult result = workflowFacade.execute(entityType, fileName, mappingProfileName, rawPreview, options);
            log.info("Background import finished: entityType={}, fileName={}, runId={}, status={}",
                    entityType, fileName, result.importRun() == null ? null : result.importRun().getId(),
                    result.importRun() == null ? null : result.importRun().getStatus());
        } catch (Exception ex) {
            log.error("Background import failed: entityType={}, fileName={}", entityType, fileName, ex);
        }
    }
}
