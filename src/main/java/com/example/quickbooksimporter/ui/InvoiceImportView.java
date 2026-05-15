package com.example.quickbooksimporter.ui;

import com.example.quickbooksimporter.domain.AppJobStatus;
import com.example.quickbooksimporter.domain.ImportPreview;
import com.example.quickbooksimporter.domain.ImportPreviewRow;
import com.example.quickbooksimporter.domain.ImportRowStatus;
import com.example.quickbooksimporter.domain.ImportRunStatus;
import com.example.quickbooksimporter.domain.NormalizedInvoiceField;
import com.example.quickbooksimporter.service.AppJobService;
import com.example.quickbooksimporter.service.AppJobSnapshot;
import com.example.quickbooksimporter.service.CsvMappingProfileService;
import com.example.quickbooksimporter.service.DateFormatOption;
import com.example.quickbooksimporter.service.ImportBackgroundService;
import com.example.quickbooksimporter.service.ImportExecutionMode;
import com.example.quickbooksimporter.service.ImportExecutionOptions;
import com.example.quickbooksimporter.service.ImportHistoryService;
import com.example.quickbooksimporter.service.ImportProgressService;
import com.example.quickbooksimporter.service.ImportPreviewJobCodec;
import com.example.quickbooksimporter.service.ImportPreviewJobResult;
import com.example.quickbooksimporter.service.ImportRunProgressSnapshot;
import com.example.quickbooksimporter.service.ImportWorkflowFacade;
import com.example.quickbooksimporter.service.InvoiceGroupingPreferenceService;
import com.example.quickbooksimporter.service.InvoiceCsvParser;
import com.example.quickbooksimporter.service.InvoiceImportService;
import com.example.quickbooksimporter.service.MappingProfileSummary;
import com.example.quickbooksimporter.service.ParsedCsvDocument;
import com.example.quickbooksimporter.service.QuickBooksJobService;
import com.example.quickbooksimporter.ui.components.UiComponents;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
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
import com.vaadin.flow.server.StreamResource;
import jakarta.annotation.security.PermitAll;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Route(value = "invoices", layout = MainLayout.class)
@PageTitle("Invoice Import")
@PermitAll
public class InvoiceImportView extends VerticalLayout {

    private final InvoiceCsvParser parser;
    private final CsvMappingProfileService mappingProfileService;
    private final InvoiceImportService invoiceImportService;
    private final ImportWorkflowFacade workflowFacade;
    private final InvoiceGroupingPreferenceService invoiceGroupingPreferenceService;
    private final ImportBackgroundService backgroundService;
    private final ImportHistoryService importHistoryService;
    private final ImportProgressService importProgressService;
    private final QuickBooksJobService quickBooksJobService;
    private final AppJobService appJobService;
    private final ImportPreviewJobCodec importPreviewJobCodec;

    private final MemoryBuffer uploadBuffer = new MemoryBuffer();
    private final Upload upload = new Upload(uploadBuffer);
    private final ComboBox<MappingProfileSummary> savedProfiles = new ComboBox<>("Saved Mapping Profiles");
    private final ComboBox<DateFormatOption> invoiceDateFormat = new ComboBox<>("Invoice Date Format");
    private final ComboBox<ImportRowStatus> previewFilter = new ComboBox<>("Preview Filter");
    private final TextField profileName = new TextField("New Profile Name");
    private final Button groupingToggle = new Button();
    private final FormLayout mappingForm = new FormLayout();
    private final Grid<ImportPreviewRow> previewGrid = new Grid<>(ImportPreviewRow.class, false);
    private final Paragraph summary = new Paragraph("Upload a CSV to begin.");
    private final Paragraph mappingHint = new Paragraph("Expected headers will be suggested automatically after upload.");
    private final Paragraph groupingHint = new Paragraph();
    private final Paragraph progressSummary = new Paragraph("No live import is running.");
    private final Paragraph progressDetails = new Paragraph();
    private final ProgressBar progressBar = new ProgressBar();
    private final Anchor downloadAnchor = new Anchor();
    private final HorizontalLayout kpiRow = new HorizontalLayout();
    private final VerticalLayout kpiTotal = UiComponents.softCard();
    private final VerticalLayout kpiReady = UiComponents.softCard();
    private final VerticalLayout kpiInvalid = UiComponents.softCard();
    private final Button previewButton = new Button("Preview & Validate");

    private final Map<NormalizedInvoiceField, ComboBox<String>> fieldSelectors = new EnumMap<>(NormalizedInvoiceField.class);

    private byte[] uploadedBytes;
    private String uploadedFileName;
    private String trackingFileName;
    private Long previewJobId;
    private List<String> currentHeaders = List.of();
    private ImportPreview currentPreview;
    private boolean invoiceGroupingEnabled;
    private boolean pollListenerRegistered;

    public InvoiceImportView(InvoiceCsvParser parser,
                             CsvMappingProfileService mappingProfileService,
                             InvoiceImportService invoiceImportService,
                             ImportWorkflowFacade workflowFacade,
                             InvoiceGroupingPreferenceService invoiceGroupingPreferenceService,
                             ImportBackgroundService backgroundService,
                             ImportHistoryService importHistoryService,
                             ImportProgressService importProgressService,
                             QuickBooksJobService quickBooksJobService,
                             AppJobService appJobService,
                             ImportPreviewJobCodec importPreviewJobCodec) {
        this.parser = parser;
        this.mappingProfileService = mappingProfileService;
        this.invoiceImportService = invoiceImportService;
        this.workflowFacade = workflowFacade;
        this.invoiceGroupingPreferenceService = invoiceGroupingPreferenceService;
        this.backgroundService = backgroundService;
        this.importHistoryService = importHistoryService;
        this.importProgressService = importProgressService;
        this.quickBooksJobService = quickBooksJobService;
        this.appJobService = appJobService;
        this.importPreviewJobCodec = importPreviewJobCodec;
        this.invoiceGroupingEnabled = invoiceGroupingPreferenceService.isGroupingEnabled();

        setSizeFull();
        addClassName("corp-page");
        add(new H2("Invoice Import Workspace"),
                new Paragraph("Upload, map, validate, review, and import invoices in a faster operator workflow."),
                UiComponents.importStepper("Upload"));
        configureKpis();
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
        upload.addClassName("corp-card-soft");
        upload.addSucceededListener(event -> {
            uploadedFileName = event.getFileName();
            try {
                uploadedBytes = uploadBuffer.getInputStream().readAllBytes();
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
            ParsedCsvDocument document = parser.parse(new java.io.ByteArrayInputStream(uploadedBytes));
            currentHeaders = document.headers();
            Map<NormalizedInvoiceField, String> defaults = mappingProfileService.defaultInvoiceMapping(currentHeaders);
            fieldSelectors.forEach((field, selector) -> {
                selector.setItems(currentHeaders);
                selector.setValue(defaults.get(field));
            });
            workflowFacade.lastUsedProfile(com.example.quickbooksimporter.domain.EntityType.INVOICE)
                    .ifPresent(lastProfile -> {
                        savedProfiles.setValue(lastProfile);
                        Map<NormalizedInvoiceField, String> recentMapping = mappingProfileService.loadProfile(lastProfile.id());
                        fieldSelectors.forEach((field, selector) -> selector.setValue(recentMapping.get(field)));
                    });
            summary.setText("Loaded " + uploadedFileName + " with " + document.rows().size() + " data rows.");
            updateMappingHint();
            refreshKpis(document.rows().size(), 0, 0);
        });
        add(UiComponents.card(UiComponents.sectionTitle("Stage 1: Upload CSV"), upload));
    }

    private void configureProfiles() {
        savedProfiles.setItems(mappingProfileService.listProfiles());
        savedProfiles.setItemLabelGenerator(MappingProfileSummary::name);
        invoiceDateFormat.setItems(DateFormatOption.values());
        invoiceDateFormat.setItemLabelGenerator(DateFormatOption::label);
        invoiceDateFormat.setValue(DateFormatOption.AUTO);
        savedProfiles.addValueChangeListener(event -> {
            if (event.getValue() == null || currentHeaders.isEmpty()) {
                return;
            }
            Map<NormalizedInvoiceField, String> mapping = mappingProfileService.loadProfile(event.getValue().id());
            fieldSelectors.forEach((field, selector) -> {
                selector.setItems(currentHeaders);
                selector.setValue(mapping.get(field));
            });
            updateMappingHint();
        });
        Button useLastProfile = new Button("Use Last Profile", event -> workflowFacade
                .lastUsedProfile(com.example.quickbooksimporter.domain.EntityType.INVOICE)
                .ifPresent(savedProfiles::setValue));
        groupingToggle.addClickListener(event -> toggleInvoiceGrouping());
        updateGroupingToggleUi();
        HorizontalLayout profileRow = new HorizontalLayout(savedProfiles, profileName, invoiceDateFormat, useLastProfile, groupingToggle);
        profileRow.setWidthFull();
        profileRow.expand(savedProfiles, profileName);
        add(UiComponents.card(UiComponents.sectionTitle("Stage 2: Mapping Profile"), profileRow, mappingHint, groupingHint));
    }

    private void configureMappingForm() {
        mappingForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("800px", 2));
        for (NormalizedInvoiceField field : NormalizedInvoiceField.values()) {
            ComboBox<String> selector = new ComboBox<>(field.name());
            selector.setWidthFull();
            selector.setPlaceholder(field.sampleHeader());
            selector.setHelperText("Expected header: " + field.sampleHeader());
            selector.addValueChangeListener(event -> updateMappingHint());
            fieldSelectors.put(field, selector);
            mappingForm.add(selector);
        }
        add(UiComponents.card(new H3("Stage 3: Column Mapping"), mappingForm));
    }

    private void configurePreviewGrid() {
        previewGrid.addColumn(ImportPreviewRow::rowNumber).setHeader("Row");
        previewGrid.addColumn(ImportPreviewRow::invoiceNo).setHeader("Invoice #");
        previewGrid.addColumn(ImportPreviewRow::customer).setHeader("Customer");
        previewGrid.addColumn(ImportPreviewRow::itemName).setHeader("Item");
        previewGrid.addColumn(ImportPreviewRow::status).setHeader("Status");
        previewGrid.addColumn(ImportPreviewRow::message).setHeader("Message").setAutoWidth(true).setFlexGrow(1);
        previewGrid.setHeight("360px");
        previewGrid.addClassName("corp-grid");
        previewFilter.setItems(ImportRowStatus.values());
        previewFilter.addValueChangeListener(event -> applyPreviewFilter());
        progressBar.setWidthFull();
        progressBar.setVisible(false);
        progressDetails.setVisible(false);
        add(UiComponents.card(new H3("Stage 4: Validation Preview"), summary, progressSummary, progressBar, progressDetails, previewFilter, previewGrid));
    }

    private void configureActions() {
        previewButton.addClickListener(event -> previewImport());
        Button saveProfileButton = new Button("Save Mapping Profile", event -> saveProfile());
        Button importButton = new Button("Import to QuickBooks", event -> importPreview(ImportExecutionMode.STRICT_ALL_ROWS));
        Button importReadyOnlyButton = new Button("Import Ready Rows Only", event -> importPreview(ImportExecutionMode.IMPORT_READY_ONLY));
        Button openHistoryButton = new Button("Open History", event -> UI.getCurrent().navigate(ImportHistoryView.class));
        previewButton.addThemeName("primary");
        importButton.addThemeName("primary");
        importReadyOnlyButton.addThemeName("contrast");
        downloadAnchor.setText("Download normalized CSV");
        downloadAnchor.setVisible(false);

        HorizontalLayout actions = new HorizontalLayout(previewButton, saveProfileButton, importButton, importReadyOnlyButton, downloadAnchor, openHistoryButton);
        actions.addClassName("corp-action-bar");
        add(UiComponents.card(UiComponents.sectionTitle("Stage 5: Execute"), actions));
    }

    private void configureKpis() {
        kpiRow.setWidthFull();
        kpiRow.setSpacing(true);
        kpiRow.add(kpiTotal, kpiReady, kpiInvalid);
        kpiRow.setFlexGrow(1, kpiTotal, kpiReady, kpiInvalid);
        refreshKpis(0, 0, 0);
        add(kpiRow);
    }

    private void previewImport() {
        if (uploadedBytes == null) {
            notifyWarning("Upload a CSV file first.");
            return;
        }
        previewButton.setEnabled(false);
        var job = quickBooksJobService.enqueueImportPreview(
                com.example.quickbooksimporter.domain.EntityType.INVOICE,
                new QuickBooksJobService.ImportPreviewRequest(
                        uploadedFileName,
                        uploadedBytes,
                        invoiceDateFormat.getValue() == null ? DateFormatOption.AUTO : invoiceDateFormat.getValue(),
                        invoiceGroupingEnabled,
                        currentMapping(),
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        Map.of()));
        previewJobId = job.getId();
        summary.setText("Preview started in background. You can keep using the page while validation runs.");
        progressSummary.setText("Preview job is queued.");
        progressDetails.setText("QuickBooks validations will update here when ready.");
        progressBar.setVisible(true);
        progressDetails.setVisible(true);
        progressBar.setIndeterminate(true);
        getUI().ifPresent(ui -> ui.setPollInterval(3000));
    }

    private void saveProfile() {
        if (profileName.isEmpty()) {
            notifyWarning("Enter a profile name first.");
            return;
        }
        mappingProfileService.saveProfile(profileName.getValue(), currentMapping());
        savedProfiles.setItems(mappingProfileService.listProfiles());
        notifySuccess("Mapping profile saved.");
    }

    private void importPreview(ImportExecutionMode mode) {
        if (currentPreview == null) {
            notifyWarning("Run preview first.");
            return;
        }
        backgroundService.enqueueForCurrentCompany(
                com.example.quickbooksimporter.domain.EntityType.INVOICE,
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

    private Map<NormalizedInvoiceField, String> currentMapping() {
        Map<NormalizedInvoiceField, String> mapping = new EnumMap<>(NormalizedInvoiceField.class);
        fieldSelectors.forEach((field, selector) -> mapping.put(field, selector.getValue()));
        return mapping;
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

    private void updateMappingHint() {
        long mappedFields = fieldSelectors.values().stream()
                .filter(selector -> selector.getValue() != null && !selector.getValue().isBlank())
                .count();
        mappingHint.setText("Mapping coverage: " + mappedFields + "/" + fieldSelectors.size()
                + " fields mapped. Review required invoice headers before validation.");
    }

    private void toggleInvoiceGrouping() {
        invoiceGroupingEnabled = !invoiceGroupingEnabled;
        invoiceGroupingPreferenceService.saveGroupingEnabled(invoiceGroupingEnabled);
        currentPreview = null;
        previewGrid.setItems(List.of());
        downloadAnchor.setVisible(false);
        summary.setText("Invoice grouping " + (invoiceGroupingEnabled ? "enabled" : "disabled") + ". Run preview again to apply the change.");
        refreshKpis(uploadedBytes == null ? 0 : refreshTotalRows(), 0, 0);
        updateGroupingToggleUi();
    }

    private void updateGroupingToggleUi() {
        groupingToggle.setText(invoiceGroupingEnabled ? "Invoice Grouping: On" : "Invoice Grouping: Off");
        groupingHint.setText(invoiceGroupingEnabled
                ? "Repeated invoice numbers will be merged into one invoice with multiple lines. This setting is remembered."
                : "Each CSV row stays independent. Turn grouping on to merge repeated invoice numbers before validation and import.");
    }

    private int refreshTotalRows() {
        if (uploadedBytes == null) {
            return 0;
        }
        try {
            return parser.parse(new java.io.ByteArrayInputStream(uploadedBytes)).rows().size();
        } catch (Exception exception) {
            return 0;
        }
    }

    private void refreshKpis(int totalRows, int readyRows, int invalidRows) {
        kpiTotal.removeAll();
        kpiReady.removeAll();
        kpiInvalid.removeAll();
        kpiTotal.add(UiComponents.kpi("Total Rows", String.valueOf(totalRows), "Rows detected in the active file"));
        kpiReady.add(UiComponents.kpi("Ready", String.valueOf(readyRows), "Rows passing validation"));
        kpiInvalid.add(UiComponents.kpi("Invalid", String.valueOf(invalidRows), "Rows requiring fixes"));
    }

    private void notifySuccess(String message) {
        Notification notification = Notification.show(Objects.requireNonNull(message));
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private void notifyWarning(String message) {
        Notification notification = Notification.show(Objects.requireNonNull(message));
        notification.addThemeVariants(NotificationVariant.LUMO_WARNING);
    }

    private void refreshBackgroundProgress() {
        if (trackingFileName == null || trackingFileName.isBlank()) {
            return;
        }
        importProgressService.findLatestRunProgressForFile(com.example.quickbooksimporter.domain.EntityType.INVOICE, trackingFileName)
                .ifPresent(this::applyRunProgress);
    }

    private void applyRunProgress(ImportRunProgressSnapshot snapshot) {
        progressBar.setVisible(true);
        progressDetails.setVisible(true);
        progressBar.setIndeterminate(false);
        progressBar.setValue(snapshot.progressValue());
        progressSummary.setText("Run #" + snapshot.runId() + " is " + snapshot.status() + " | " + snapshot.percentLabel());
        progressDetails.setText(snapshot.processedRows() + "/" + snapshot.runnableRows() + " runnable rows"
                + " | imported=" + snapshot.importedRows()
                + " | skipped=" + snapshot.skippedRows()
                + " | " + snapshot.remainingLabel()
                + " | " + snapshot.throughputLabel()
                + " | " + snapshot.startedLabel());
        if (snapshot.status() != ImportRunStatus.QUEUED && snapshot.status() != ImportRunStatus.RUNNING) {
            trackingFileName = null;
            stopPollingIfIdle();
        }
    }

    private void refreshPreviewJob() {
        if (previewJobId == null) {
            return;
        }
        appJobService.findSnapshot(previewJobId).ifPresent(this::applyPreviewJobSnapshot);
    }

    private void applyPreviewJobSnapshot(AppJobSnapshot snapshot) {
        progressBar.setVisible(true);
        progressDetails.setVisible(true);
        if (snapshot.status() == AppJobStatus.QUEUED || snapshot.status() == AppJobStatus.RUNNING) {
            progressBar.setIndeterminate(snapshot.totalUnits() <= 1);
            if (!progressBar.isIndeterminate()) {
                progressBar.setValue(snapshot.progressValue());
            }
            progressSummary.setText(snapshot.description() + " is " + snapshot.status() + " | " + snapshot.percentLabel());
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
        ImportPreviewJobResult result = appJobService.readResult(snapshot.resultPayload(), ImportPreviewJobResult.class);
        currentPreview = importPreviewJobCodec.readInvoicePreview(result);
        applyPreviewFilter();
        summary.setText("Preview complete: " + result.readyRows() + " ready, " + result.invalidRows()
                + " invalid. Use 'Import Ready Rows Only' to skip invalid rows. Grouping is "
                + (invoiceGroupingEnabled ? "enabled" : "disabled") + ".");
        refreshKpis(result.totalRows(), result.readyRows(), result.invalidRows());
        if (currentPreview.exportCsv() != null) {
            downloadAnchor.setHref(new StreamResource("normalized-" + uploadedFileName,
                    () -> new java.io.ByteArrayInputStream(currentPreview.exportCsv().getBytes(StandardCharsets.UTF_8))));
            downloadAnchor.setVisible(true);
        } else {
            downloadAnchor.setVisible(false);
        }
        progressBar.setIndeterminate(false);
        progressBar.setValue(1d);
        progressSummary.setText("Preview finished");
        progressDetails.setText(snapshot.summaryMessage());
        previewJobId = null;
        stopPollingIfIdle();
    }

    private void stopPollingIfIdle() {
        if (previewJobId == null && (trackingFileName == null || trackingFileName.isBlank())) {
            getUI().ifPresent(ui -> ui.setPollInterval(-1));
        }
    }
}
