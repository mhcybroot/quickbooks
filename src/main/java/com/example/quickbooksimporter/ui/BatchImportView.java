package com.example.quickbooksimporter.ui;

import com.example.quickbooksimporter.domain.AppJobStatus;
import com.example.quickbooksimporter.domain.EntityType;
import com.example.quickbooksimporter.persistence.ImportBatchEntity;
import com.example.quickbooksimporter.persistence.ImportRunEntity;
import com.example.quickbooksimporter.service.AppJobService;
import com.example.quickbooksimporter.service.AppJobSnapshot;
import com.example.quickbooksimporter.service.BatchValidationJobResult;
import com.example.quickbooksimporter.service.ImportBatchBackgroundService;
import com.example.quickbooksimporter.service.ImportBatchService;
import com.example.quickbooksimporter.service.ImportBatchProgressSnapshot;
import com.example.quickbooksimporter.service.ImportHistoryService;
import com.example.quickbooksimporter.service.ImportPreviewJobCodec;
import com.example.quickbooksimporter.service.ImportPreviewOptions;
import com.example.quickbooksimporter.service.ImportPreviewSummary;
import com.example.quickbooksimporter.service.ImportProgressService;
import com.example.quickbooksimporter.service.ImportWorkflowFacade;
import com.example.quickbooksimporter.service.MappingProfileSummary;
import com.example.quickbooksimporter.service.QuickBooksInvoiceRef;
import com.example.quickbooksimporter.service.QuickBooksJobService;
import com.example.quickbooksimporter.service.QuickBooksConnectionService;
import com.example.quickbooksimporter.service.QuickBooksConnectionStatus;
import com.example.quickbooksimporter.ui.components.UiComponents;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MultiFileMemoryBuffer;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import jakarta.annotation.security.PermitAll;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Route(value = "batch", layout = MainLayout.class)
@PageTitle("Batch Import Workspace")
@PermitAll
public class BatchImportView extends VerticalLayout {

    private static final Logger log = LoggerFactory.getLogger(BatchImportView.class);

    private final ImportWorkflowFacade workflowFacade;
    private final ImportBatchService batchService;
    private final ImportBatchBackgroundService batchBackgroundService;
    private final ImportHistoryService importHistoryService;
    private final ImportProgressService importProgressService;
    private final QuickBooksConnectionStatus connectionStatus;
    private final QuickBooksJobService quickBooksJobService;
    private final AppJobService appJobService;
    private final ImportPreviewJobCodec importPreviewJobCodec;

    private final List<BatchDraftItem> items = new ArrayList<>();
    private final MultiFileMemoryBuffer buffer = new MultiFileMemoryBuffer();
    private final Upload upload = new Upload(buffer);
    private final Grid<BatchDraftItem> grid = new Grid<>(BatchDraftItem.class, false);
    private final TextField batchName = new TextField("Batch Name");
    private final Paragraph dependencySummary = new Paragraph("Upload one or more CSV files to build a batch.");
    private final ComboBox<EntityType> entityType = new ComboBox<>("Detected Entity Type");
    private final ComboBox<MappingProfileSummary> profile = new ComboBox<>("Mapping Profile");
    private final Paragraph detailSummary = new Paragraph("Select a file to review and validate it.");
    private final Checkbox skipInvalidRows = new Checkbox("Skip invalid rows per file");
    private final Anchor downloadAnchor = new Anchor();
    private final Paragraph progressSummary = new Paragraph("No live batch is running.");
    private final Paragraph progressDetails = new Paragraph();
    private final ProgressBar progressBar = new ProgressBar();
    private final Button validateSelectedButton = new Button("Validate Selected File");
    private final Button validateAllButton = new Button("Validate All");

    private BatchDraftItem selectedItem;
    private Long activeBatchId;
    private Long trackingBatchId;
    private Long validationJobId;
    private boolean syncingDetailFields;
    private boolean pollListenerRegistered;

    public BatchImportView(ImportWorkflowFacade workflowFacade,
                           ImportBatchService batchService,
                           ImportBatchBackgroundService batchBackgroundService,
                           ImportHistoryService importHistoryService,
                           ImportProgressService importProgressService,
                           QuickBooksConnectionService connectionService,
                           QuickBooksJobService quickBooksJobService,
                           AppJobService appJobService,
                           ImportPreviewJobCodec importPreviewJobCodec) {
        this.workflowFacade = workflowFacade;
        this.batchService = batchService;
        this.batchBackgroundService = batchBackgroundService;
        this.importHistoryService = importHistoryService;
        this.importProgressService = importProgressService;
        this.connectionStatus = connectionService.getStatus();
        this.quickBooksJobService = quickBooksJobService;
        this.appJobService = appJobService;
        this.importPreviewJobCodec = importPreviewJobCodec;

        initializeView();
    }

    private void initializeView() {
        addClassName("corp-page");
        setSizeFull();
        batchName.setValue("Batch " + Instant.now().toString().replace(':', '-'));
        profile.setItems(List.of());

        try {
            add(new H2("Batch Import Workspace"),
                    new Paragraph("Upload multiple files, validate them together, and run them in dependency-aware order."),
                    UiComponents.importStepper("Upload"));
            add(buildConnectionCard());
            configureUpload();
            configureGrid();
            configureDetailPanel();
            configureActions();
        } catch (RuntimeException exception) {
            log.error("Failed to initialize BatchImportView", exception);
            removeAll();
            add(new H2("Batch Import Workspace"),
                    new Paragraph("The batch workspace could not be initialized fully."),
                    UiComponents.card(
                            new H3("Initialization Error"),
                            new Paragraph(exception.getClass().getSimpleName() + ": " + String.valueOf(exception.getMessage())),
                            new Paragraph("Open the single-import screens or import history while this page is being corrected.")));
        }
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        if (!pollListenerRegistered) {
            attachEvent.getUI().addPollListener(event -> {
                refreshValidationJob();
                refreshBatchProgress();
            });
            pollListenerRegistered = true;
        }
    }

    private VerticalLayout buildConnectionCard() {
        HorizontalLayout kpis = new HorizontalLayout(
                UiComponents.kpi("Connection", connectionStatus.connected() ? "Active" : "Blocked",
                        connectionStatus.connected() ? "QuickBooks is ready for run-all execution" : "Connect QuickBooks before batch import"),
                UiComponents.kpi("Files In Queue", String.valueOf(items.size()), "Current in-memory draft batch"),
                UiComponents.kpi("Validation Mode", "Partial", "Invalid files stay blocked while valid files continue"),
                UiComponents.kpi("Execution Order", "Auto", "Invoices before payments, bills before bill payments"));
        kpis.setWidthFull();
        kpis.addClassName("corp-kpi-row");
        return UiComponents.card(new H3("Batch Readiness"), kpis);
    }

    private void configureUpload() {
        upload.setAcceptedFileTypes(".csv");
        upload.setMaxFiles(100);
        upload.addSucceededListener(event -> {
            try {
                byte[] bytes = buffer.getInputStream(event.getFileName()).readAllBytes();
                EntityType detected = detectEntityType(event.getFileName(), workflowFacade.parse(bytes).headers());
                BatchDraftItem item = new BatchDraftItem(items.size() + 1, event.getFileName(), bytes, detected);
                workflowFacade.lastUsedProfile(detected).ifPresent(item::setSelectedProfile);
                item.setStatusText("Uploaded");
                item.setWarningText("Ready for validation.");
                items.add(item);
                refreshGrid();
                notifySuccess("Added " + event.getFileName() + " to the batch queue.");
            } catch (IOException exception) {
                notifyWarning("Could not read uploaded file: " + exception.getMessage());
            }
        });
        add(UiComponents.card(new H3("Upload Queue"), upload, dependencySummary));
    }

    private void configureGrid() {
        grid.addColumn(BatchDraftItem::getPosition).setHeader("#").setAutoWidth(true);
        grid.addColumn(BatchDraftItem::getFileName).setHeader("File").setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(item -> item.getEntityType().displayName()).setHeader("Entity").setAutoWidth(true);
        grid.addColumn(item -> item.getSelectedProfile() == null ? "Auto / default" : item.getSelectedProfile().name()).setHeader("Profile").setAutoWidth(true);
        grid.addColumn(BatchDraftItem::getRowSummary).setHeader("Rows").setAutoWidth(true);
        grid.addColumn(BatchDraftItem::getStatusText).setHeader("Validation").setAutoWidth(true);
        grid.addColumn(BatchDraftItem::getRunStatusText).setHeader("Run").setAutoWidth(true);
        grid.addColumn(BatchDraftItem::getWarningText).setHeader("Warnings").setAutoWidth(true).setFlexGrow(1);
        grid.setHeight("320px");
        grid.setWidthFull();
        grid.addClassName("corp-grid");
        grid.asSingleSelect().addValueChangeListener(event -> {
            selectedItem = event.getValue();
            refreshDetailPanel();
        });
        add(UiComponents.card(new H3("Batch Queue"), grid));
    }

    private void configureDetailPanel() {
        entityType.setItems(EntityType.values());
        entityType.setItemLabelGenerator(EntityType::displayName);
        entityType.addValueChangeListener(event -> {
            if (syncingDetailFields || selectedItem == null || event.getValue() == null) {
                return;
            }
            selectedItem.setEntityType(event.getValue());
            loadProfiles(event.getValue());
            selectedItem.setPreviewSummary(null);
            selectedItem.setStatusText("Needs validation");
            selectedItem.setWarningText("Entity type changed. Validate again.");
            refreshGrid();
        });

        profile.setItemLabelGenerator(MappingProfileSummary::name);
        profile.addValueChangeListener(event -> {
            if (syncingDetailFields || selectedItem == null) {
                return;
            }
            selectedItem.setSelectedProfile(event.getValue());
            selectedItem.setPreviewSummary(null);
            selectedItem.setStatusText("Needs validation");
            selectedItem.setWarningText("Profile changed. Validate again.");
            refreshGrid();
        });

        validateSelectedButton.addClickListener(event -> validateSelected());
        validateSelectedButton.addThemeName("primary");
        downloadAnchor.setVisible(false);
        downloadAnchor.setText("Download normalized CSV");

        add(UiComponents.card(
                new H3("Selected File"),
                detailSummary,
                new HorizontalLayout(entityType, profile, validateSelectedButton, downloadAnchor)));
    }

    private void configureActions() {
        validateAllButton.addClickListener(event -> validateAll());
        Button runAll = new Button("Run All", event -> runAll());
        Button openHistory = new Button("Open History", event -> UI.getCurrent().navigate(ImportHistoryView.class));
        Button clear = new Button("Clear Draft", event -> clearDraft());
        validateAllButton.addThemeName("primary");
        runAll.addThemeName("primary");

        skipInvalidRows.setValue(false);
        HorizontalLayout actions = new HorizontalLayout(batchName, validateAllButton, runAll, openHistory, clear);
        actions.add(skipInvalidRows);
        actions.setWidthFull();
        progressBar.setWidthFull();
        progressBar.setVisible(false);
        progressDetails.setVisible(false);
        add(UiComponents.card(new H3("Batch Actions"),
                new Paragraph("Validate the queue first, then run valid files in system-managed order."),
                actions,
                progressSummary,
                progressBar,
                progressDetails));
    }

    private void validateSelected() {
        if (selectedItem == null) {
            notifyWarning("Select a file first.");
            return;
        }
        validateSelectedButton.setEnabled(false);
        validationJobId = quickBooksJobService.enqueueBatchValidation(null,
                List.of(toValidationRequest(selectedItem)),
                skipInvalidRows.getValue()).getId();
        dependencySummary.setText("Validation started for " + selectedItem.getFileName() + ".");
        progressSummary.setText("Validation job is queued.");
        progressDetails.setText("QuickBooks validation checks will update here.");
        progressBar.setVisible(true);
        progressDetails.setVisible(true);
        progressBar.setIndeterminate(true);
        getUI().ifPresent(ui -> ui.setPollInterval(3000));
    }

    private void validateAll() {
        if (items.isEmpty()) {
            notifyWarning("Upload at least one file first.");
            return;
        }
        ensureBatch();
        validateAllButton.setEnabled(false);
        validateSelectedButton.setEnabled(false);
        validationJobId = quickBooksJobService.enqueueBatchValidation(activeBatchId,
                items.stream().map(this::toValidationRequest).toList(),
                skipInvalidRows.getValue()).getId();
        dependencySummary.setText("Batch validation started in the background.");
        progressSummary.setText("Validation job is queued.");
        progressDetails.setText("QuickBooks validation checks will update here.");
        progressBar.setVisible(true);
        progressDetails.setVisible(true);
        progressBar.setIndeterminate(true);
        getUI().ifPresent(ui -> ui.setPollInterval(3000));
    }

    private void runAll() {
        if (!connectionStatus.connected()) {
            notifyWarning("Connect QuickBooks before running the batch.");
            return;
        }
        if (items.isEmpty()) {
            notifyWarning("Upload files before running a batch.");
            return;
        }
        if (items.stream().anyMatch(item -> item.getPreviewSummary() == null)) {
            validateAll();
            notifyWarning("Validation started. Run the batch again after validation completes.");
            return;
        }
        ensureBatch();
        ImportBatchEntity prepared = batchBackgroundService.enqueueForCurrentCompany(activeBatchId, items.stream()
                .map(this::toBatchRequest)
                .toList());
        trackingBatchId = prepared.getId();
        progressBar.setVisible(true);
        progressDetails.setVisible(true);
        progressBar.setIndeterminate(true);
        progressSummary.setText("Batch #" + prepared.getId() + " is starting.");
        progressDetails.setText("Calculating ETA and preparing file execution order...");
        dependencySummary.setText("Batch " + prepared.getBatchName() + " is running in the background.");
        getUI().ifPresent(ui -> ui.setPollInterval(3000));
        refreshBatchProgress();
        notifySuccess("Batch run started. Progress and ETA will update live.");
    }

    private void validateItem(BatchDraftItem item, Map<String, QuickBooksInvoiceRef> draftInvoiceRefs) {
        Long profileId = item.getSelectedProfile() == null ? null : item.getSelectedProfile().id();
        ImportPreviewSummary preview = workflowFacade.preview(
                item.getEntityType(),
                item.getFileName(),
                item.getBytes(),
                profileId,
                Map.of(),
                new ImportPreviewOptions(null, draftInvoiceRefs));
        item.setPreviewSummary(preview);
        item.setStatusText(preview.runStatusSummary(
                skipInvalidRows.getValue() ? com.example.quickbooksimporter.service.ImportExecutionMode.IMPORT_READY_ONLY
                        : com.example.quickbooksimporter.service.ImportExecutionMode.STRICT_ALL_ROWS).name());
        item.setRowSummary(preview.totalRows() + " total / " + preview.readyRows() + " ready / " + preview.invalidRows() + " invalid");
        item.setWarningText(preview.warnings().isEmpty() ? "Validated." : String.join(" | ", preview.warnings()));
        if (item.getSelectedProfile() == null && preview.suggestedProfileName() != null) {
            workflowFacade.listProfiles(item.getEntityType()).stream()
                    .filter(candidate -> candidate.name().equals(preview.suggestedProfileName()))
                    .findFirst()
                    .ifPresent(item::setSelectedProfile);
        }
    }

    private QuickBooksJobService.BatchValidationItemRequest toValidationRequest(BatchDraftItem item) {
        return new QuickBooksJobService.BatchValidationItemRequest(
                item.getPosition(),
                item.getEntityType(),
                item.getFileName(),
                item.getBytes(),
                item.getSelectedProfile() == null ? null : item.getSelectedProfile().id());
    }

    private Map<String, QuickBooksInvoiceRef> draftInvoiceRefsForBatch(BatchDraftItem targetItem) {
        Map<String, QuickBooksInvoiceRef> refs = new LinkedHashMap<>();
        for (BatchDraftItem item : items) {
            if (item == targetItem) {
                continue;
            }
            if (item.getEntityType() != EntityType.INVOICE || item.getPreviewSummary() == null) {
                continue;
            }
            refs.putAll(workflowFacade.draftInvoiceRefs(item.getEntityType(), item.getPreviewSummary().rawPreview()));
        }
        return refs;
    }

    private void refreshDetailPanel() {
        syncingDetailFields = true;
        try {
        if (selectedItem == null) {
            detailSummary.setText("Select a file to review and validate it.");
            entityType.clear();
            profile.clear();
            profile.setItems(List.of());
            downloadAnchor.setVisible(false);
            return;
        }
        entityType.setValue(selectedItem.getEntityType());
        loadProfiles(selectedItem.getEntityType());
        if (selectedItem.getSelectedProfile() != null
                && workflowFacade.listProfiles(selectedItem.getEntityType()).stream()
                .anyMatch(candidate -> Objects.equals(candidate.id(), selectedItem.getSelectedProfile().id()))) {
            profile.setValue(selectedItem.getSelectedProfile());
        } else {
            profile.clear();
        }
        detailSummary.setText(selectedItem.getFileName() + " | " + selectedItem.getStatusText() + " | " + selectedItem.getRowSummary());
        if (selectedItem.getPreviewSummary() != null && selectedItem.getPreviewSummary().exportCsv() != null) {
            downloadAnchor.setHref(new StreamResource("normalized-" + selectedItem.getFileName(),
                    () -> new ByteArrayInputStream(selectedItem.getPreviewSummary().exportCsv().getBytes(StandardCharsets.UTF_8))));
            downloadAnchor.setVisible(true);
        } else {
            downloadAnchor.setVisible(false);
        }
        } finally {
            syncingDetailFields = false;
        }
    }

    private void refreshGrid() {
        grid.setItems(List.copyOf(items));
    }

    private void loadProfiles(EntityType type) {
        profile.setItems(workflowFacade.listProfiles(type));
    }

    private void ensureBatch() {
        if (activeBatchId != null) {
            return;
        }
        ImportBatchEntity batch = batchService.createDraftBatch(batchName.getValue(), items.size());
        activeBatchId = batch.getId();
    }

    private void clearDraft() {
        items.clear();
        selectedItem = null;
        activeBatchId = null;
        trackingBatchId = null;
        validationJobId = null;
        dependencySummary.setText("Upload one or more CSV files to build a batch.");
        refreshGrid();
        refreshDetailPanel();
        progressSummary.setText("No live batch is running.");
        progressDetails.setVisible(false);
        progressBar.setVisible(false);
    }

    private EntityType detectEntityType(String fileName, List<String> headers) {
        String lower = fileName.toLowerCase();
        if (lower.contains("bill payment")) {
            return EntityType.BILL_PAYMENT;
        }
        if (lower.contains("bill")) {
            return EntityType.BILL;
        }
        if (lower.contains("receipt")) {
            return EntityType.SALES_RECEIPT;
        }
        if (lower.contains("expense")) {
            return EntityType.EXPENSE;
        }
        if (lower.contains("payment")) {
            return EntityType.PAYMENT;
        }
        if (headers.stream().anyMatch(header -> header.equalsIgnoreCase("AppliedAmount"))) {
            return EntityType.PAYMENT;
        }
        if (headers.stream().anyMatch(header -> header.equalsIgnoreCase("DepositToAccount"))) {
            return EntityType.SALES_RECEIPT;
        }
        if (headers.stream().anyMatch(header -> header.equalsIgnoreCase("DueDate"))) {
            return EntityType.BILL;
        }
        return EntityType.INVOICE;
    }

    private ImportBatchService.BatchFileRequest toBatchRequest(BatchDraftItem item) {
        return new ImportBatchService.BatchFileRequest(
                item.getPosition(),
                item.getEntityType(),
                item.getFileName(),
                item.getSelectedProfile() == null ? null : item.getSelectedProfile().name(),
                item.getPreviewSummary(),
                skipInvalidRows.getValue());
    }

    private void notifySuccess(String message) {
        Notification notification = Notification.show(message);
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private void notifyWarning(String message) {
        Notification notification = Notification.show(message);
        notification.addThemeVariants(NotificationVariant.LUMO_WARNING);
    }

    private void refreshBatchProgress() {
        if (trackingBatchId == null) {
            return;
        }
        importProgressService.findBatchProgress(trackingBatchId).ifPresent(snapshot -> {
            progressBar.setVisible(true);
            progressDetails.setVisible(true);
            progressBar.setIndeterminate(false);
            progressBar.setValue(snapshot.progressValue());
            progressSummary.setText("Batch #" + snapshot.batchId() + " is " + snapshot.status() + " | " + snapshot.percentLabel());
            String currentWork = snapshot.currentFileName() == null
                    ? "Preparing work queue"
                    : snapshot.currentEntityLabel() + " | " + snapshot.currentFileName();
            progressDetails.setText(snapshot.processedRows() + "/" + snapshot.plannedRunnableRows() + " runnable rows"
                    + " | completed files=" + snapshot.completedFiles() + "/" + snapshot.runnableFiles()
                    + " | " + currentWork
                    + " | " + snapshot.remainingLabel()
                    + " | " + snapshot.throughputLabel()
                    + " | " + snapshot.startedLabel());
            refreshBatchRunRows();
            if (snapshot.status() != com.example.quickbooksimporter.domain.ImportBatchStatus.RUNNING) {
                dependencySummary.setText("Batch " + snapshot.batchName() + " finished with status " + snapshot.status().name() + ".");
                trackingBatchId = null;
                stopPollingIfIdle();
            }
        });
    }

    private void refreshValidationJob() {
        if (validationJobId != null) {
            appJobService.findSnapshot(validationJobId).ifPresent(this::applyValidationSnapshot);
        }
    }

    private void applyValidationSnapshot(AppJobSnapshot snapshot) {
        progressBar.setVisible(true);
        progressDetails.setVisible(true);
        if (snapshot.status() == AppJobStatus.QUEUED || snapshot.status() == AppJobStatus.RUNNING) {
            progressBar.setIndeterminate(false);
            progressBar.setValue(snapshot.progressValue());
            progressSummary.setText(snapshot.description() + " is " + snapshot.status() + " | " + snapshot.percentLabel());
            progressDetails.setText(snapshot.summaryMessage());
            return;
        }
        validateAllButton.setEnabled(true);
        validateSelectedButton.setEnabled(true);
        if (snapshot.status() == AppJobStatus.FAILED) {
            progressBar.setValue(0d);
            progressSummary.setText("Validation failed");
            progressDetails.setText(snapshot.summaryMessage());
            notifyWarning("Validation failed: " + snapshot.summaryMessage());
            validationJobId = null;
            stopPollingIfIdle();
            return;
        }
        BatchValidationJobResult result = appJobService.readResult(snapshot.resultPayload(), BatchValidationJobResult.class);
        result.items().forEach(this::applyValidationResult);
        dependencySummary.setText(result.dependencyWarnings().isEmpty()
                ? "All validations completed. Files with invalid rows remain blocked, valid independent files can run."
                : String.join(" | ", result.dependencyWarnings()));
        refreshGrid();
        refreshDetailPanel();
        progressBar.setValue(1d);
        progressSummary.setText("Validation finished");
        progressDetails.setText(snapshot.summaryMessage());
        notifySuccess(snapshot.summaryMessage());
        validationJobId = null;
        stopPollingIfIdle();
    }

    private void applyValidationResult(BatchValidationJobResult.BatchValidationItemResult result) {
        items.stream()
                .filter(item -> item.getPosition() == result.position())
                .findFirst()
                .ifPresent(item -> {
                    ImportPreviewSummary summary = importPreviewJobCodec.toSummary(result.previewResult(), result.suggestedProfileName());
                    item.setPreviewSummary(summary);
                    item.setStatusText(summary.runStatusSummary(
                            skipInvalidRows.getValue() ? com.example.quickbooksimporter.service.ImportExecutionMode.IMPORT_READY_ONLY
                                    : com.example.quickbooksimporter.service.ImportExecutionMode.STRICT_ALL_ROWS).name());
                    item.setRowSummary(summary.totalRows() + " total / " + summary.readyRows() + " ready / " + summary.invalidRows() + " invalid");
                    item.setWarningText(summary.warnings().isEmpty() ? "Validated." : String.join(" | ", summary.warnings()));
                    if (item.getSelectedProfile() == null && result.suggestedProfileName() != null) {
                        workflowFacade.listProfiles(item.getEntityType()).stream()
                                .filter(candidate -> candidate.name().equals(result.suggestedProfileName()))
                                .findFirst()
                                .ifPresent(item::setSelectedProfile);
                    }
                });
    }

    private void stopPollingIfIdle() {
        if (validationJobId == null && trackingBatchId == null) {
            getUI().ifPresent(ui -> ui.setPollInterval(-1));
        }
    }

    private void refreshBatchRunRows() {
        if (trackingBatchId == null) {
            return;
        }
        List<ImportRunEntity> runs = importHistoryService.runsForBatch(trackingBatchId);
        runs.forEach(run -> items.stream()
                .filter(candidate -> Objects.equals(candidate.getFileName(), run.getSourceFileName()))
                .findFirst()
                .ifPresent(item -> {
                    item.setRunStatusText(run.getStatus().name());
                    item.setRunId(run.getId());
                    item.setWarningText("attempted=" + run.getAttemptedRows()
                            + ", imported=" + run.getImportedRows()
                            + ", skipped=" + run.getSkippedRows());
                }));
        refreshGrid();
        refreshDetailPanel();
    }

    public static class BatchDraftItem {
        private final int position;
        private final String fileName;
        private final byte[] bytes;
        private EntityType entityType;
        private MappingProfileSummary selectedProfile;
        private ImportPreviewSummary previewSummary;
        private String rowSummary = "Not validated";
        private String statusText = "Uploaded";
        private String runStatusText = "Pending";
        private String warningText = "";
        private Long runId;

        public BatchDraftItem(int position, String fileName, byte[] bytes, EntityType entityType) {
            this.position = position;
            this.fileName = fileName;
            this.bytes = bytes;
            this.entityType = entityType;
        }

        public int getPosition() {
            return position;
        }

        public String getFileName() {
            return fileName;
        }

        public byte[] getBytes() {
            return bytes;
        }

        public EntityType getEntityType() {
            return entityType;
        }

        public void setEntityType(EntityType entityType) {
            this.entityType = entityType;
        }

        public MappingProfileSummary getSelectedProfile() {
            return selectedProfile;
        }

        public void setSelectedProfile(MappingProfileSummary selectedProfile) {
            this.selectedProfile = selectedProfile;
        }

        public ImportPreviewSummary getPreviewSummary() {
            return previewSummary;
        }

        public void setPreviewSummary(ImportPreviewSummary previewSummary) {
            this.previewSummary = previewSummary;
        }

        public String getRowSummary() {
            return rowSummary;
        }

        public void setRowSummary(String rowSummary) {
            this.rowSummary = rowSummary;
        }

        public String getStatusText() {
            return statusText;
        }

        public void setStatusText(String statusText) {
            this.statusText = statusText;
        }

        public String getRunStatusText() {
            return runStatusText;
        }

        public void setRunStatusText(String runStatusText) {
            this.runStatusText = runStatusText;
        }

        public String getWarningText() {
            return warningText;
        }

        public void setWarningText(String warningText) {
            this.warningText = warningText;
        }

        public Long getRunId() {
            return runId;
        }

        public void setRunId(Long runId) {
            this.runId = runId;
        }
    }
}
