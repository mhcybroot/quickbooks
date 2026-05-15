package com.example.quickbooksimporter.ui;

import com.example.quickbooksimporter.domain.AppJobStatus;
import com.example.quickbooksimporter.domain.ImportRowStatus;
import com.example.quickbooksimporter.domain.ImportRunStatus;
import com.example.quickbooksimporter.domain.NormalizedSalesReceiptField;
import com.example.quickbooksimporter.domain.SalesReceiptImportPreview;
import com.example.quickbooksimporter.domain.SalesReceiptImportPreviewRow;
import com.example.quickbooksimporter.service.AppJobService;
import com.example.quickbooksimporter.service.AppJobSnapshot;
import com.example.quickbooksimporter.service.ImportExecutionMode;
import com.example.quickbooksimporter.service.ImportExecutionOptions;
import com.example.quickbooksimporter.service.ImportBackgroundService;
import com.example.quickbooksimporter.service.ImportHistoryService;
import com.example.quickbooksimporter.service.ImportProgressService;
import com.example.quickbooksimporter.service.ImportPreviewJobCodec;
import com.example.quickbooksimporter.service.ImportPreviewJobResult;
import com.example.quickbooksimporter.service.ImportRunProgressSnapshot;
import com.example.quickbooksimporter.service.InvoiceCsvParser;
import com.example.quickbooksimporter.service.MappingProfileSummary;
import com.example.quickbooksimporter.service.ParsedCsvDocument;
import com.example.quickbooksimporter.service.DateFormatOption;
import com.example.quickbooksimporter.service.QuickBooksJobService;
import com.example.quickbooksimporter.service.SalesReceiptImportService;
import com.example.quickbooksimporter.service.SalesReceiptMappingProfileService;
import com.example.quickbooksimporter.ui.components.UiComponents;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Route(value = "sales-receipts", layout = MainLayout.class)
@PageTitle("Sales Receipt Import")
@PermitAll
public class SalesReceiptImportView extends VerticalLayout {

    private final InvoiceCsvParser parser;
    private final SalesReceiptMappingProfileService mappingProfileService;
    private final SalesReceiptImportService importService;
    private final ImportBackgroundService backgroundService;
    private final ImportHistoryService importHistoryService;
    private final ImportProgressService importProgressService;
    private final QuickBooksJobService quickBooksJobService;
    private final AppJobService appJobService;
    private final ImportPreviewJobCodec importPreviewJobCodec;

    private final MemoryBuffer uploadBuffer = new MemoryBuffer();
    private final Upload upload = new Upload(uploadBuffer);
    private final ComboBox<MappingProfileSummary> savedProfiles = new ComboBox<>("Saved Receipt Mapping Profiles");
    private final ComboBox<DateFormatOption> txnDateFormat = new ComboBox<>("Transaction Date Format");
    private final ComboBox<ImportRowStatus> previewFilter = new ComboBox<>("Preview Filter");
    private final TextField profileName = new TextField("New Profile Name");
    private final FormLayout mappingForm = new FormLayout();
    private final Grid<SalesReceiptImportPreviewRow> previewGrid = new Grid<>(SalesReceiptImportPreviewRow.class,
            false);
    private final Paragraph summary = new Paragraph("Upload a sales receipt CSV to begin.");
    private final Paragraph progressSummary = new Paragraph("No live import is running.");
    private final Paragraph progressDetails = new Paragraph();
    private final ProgressBar progressBar = new ProgressBar();
    private final Button previewButton = new Button("Preview & Validate");

    private final Map<NormalizedSalesReceiptField, ComboBox<String>> fieldSelectors = new EnumMap<>(
            NormalizedSalesReceiptField.class);

    private byte[] uploadedBytes;
    private String uploadedFileName;
    private String trackingFileName;
    private Long trackingRunId;
    private Long previewJobId;
    private List<String> currentHeaders = List.of();
    private SalesReceiptImportPreview currentPreview;
    private boolean pollListenerRegistered;
    private boolean autoImportOnPreviewComplete;

    public SalesReceiptImportView(InvoiceCsvParser parser,
            SalesReceiptMappingProfileService mappingProfileService,
            SalesReceiptImportService importService,
            ImportBackgroundService backgroundService,
            ImportHistoryService importHistoryService,
            ImportProgressService importProgressService,
            QuickBooksJobService quickBooksJobService,
            AppJobService appJobService,
            ImportPreviewJobCodec importPreviewJobCodec) {
        this.parser = parser;
        this.mappingProfileService = mappingProfileService;
        this.importService = importService;
        this.backgroundService = backgroundService;
        this.importHistoryService = importHistoryService;
        this.importProgressService = importProgressService;
        this.quickBooksJobService = quickBooksJobService;
        this.appJobService = appJobService;
        this.importPreviewJobCodec = importPreviewJobCodec;

        addClassName("corp-page");
        setSizeFull();
        add(new H2("Sales Receipt Import"),
                new Paragraph("Upload, map, validate, review, and import grouped sales receipts into QuickBooks."),
                UiComponents.importStepper("Upload"));
        configureUpload();
        configureProfiles();
        configureMappingForm();
        configurePreviewGrid();
        configureActions();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        if (!pollListenerRegistered) {
            attachEvent.getUI().addPollListener(event -> {
                refreshPreviewJob();
                refreshBackgroundProgress();
            });
            pollListenerRegistered = true;
        }
    }

    private void configureUpload() {
        upload.setAcceptedFileTypes(".csv");
        upload.addSucceededListener(event -> {
            uploadedFileName = event.getFileName();
            try {
                uploadedBytes = uploadBuffer.getInputStream().readAllBytes();
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
            ParsedCsvDocument document = parser.parse(new java.io.ByteArrayInputStream(uploadedBytes));
            currentHeaders = document.headers();
            Map<NormalizedSalesReceiptField, String> defaults = mappingProfileService.defaultMapping(currentHeaders);
            fieldSelectors.forEach((field, selector) -> {
                selector.setItems(currentHeaders);
                selector.setValue(defaults.get(field));
            });
            summary.setText("Loaded " + uploadedFileName + " with " + document.rows().size() + " data rows.");
        });
        add(UiComponents.card(UiComponents.sectionTitle("Stage 1: Upload CSV"), upload));
    }

    private void configureProfiles() {
        savedProfiles.setItems(mappingProfileService.listProfiles());
        savedProfiles.setItemLabelGenerator(MappingProfileSummary::name);
        txnDateFormat.setItems(DateFormatOption.values());
        txnDateFormat.setItemLabelGenerator(DateFormatOption::label);
        txnDateFormat.setValue(DateFormatOption.AUTO);
        savedProfiles.addValueChangeListener(event -> {
            if (event.getValue() == null || currentHeaders.isEmpty()) {
                return;
            }
            Map<NormalizedSalesReceiptField, String> mapping = mappingProfileService.loadProfile(event.getValue().id());
            fieldSelectors.forEach((field, selector) -> {
                selector.setItems(currentHeaders);
                selector.setValue(mapping.get(field));
            });
        });
        HorizontalLayout profileRow = new HorizontalLayout(savedProfiles, profileName, txnDateFormat);
        profileRow.setWidthFull();
        profileRow.expand(savedProfiles, profileName);
        add(UiComponents.card(UiComponents.sectionTitle("Stage 2: Mapping Profile"), profileRow));
    }

    private void configureMappingForm() {
        mappingForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("800px", 2));
        for (NormalizedSalesReceiptField field : NormalizedSalesReceiptField.values()) {
            ComboBox<String> selector = new ComboBox<>(field.name());
            selector.setWidthFull();
            selector.setPlaceholder(field.sampleHeader());
            fieldSelectors.put(field, selector);
            mappingForm.add(selector);
        }
        add(UiComponents.card(new H3("Stage 3: Column Mapping"), mappingForm));
    }

    private void configurePreviewGrid() {
        previewGrid.addColumn(SalesReceiptImportPreviewRow::rowNumber).setHeader("Row");
        previewGrid.addColumn(SalesReceiptImportPreviewRow::receiptNo).setHeader("Receipt #");
        previewGrid.addColumn(SalesReceiptImportPreviewRow::customer).setHeader("Customer");
        previewGrid.addColumn(SalesReceiptImportPreviewRow::lineCount).setHeader("Lines");
        previewGrid.addColumn(SalesReceiptImportPreviewRow::status).setHeader("Status");
        previewGrid.addColumn(SalesReceiptImportPreviewRow::message).setHeader("Message").setAutoWidth(true)
                .setFlexGrow(1);
        previewGrid.setHeight("360px");
        previewGrid.addClassName("corp-grid");
        previewFilter.setItems(ImportRowStatus.values());
        previewFilter.addValueChangeListener(event -> applyPreviewFilter());
        progressBar.setWidthFull();
        progressBar.setVisible(false);
        progressDetails.setVisible(false);
        add(UiComponents.card(new H3("Stage 4: Validation Preview"), summary, progressSummary, progressBar,
                progressDetails, previewFilter, previewGrid));
    }

    private void configureActions() {
        previewButton.addClickListener(event -> previewImport());
        Button saveProfileButton = new Button("Save Mapping Profile", event -> saveProfile());
        Button importButton = new Button("Import Sales Receipts",
                event -> importPreview(ImportExecutionMode.STRICT_ALL_ROWS));
        Button importReadyOnlyButton = new Button("Import Ready Rows Only",
                event -> importPreview(ImportExecutionMode.IMPORT_READY_ONLY));
        Button directImportButton = new Button("Import Ready Rows (Skip Validation)", event -> directImport());
        Button historyButton = new Button("Open History", event -> UI.getCurrent().navigate(ImportHistoryView.class));
        previewButton.addThemeName("primary");
        importButton.addThemeName("primary");
        HorizontalLayout actions = new HorizontalLayout(previewButton, saveProfileButton, importButton,
                importReadyOnlyButton, directImportButton, historyButton);
        actions.addClassName("corp-action-bar");
        add(UiComponents.card(UiComponents.sectionTitle("Stage 5: Execute"), actions));
    }

    private void previewImport() {
        if (uploadedBytes == null) {
            notifyWarning("Upload a CSV file first.");
            return;
        }
        previewButton.setEnabled(false);
        previewJobId = quickBooksJobService.enqueueImportPreview(
                com.example.quickbooksimporter.domain.EntityType.SALES_RECEIPT,
                new QuickBooksJobService.ImportPreviewRequest(
                        uploadedFileName, uploadedBytes,
                        txnDateFormat.getValue() == null ? DateFormatOption.AUTO : txnDateFormat.getValue(),
                        false, Map.of(), Map.of(), Map.of(), currentMapping(), Map.of(), Map.of(), Map.of(), false))
                .getId();
        summary.setText("Preview started in background. You can keep using the page while validation runs.");
        progressSummary.setText("Preview job is queued.");
        progressDetails.setText("QuickBooks validation checks will update here.");
        progressBar.setVisible(true);
        progressDetails.setVisible(true);
        progressBar.setIndeterminate(true);
        getUI().ifPresent(ui -> ui.setPollInterval(3000));
    }

    private void directImport() {
        if (uploadedBytes == null) {
            notifyWarning("Upload a CSV file first.");
            return;
        }
        previewButton.setEnabled(false);
        autoImportOnPreviewComplete = true;
        previewJobId = quickBooksJobService.enqueueImportPreview(
                com.example.quickbooksimporter.domain.EntityType.SALES_RECEIPT,
                new QuickBooksJobService.ImportPreviewRequest(
                        uploadedFileName, uploadedBytes,
                        txnDateFormat.getValue() == null ? DateFormatOption.AUTO : txnDateFormat.getValue(),
                        false, Map.of(), Map.of(), Map.of(), currentMapping(), Map.of(), Map.of(), Map.of(), true))
                .getId();
        summary.setText("Mapping CSV... import will start immediately after.");
        progressSummary.setText("Preparing fast import.");
        progressBar.setVisible(true);
        progressDetails.setVisible(true);
        progressBar.setIndeterminate(true);
        getUI().ifPresent(ui -> ui.setPollInterval(500));
    }

    private void saveProfile() {
        if (profileName.isEmpty()) {
            notifyWarning("Enter a profile name first.");
            return;
        }
        mappingProfileService.saveProfile(profileName.getValue(), currentMapping());
        savedProfiles.setItems(mappingProfileService.listProfiles());
        notifySuccess("Sales receipt mapping profile saved.");
    }

    private void importPreview(ImportExecutionMode mode) {
        if (currentPreview == null) {
            notifyWarning("Run preview first.");
            return;
        }
        trackingRunId = backgroundService.enqueueForCurrentCompany(
                com.example.quickbooksimporter.domain.EntityType.SALES_RECEIPT,
                uploadedFileName,
                savedProfiles.getOptionalValue().map(MappingProfileSummary::name).orElse(profileName.getValue()),
                currentPreview,
                new ImportExecutionOptions(null, null, null, mode));
        String message = "Background import started. Open Import History to track live progress.";
        notifySuccess(message);
        summary.setText(message);
        trackingFileName = uploadedFileName;
        progressBar.setVisible(true);
        progressDetails.setVisible(true);
        progressBar.setIndeterminate(true);
        getUI().ifPresent(ui -> ui.setPollInterval(3000));
        refreshBackgroundProgress();
    }

    private Map<NormalizedSalesReceiptField, String> currentMapping() {
        Map<NormalizedSalesReceiptField, String> mapping = new EnumMap<>(NormalizedSalesReceiptField.class);
        fieldSelectors.forEach((field, selector) -> mapping.put(field, selector.getValue()));
        return mapping;
    }

    private void notifySuccess(String message) {
        Notification notification = Notification.show(Objects.requireNonNull(message));
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private void notifyWarning(String message) {
        Notification notification = Notification.show(Objects.requireNonNull(message));
        notification.addThemeVariants(NotificationVariant.LUMO_WARNING);
    }

    private void applyPreviewFilter() {
        if (currentPreview == null) {
            previewGrid.setItems(List.of());
            return;
        }
        ImportRowStatus filter = previewFilter.getValue();
        previewGrid.setItems(currentPreview.rows().stream()
                .filter(row -> filter == null || row.status() == filter)
                .toList());
    }

    private void refreshBackgroundProgress() {
        if (trackingRunId != null) {
            importProgressService.findRunProgress(trackingRunId)
                    .ifPresent(this::applyRunProgress);
        } else if (trackingFileName != null && !trackingFileName.isBlank()) {
            importProgressService
                    .findLatestRunProgressForFile(com.example.quickbooksimporter.domain.EntityType.SALES_RECEIPT,
                            trackingFileName)
                    .ifPresent(this::applyRunProgress);
        }
    }

    private void applyRunProgress(ImportRunProgressSnapshot snapshot) {
        progressBar.setVisible(true);
        progressDetails.setVisible(true);
        progressBar.setIndeterminate(false);
        progressBar.setValue(snapshot.progressValue());
        progressSummary
                .setText("Run #" + snapshot.runId() + " is " + snapshot.status() + " | " + snapshot.percentLabel());
        progressDetails.setText(snapshot.processedRows() + "/" + snapshot.runnableRows() + " runnable rows"
                + " | imported=" + snapshot.importedRows()
                + " | skipped=" + snapshot.skippedRows()
                + " | " + snapshot.remainingLabel()
                + " | " + snapshot.throughputLabel()
                + " | " + snapshot.startedLabel());
        if (snapshot.status() != ImportRunStatus.QUEUED && snapshot.status() != ImportRunStatus.RUNNING) {
            trackingFileName = null;
            trackingRunId = null;
            stopPollingIfIdle();
        }
    }

    private void refreshPreviewJob() {
        if (previewJobId != null) {
            appJobService.findSnapshot(previewJobId).ifPresent(this::applyPreviewJobSnapshot);
        }
    }

    private void applyPreviewJobSnapshot(AppJobSnapshot snapshot) {
        progressBar.setVisible(true);
        progressDetails.setVisible(true);
        if (snapshot.status() == AppJobStatus.QUEUED || snapshot.status() == AppJobStatus.RUNNING) {
            progressBar.setIndeterminate(snapshot.totalUnits() <= 1);
            if (!progressBar.isIndeterminate()) {
                progressBar.setValue(snapshot.progressValue());
            }
            progressSummary
                    .setText(snapshot.description() + " is " + snapshot.status() + " | " + snapshot.percentLabel());
            progressDetails.setText(snapshot.summaryMessage());
            return;
        }
        previewButton.setEnabled(true);
        if (snapshot.status() == AppJobStatus.FAILED) {
            progressBar.setIndeterminate(false);
            progressBar.setValue(0d);
            progressSummary.setText("Preview failed");
            progressDetails.setText(snapshot.summaryMessage());
            notifyWarning("Preview failed: " + snapshot.summaryMessage());
            previewJobId = null;
            stopPollingIfIdle();
            return;
        }
        ImportPreviewJobResult result = appJobService.readResult(snapshot.resultPayload(),
                ImportPreviewJobResult.class);
        currentPreview = importPreviewJobCodec.readSalesReceiptPreview(result);
        applyPreviewFilter();
        summary.setText("Preview complete: " + result.readyRows() + " ready, " + result.invalidRows()
                + " invalid. Use 'Import Ready Rows Only' to skip invalid rows.");
        progressBar.setIndeterminate(false);
        progressBar.setValue(1d);
        progressSummary.setText("Preview finished");
        progressDetails.setText(snapshot.summaryMessage());
        previewJobId = null;
        stopPollingIfIdle();
        
        if (autoImportOnPreviewComplete) {
            autoImportOnPreviewComplete = false;
            importPreview(ImportExecutionMode.IMPORT_READY_ONLY);
        }
    }

    private void stopPollingIfIdle() {
        if (previewJobId == null && (trackingFileName == null || trackingFileName.isBlank()) && trackingRunId == null) {
            getUI().ifPresent(ui -> ui.setPollInterval(-1));
        }
    }
}
