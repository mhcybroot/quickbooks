package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.EntityType;
import com.example.quickbooksimporter.persistence.ImportRunEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ImportBackgroundService {

    private static final Logger log = LoggerFactory.getLogger(ImportBackgroundService.class);

    private final ImportWorkflowFacade workflowFacade;
    private final CurrentCompanyService currentCompanyService;
    private final ImportBackgroundService self;

    public ImportBackgroundService(ImportWorkflowFacade workflowFacade,
            CurrentCompanyService currentCompanyService,
            @Lazy ImportBackgroundService self) {
        this.workflowFacade = workflowFacade;
        this.currentCompanyService = currentCompanyService;
        this.self = self;
    }

    public Long enqueueForCurrentCompany(EntityType entityType,
            String fileName,
            String mappingProfileName,
            Object rawPreview,
            ImportExecutionOptions options) {
        Long companyId = currentCompanyService.requireCurrentCompanyId();
        Long runId = workflowFacade.preCreateRun(entityType, fileName, mappingProfileName, rawPreview, options);
        self.enqueue(runId, entityType, fileName, mappingProfileName, rawPreview, options, companyId);
        return runId;
    }

    @Async("importTaskExecutor")
    public void enqueue(Long runId,
            EntityType entityType,
            String fileName,
            String mappingProfileName,
            Object rawPreview,
            ImportExecutionOptions options,
            Long companyId) {
        currentCompanyService.runWithCompanyContext(companyId, () -> {
            try {
                log.info("Background import started: runId={}, entityType={}, fileName={}, companyId={}", runId,
                        entityType, fileName, companyId);
                ImportExecutionResult result = workflowFacade.executeWithRunId(runId, entityType, fileName,
                        mappingProfileName, rawPreview, options);
                log.info("Background import finished: runId={}, entityType={}, fileName={}, companyId={}, status={}",
                        runId, entityType, fileName, companyId,
                        result.importRun() == null ? null : result.importRun().getStatus());
            } catch (Exception ex) {
                log.error("Background import failed: runId={}, entityType={}, fileName={}, companyId={}", runId,
                        entityType, fileName, companyId, ex);
            }
        });
    }
}
