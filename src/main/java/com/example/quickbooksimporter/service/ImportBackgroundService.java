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
    private final CurrentCompanyService currentCompanyService;

    public ImportBackgroundService(ImportWorkflowFacade workflowFacade,
                                   CurrentCompanyService currentCompanyService) {
        this.workflowFacade = workflowFacade;
        this.currentCompanyService = currentCompanyService;
    }

    public void enqueueForCurrentCompany(EntityType entityType,
                                         String fileName,
                                         String mappingProfileName,
                                         Object rawPreview,
                                         ImportExecutionOptions options) {
        Long companyId = currentCompanyService.requireCurrentCompanyId();
        enqueue(entityType, fileName, mappingProfileName, rawPreview, options, companyId);
    }

    @Async("importTaskExecutor")
    public void enqueue(EntityType entityType,
                        String fileName,
                        String mappingProfileName,
                        Object rawPreview,
                        ImportExecutionOptions options,
                        Long companyId) {
        currentCompanyService.runWithCompanyContext(companyId, () -> {
            try {
                log.info("Background import started: entityType={}, fileName={}, companyId={}", entityType, fileName, companyId);
                ImportExecutionResult result = workflowFacade.execute(entityType, fileName, mappingProfileName, rawPreview, options);
                log.info("Background import finished: entityType={}, fileName={}, companyId={}, runId={}, status={}",
                        entityType, fileName, companyId, result.importRun() == null ? null : result.importRun().getId(),
                        result.importRun() == null ? null : result.importRun().getStatus());
            } catch (Exception ex) {
                log.error("Background import failed: entityType={}, fileName={}, companyId={}", entityType, fileName, companyId, ex);
            }
        });
    }
}
