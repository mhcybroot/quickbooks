package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.AppJobType;
import com.example.quickbooksimporter.domain.EntityType;
import com.example.quickbooksimporter.domain.NormalizedBillField;
import com.example.quickbooksimporter.domain.NormalizedBillPaymentField;
import com.example.quickbooksimporter.domain.NormalizedExpenseField;
import com.example.quickbooksimporter.domain.NormalizedInvoiceField;
import com.example.quickbooksimporter.domain.NormalizedPaymentField;
import com.example.quickbooksimporter.domain.NormalizedSalesReceiptField;
import com.example.quickbooksimporter.persistence.AppJobEntity;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class QuickBooksJobService {

    private final AppJobService appJobService;
    private final CurrentCompanyService currentCompanyService;
    private final QuickBooksJobRunner jobRunner;

    public QuickBooksJobService(AppJobService appJobService,
                                CurrentCompanyService currentCompanyService,
                                QuickBooksJobRunner jobRunner) {
        this.appJobService = appJobService;
        this.currentCompanyService = currentCompanyService;
        this.jobRunner = jobRunner;
    }

    public AppJobEntity enqueueImportPreview(EntityType entityType, ImportPreviewRequest request) {
        AppJobEntity job = appJobService.createForCurrentCompany(AppJobType.IMPORT_PREVIEW,
                "Preview " + entityType.displayName() + " import", 1);
        jobRunner.runImportPreview(job.getId(), currentCompanyService.requireCurrentCompanyId(), entityType, request);
        return job;
    }

    public AppJobEntity enqueueBatchValidation(Long batchId, List<BatchValidationItemRequest> items, boolean skipInvalidRows) {
        AppJobEntity job = appJobService.createForCurrentCompany(AppJobType.BATCH_VALIDATION,
                "Validate batch files", items.size());
        jobRunner.runBatchValidation(job.getId(), currentCompanyService.requireCurrentCompanyId(), batchId, items, skipInvalidRows);
        return job;
    }

    public AppJobEntity enqueueCleanupSearch(QboCleanupEntityType type, QboCleanupFilter filter, boolean includeAll) {
        AppJobEntity job = appJobService.createForCurrentCompany(AppJobType.QBO_CLEANUP_SEARCH,
                "Search " + type.qboEntityName(), 1);
        jobRunner.runCleanupSearch(job.getId(), currentCompanyService.requireCurrentCompanyId(), type, filter, includeAll);
        return job;
    }

    public AppJobEntity enqueueCleanupDelete(QboCleanupEntityType type, List<QboTransactionRow> rows) {
        AppJobEntity job = appJobService.createForCurrentCompany(AppJobType.QBO_CLEANUP_DELETE,
                "Delete " + type.qboEntityName(), rows == null ? 0 : rows.size());
        jobRunner.runCleanupAction(job.getId(), currentCompanyService.requireCurrentCompanyId(), AppJobType.QBO_CLEANUP_DELETE, type, rows);
        return job;
    }

    public AppJobEntity enqueueCleanupVoid(QboCleanupEntityType type, List<QboTransactionRow> rows) {
        AppJobEntity job = appJobService.createForCurrentCompany(AppJobType.QBO_CLEANUP_VOID,
                "Void " + type.qboEntityName(), rows == null ? 0 : rows.size());
        jobRunner.runCleanupAction(job.getId(), currentCompanyService.requireCurrentCompanyId(), AppJobType.QBO_CLEANUP_VOID, type, rows);
        return job;
    }

    public AppJobEntity enqueueCleanupRecoveryPlan(QboCleanupEntityType type, List<QboTransactionRow> roots) {
        AppJobEntity job = appJobService.createForCurrentCompany(AppJobType.QBO_CLEANUP_RECOVERY_PLAN,
                "Plan linked delete recovery", roots == null ? 0 : roots.size());
        jobRunner.runCleanupRecoveryPlan(job.getId(), currentCompanyService.requireCurrentCompanyId(), type, roots);
        return job;
    }

    public AppJobEntity enqueueCleanupRecoveryExecution(QboCleanupDryRunPlan plan, boolean allowVoidFallback) {
        AppJobEntity job = appJobService.createForCurrentCompany(AppJobType.QBO_CLEANUP_RECOVERY_EXECUTION,
                "Execute linked delete recovery", plan == null ? 0 : plan.operationCount());
        jobRunner.runCleanupRecoveryExecution(job.getId(), currentCompanyService.requireCurrentCompanyId(), plan, allowVoidFallback);
        return job;
    }

    public AppJobEntity enqueueReconciliationPreview(ReconciliationPreviewRequest request) {
        AppJobEntity job = appJobService.createForCurrentCompany(AppJobType.RECONCILIATION_PREVIEW,
                "Preview reconciliation matches", 1);
        jobRunner.runReconciliationPreview(job.getId(), currentCompanyService.requireCurrentCompanyId(), request);
        return job;
    }

    public AppJobEntity enqueueReconciliationApply(Long sessionId, List<Integer> selectedBankRows) {
        AppJobEntity job = appJobService.createForCurrentCompany(AppJobType.RECONCILIATION_APPLY,
                "Apply reconciliation writeback", selectedBankRows == null ? 0 : selectedBankRows.size());
        jobRunner.runReconciliationApply(job.getId(), currentCompanyService.requireCurrentCompanyId(), sessionId, selectedBankRows);
        return job;
    }

    public AppJobEntity enqueueLoadIncomeAccounts() {
        AppJobEntity job = appJobService.createForCurrentCompany(AppJobType.SETTINGS_LOAD_INCOME_ACCOUNTS,
                "Load income accounts", 1);
        jobRunner.runLoadIncomeAccounts(job.getId(), currentCompanyService.requireCurrentCompanyId());
        return job;
    }

    public record ImportPreviewRequest(
            String fileName,
            byte[] fileBytes,
            DateFormatOption dateFormatOption,
            boolean invoiceGroupingEnabled,
            Map<NormalizedInvoiceField, String> invoiceMapping,
            Map<NormalizedPaymentField, String> paymentMapping,
            Map<NormalizedExpenseField, String> expenseMapping,
            Map<NormalizedSalesReceiptField, String> salesReceiptMapping,
            Map<NormalizedBillField, String> billMapping,
            Map<NormalizedBillPaymentField, String> billPaymentMapping,
            Map<String, QuickBooksInvoiceRef> draftInvoiceRefs,
            boolean skipQuickBooksChecks) {
    }

    public record BatchValidationItemRequest(
            int position,
            EntityType entityType,
            String fileName,
            byte[] fileBytes,
            Long profileId) {
    }

    public record ReconciliationPreviewRequest(
            String fileName,
            byte[] fileBytes,
            ReconciliationService.ReconciliationColumnMapping mapping,
            boolean dryRun,
            int dateWindowDays) {
    }
}
