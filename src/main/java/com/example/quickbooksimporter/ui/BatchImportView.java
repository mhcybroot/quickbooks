package com.example.quickbooksimporter.ui;

import com.example.quickbooksimporter.domain.EntityType;
import com.example.quickbooksimporter.persistence.ImportBatchEntity;
import com.example.quickbooksimporter.service.ImportBatchService;
import com.example.quickbooksimporter.service.ImportPreviewOptions;
import com.example.quickbooksimporter.service.ImportPreviewSummary;
import com.example.quickbooksimporter.service.ImportWorkflowFacade;
import com.example.quickbooksimporter.service.MappingProfileSummary;
import com.example.quickbooksimporter.service.QuickBooksInvoiceRef;
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
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MultiFileMemoryBuffer;
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
    private final QuickBooksConnectionStatus connectionStatus;

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

    private BatchDraftItem selectedItem;
    private Long activeBatchId;
    private boolean syncingDetailFields;

    public BatchImportView(ImportWorkflowFacade workflowFacade,
                           ImportBatchService batchService,
                           QuickBooksConnectionService connectionService) {
        this.workflowFacade = workflowFacade;
        this.batchService = batchService;
        this.connectionStatus = connectionService.getStatus();

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

        Button validateSelected = new Button("Validate Selected File", event -> validateSelected());
        validateSelected.addThemeName("primary");
        downloadAnchor.setVisible(false);
        downloadAnchor.setText("Download normalized CSV");

        add(UiComponents.card(
                new H3("Selected File"),
                detailSummary,
                new HorizontalLayout(entityType, profile, validateSelected, downloadAnchor)));
    }

    private void configureActions() {
        Button validateAll = new Button("Validate All", event -> validateAll());
        Button runAll = new Button("Run All", event -> runAll());
        Button openHistory = new Button("Open History", event -> UI.getCurrent().navigate(ImportHistoryView.class));
        Button clear = new Button("Clear Draft", event -> clearDraft());
        validateAll.addThemeName("primary");
        runAll.addThemeName("primary");

        skipInvalidRows.setValue(false);
        HorizontalLayout actions = new HorizontalLayout(batchName, validateAll, runAll, openHistory, clear);
        actions.add(skipInvalidRows);
        actions.setWidthFull();
        add(UiComponents.card(new H3("Batch Actions"), new Paragraph("Validate the queue first, then run valid files in system-managed order."), actions));
    }

    private void validateSelected() {
        if (selectedItem == null) {
            notifyWarning("Select a file first.");
            return;
        }
        validateItem(selectedItem, draftInvoiceRefsForBatch(selectedItem));
        refreshGrid();
        refreshDetailPanel();
    }

    private void validateAll() {
        if (items.isEmpty()) {
            notifyWarning("Upload at least one file first.");
            return;
        }
        ensureBatch();
        items.stream()
                .sorted(Comparator.comparingInt((BatchDraftItem item) -> item.getEntityType().batchPriority())
                        .thenComparingInt(BatchDraftItem::getPosition))
                .forEach(item -> validateItem(item, draftInvoiceRefsForBatch(item)));
        batchService.updateValidationSnapshot(activeBatchId, items.size(),
                (int) items.stream().filter(item -> item.getPreviewSummary() != null).count(),
                (int) items.stream().filter(item -> item.getPreviewSummary() != null
                        && !item.getPreviewSummary().hasBlockingIssues(
                        skipInvalidRows.getValue() ? com.example.quickbooksimporter.service.ImportExecutionMode.IMPORT_READY_ONLY
                                : com.example.quickbooksimporter.service.ImportExecutionMode.STRICT_ALL_ROWS)).count());
        List<String> warnings = batchService.dependencyWarnings(items.stream()
                .map(this::toBatchRequest)
                .toList());
        dependencySummary.setText(warnings.isEmpty()
                ? "All validations completed. Files with invalid rows remain blocked, valid independent files can run."
                : String.join(" | ", warnings));
        refreshGrid();
        refreshDetailPanel();
        notifySuccess("Validated " + items.size() + " files.");
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
        }
        ensureBatch();
        ImportBatchService.BatchExecutionReport report = batchService.executeBatch(activeBatchId, items.stream()
                .map(this::toBatchRequest)
                .toList());
        report.results().forEach(result -> {
            BatchDraftItem item = items.stream()
                    .filter(candidate -> Objects.equals(candidate.getFileName(), result.importRun().getSourceFileName()))
                    .findFirst()
                    .orElse(null);
            if (item != null) {
                item.setRunStatusText(result.importRun().getStatus().name());
                item.setRunId(result.importRun().getId());
                item.setWarningText(result.message());
            }
        });
        dependencySummary.setText("Batch " + report.batch().getBatchName() + " finished with status " + report.batch().getStatus().name() + ".");
        refreshGrid();
        refreshDetailPanel();
        notifySuccess("Batch run completed. Review history for row-level outcomes.");
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
        dependencySummary.setText("Upload one or more CSV files to build a batch.");
        refreshGrid();
        refreshDetailPanel();
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
