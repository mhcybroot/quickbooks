package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.AppJobType;
import com.example.quickbooksimporter.domain.EntityType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class QuickBooksJobRunner {

    private static final Logger log = LoggerFactory.getLogger(QuickBooksJobRunner.class);

    private final CurrentCompanyService currentCompanyService;
    private final AppJobService appJobService;
    private final ImportWorkflowFacade workflowFacade;
    private final ImportPreviewJobCodec importPreviewJobCodec;
    private final InvoiceImportService invoiceImportService;
    private final PaymentImportService paymentImportService;
    private final ExpenseImportService expenseImportService;
    private final SalesReceiptImportService salesReceiptImportService;
    private final BillImportService billImportService;
    private final BillPaymentImportService billPaymentImportService;
    private final QuickBooksGateway quickBooksGateway;
    private final QboCleanupService qboCleanupService;
    private final ReconciliationService reconciliationService;
    private final QuickBooksConnectionService connectionService;
    private final ImportBatchService importBatchService;

    public QuickBooksJobRunner(CurrentCompanyService currentCompanyService,
                               AppJobService appJobService,
                               ImportWorkflowFacade workflowFacade,
                               ImportPreviewJobCodec importPreviewJobCodec,
                               InvoiceImportService invoiceImportService,
                               PaymentImportService paymentImportService,
                               ExpenseImportService expenseImportService,
                               SalesReceiptImportService salesReceiptImportService,
                               BillImportService billImportService,
                               BillPaymentImportService billPaymentImportService,
                               QuickBooksGateway quickBooksGateway,
                               QboCleanupService qboCleanupService,
                               ReconciliationService reconciliationService,
                               QuickBooksConnectionService connectionService,
                               ImportBatchService importBatchService) {
        this.currentCompanyService = currentCompanyService;
        this.appJobService = appJobService;
        this.workflowFacade = workflowFacade;
        this.importPreviewJobCodec = importPreviewJobCodec;
        this.invoiceImportService = invoiceImportService;
        this.paymentImportService = paymentImportService;
        this.expenseImportService = expenseImportService;
        this.salesReceiptImportService = salesReceiptImportService;
        this.billImportService = billImportService;
        this.billPaymentImportService = billPaymentImportService;
        this.quickBooksGateway = quickBooksGateway;
        this.qboCleanupService = qboCleanupService;
        this.reconciliationService = reconciliationService;
        this.connectionService = connectionService;
        this.importBatchService = importBatchService;
    }

    @Async("importTaskExecutor")
    public void runImportPreview(Long jobId,
                                 Long companyId,
                                 EntityType entityType,
                                 QuickBooksJobService.ImportPreviewRequest request) {
        runJob(jobId, companyId, "Running preview validation", () -> {
            PreviewProgressListener progressListener = (completedUnits, totalUnits, summaryMessage) ->
                    appJobService.updateProgress(jobId, companyId, completedUnits, totalUnits, summaryMessage);
            ImportPreviewJobResult result = switch (entityType) {
                case INVOICE -> importPreviewJobCodec.fromInvoicePreview(
                        invoiceImportService.preview(
                                request.fileName(),
                                request.fileBytes(),
                                request.invoiceMapping(),
                                request.invoiceGroupingEnabled(),
                                request.dateFormatOption(),
                                progressListener),
                        List.of());
                case PAYMENT -> importPreviewJobCodec.fromPaymentPreview(
                        paymentImportService.preview(
                                request.fileName(),
                                request.fileBytes(),
                                request.paymentMapping(),
                                request.draftInvoiceRefs(),
                                request.dateFormatOption(),
                                progressListener),
                        List.of("Payments with invalid invoice references will be blocked."));
                case EXPENSE -> importPreviewJobCodec.fromExpensePreview(
                        expenseImportService.preview(
                                request.fileName(),
                                request.fileBytes(),
                                request.expenseMapping(),
                                request.dateFormatOption(),
                                progressListener),
                        List.of());
                case SALES_RECEIPT -> importPreviewJobCodec.fromSalesReceiptPreview(
                        salesReceiptImportService.preview(
                                request.fileName(),
                                request.fileBytes(),
                                request.salesReceiptMapping(),
                                request.dateFormatOption(),
                                progressListener),
                        List.of());
                case BILL -> importPreviewJobCodec.fromBillPreview(
                        billImportService.preview(
                                request.fileName(),
                                request.fileBytes(),
                                request.billMapping(),
                                request.dateFormatOption(),
                                progressListener,
                                request.skipQuickBooksChecks()),
                        List.of("Bills with line grouping problems will be blocked."));
                case BILL_PAYMENT -> importPreviewJobCodec.fromBillPaymentPreview(
                        billPaymentImportService.preview(
                                request.fileName(),
                                request.fileBytes(),
                                request.billPaymentMapping(),
                                request.dateFormatOption(),
                                progressListener),
                        List.of("Bill payments with missing bill references will be blocked."));
            };
            return new JobCompletion("Preview complete: " + result.readyRows() + " ready, " + result.invalidRows() + " invalid.", result);
        });
    }

    @Async("importTaskExecutor")
    public void runBatchValidation(Long jobId,
                                   Long companyId,
                                   Long batchId,
                                   List<QuickBooksJobService.BatchValidationItemRequest> items,
                                   boolean skipInvalidRows) {
        runJob(jobId, companyId, "Validating batch files", () -> {
            List<BatchValidationJobResult.BatchValidationItemResult> results = new ArrayList<>();
            Map<String, QuickBooksInvoiceRef> draftInvoiceRefs = new LinkedHashMap<>();
            int completed = 0;
            for (QuickBooksJobService.BatchValidationItemRequest item : items.stream()
                    .sorted(java.util.Comparator.comparingInt((QuickBooksJobService.BatchValidationItemRequest candidate) -> candidate.entityType().batchPriority())
                            .thenComparingInt(QuickBooksJobService.BatchValidationItemRequest::position))
                    .toList()) {
                appJobService.updateProgress(jobId, companyId, completed, items.size(), "Validating " + item.fileName());
                ImportPreviewSummary summary = workflowFacade.preview(
                        item.entityType(),
                        item.fileName(),
                        item.fileBytes(),
                        item.profileId(),
                        Map.of(),
                        new ImportPreviewOptions(null, draftInvoiceRefs));
                ImportPreviewJobResult previewResult = importPreviewJobCodec.fromSummary(summary);
                results.add(new BatchValidationJobResult.BatchValidationItemResult(
                        item.position(),
                        previewResult,
                        summary.suggestedProfileName()));
                if (item.entityType() == EntityType.INVOICE) {
                    draftInvoiceRefs.putAll(workflowFacade.draftInvoiceRefs(item.entityType(), summary.rawPreview()));
                }
                completed++;
                appJobService.updateProgress(jobId, companyId, completed, items.size(), "Validated " + item.fileName());
            }

            List<ImportBatchService.BatchFileRequest> requests = results.stream()
                    .map(result -> {
                        Object rawPreview = importPreviewJobCodec.readRawPreview(result.previewResult());
                        return new ImportBatchService.BatchFileRequest(
                                result.position(),
                                result.previewResult().entityType(),
                                result.previewResult().sourceFileName(),
                                null,
                                new ImportPreviewSummary(
                                        result.previewResult().entityType(),
                                        result.previewResult().sourceFileName(),
                                        List.of(),
                                        result.previewResult().totalRows(),
                                        result.previewResult().readyRows(),
                                        result.previewResult().invalidRows(),
                                        result.previewResult().duplicateRows(),
                                        result.previewResult().exportCsv(),
                                        result.suggestedProfileName(),
                                        result.previewResult().warnings(),
                                        rawPreview),
                                skipInvalidRows);
                    })
                    .toList();
            int runnableFiles = (int) requests.stream()
                    .filter(file -> file.previewSummary() != null
                            && !file.previewSummary().hasBlockingIssues(skipInvalidRows
                            ? ImportExecutionMode.IMPORT_READY_ONLY
                            : ImportExecutionMode.STRICT_ALL_ROWS))
                    .count();
            BatchValidationJobResult payload = new BatchValidationJobResult(
                    results,
                    importBatchService.dependencyWarnings(requests),
                    results.size(),
                    runnableFiles);
            if (batchId != null) {
                importBatchService.updateValidationSnapshot(batchId, items.size(), results.size(), runnableFiles);
            }
            return new JobCompletion("Validated " + results.size() + " files.", payload);
        });
    }

    @Async("importTaskExecutor")
    public void runCleanupSearch(Long jobId, Long companyId, QboCleanupEntityType type, QboCleanupFilter filter, boolean includeAll) {
        runJob(jobId, companyId, "Loading QuickBooks records", () -> {
            List<QboTransactionRow> rows = qboCleanupService.list(type, filter, includeAll);
            String summary = "Loaded " + rows.size() + " records | Sort: " + filter.sortField() + " " + filter.sortDirection()
                    + (includeAll ? " (all pages)." : ".");
            return new JobCompletion(summary, new QboCleanupSearchJobResult(rows, summary));
        });
    }

    @Async("importTaskExecutor")
    public void runCleanupAction(Long jobId, Long companyId, AppJobType type, QboCleanupEntityType entityType, List<QboTransactionRow> rows) {
        runJob(jobId, companyId, "Running cleanup action", () -> {
            QboCleanupService.CleanupActionResponse response = type == AppJobType.QBO_CLEANUP_DELETE
                    ? qboCleanupService.delete(entityType, rows)
                    : qboCleanupService.voidTransactions(entityType, rows);
            return new JobCompletion("Processed " + rows.size() + " record(s).", new QboCleanupActionJobResult(response));
        });
    }

    @Async("importTaskExecutor")
    public void runCleanupRecoveryPlan(Long jobId, Long companyId, QboCleanupEntityType entityType, List<QboTransactionRow> roots) {
        runJob(jobId, companyId, "Preparing dependency recovery plan", () -> {
            QboCleanupDryRunPlan plan = qboCleanupService.prepareRecoveryPlan(entityType, roots);
            return new JobCompletion("Prepared recovery plan with " + plan.operationCount() + " operations.",
                    new QboCleanupRecoveryPlanJobResult(plan));
        });
    }

    @Async("importTaskExecutor")
    public void runCleanupRecoveryExecution(Long jobId, Long companyId, QboCleanupDryRunPlan plan, boolean allowVoidFallback) {
        runJob(jobId, companyId, "Executing dependency recovery", () -> {
            QboCleanupRecoveryResult result = qboCleanupService.executeRecoveryPlan(plan, allowVoidFallback);
            return new JobCompletion("Recovery completed with " + result.results().size() + " results.",
                    new QboCleanupRecoveryExecutionJobResult(result));
        });
    }

    @Async("importTaskExecutor")
    public void runReconciliationPreview(Long jobId, Long companyId, QuickBooksJobService.ReconciliationPreviewRequest request) {
        runJob(jobId, companyId, "Matching bank rows to QuickBooks transactions", () -> {
            ReconciliationPreview preview = reconciliationService.previewMatches(
                    request.fileName(),
                    request.fileBytes(),
                    request.mapping(),
                    request.dryRun(),
                    request.dateWindowDays());
            return new JobCompletion("Previewed " + preview.autoMatched().size() + " auto matches and "
                    + preview.needsReview().size() + " review rows.", new ReconciliationPreviewJobResult(preview));
        });
    }

    @Async("importTaskExecutor")
    public void runReconciliationApply(Long jobId, Long companyId, Long sessionId, List<Integer> selectedBankRows) {
        runJob(jobId, companyId, "Applying reconciliation writeback", () -> {
            ReconciliationApplyResult result = reconciliationService.applyMatches(sessionId, selectedBankRows);
            return new JobCompletion(result.message(), new ReconciliationApplyJobResult(result));
        });
    }

    @Async("importTaskExecutor")
    public void runLoadIncomeAccounts(Long jobId, Long companyId) {
        runJob(jobId, companyId, "Loading income accounts", () -> {
            String realmId = connectionService.getActiveConnection().getRealmId();
            List<QuickBooksIncomeAccount> accounts = quickBooksGateway.listIncomeAccounts(realmId);
            return new JobCompletion("Loaded " + accounts.size() + " income accounts.",
                    new SettingsIncomeAccountsJobResult(accounts));
        });
    }

    private void runJob(Long jobId, Long companyId, String startMessage, JobWork work) {
        currentCompanyService.runWithCompanyContext(companyId, () -> {
            try {
                log.info("Background job starting: jobId={}, companyId={}, message={}", jobId, companyId, startMessage);
                appJobService.markRunning(jobId, companyId, startMessage);
                JobCompletion completion = work.run();
                log.info("Background job completed: jobId={}, companyId={}, summary={}", jobId, companyId, completion.summaryMessage());
                appJobService.completeSuccess(jobId, companyId, completion.summaryMessage(), completion.resultPayload());
            } catch (Exception exception) {
                log.error("Background job failed: jobId={}, companyId={}", jobId, companyId, exception);
                appJobService.completeFailure(jobId, companyId, exception.getMessage());
            }
        });
    }

    private record JobCompletion(String summaryMessage, Object resultPayload) {
    }

    @FunctionalInterface
    private interface JobWork {
        JobCompletion run();
    }
}
