package com.example.quickbooksimporter.ui;

import com.example.quickbooksimporter.domain.ExpenseImportPreview;
import com.example.quickbooksimporter.domain.ExpenseImportPreviewRow;
import com.example.quickbooksimporter.domain.ImportRowStatus;
import com.example.quickbooksimporter.domain.ImportRunStatus;
import com.example.quickbooksimporter.domain.NormalizedExpenseField;
import com.example.quickbooksimporter.service.ExpenseImportService;
import com.example.quickbooksimporter.service.ExpenseMappingProfileService;
import com.example.quickbooksimporter.service.DateFormatOption;
import com.example.quickbooksimporter.service.ImportExecutionMode;
import com.example.quickbooksimporter.service.ImportExecutionOptions;
import com.example.quickbooksimporter.service.ImportBackgroundService;
import com.example.quickbooksimporter.service.ImportHistoryService;
import com.example.quickbooksimporter.service.InvoiceCsvParser;
import com.example.quickbooksimporter.service.MappingProfileSummary;
import com.example.quickbooksimporter.service.ParsedCsvDocument;
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
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Route(value = "expenses", layout = MainLayout.class)
@PageTitle("Expense Import")
@PermitAll
public class ExpenseImportView extends VerticalLayout {

    private final InvoiceCsvParser parser;
    private final ExpenseMappingProfileService mappingProfileService;
    private final ExpenseImportService expenseImportService;
    private final ImportBackgroundService backgroundService;
    private final ImportHistoryService importHistoryService;

    private final MemoryBuffer uploadBuffer = new MemoryBuffer();
    private final Upload upload = new Upload(uploadBuffer);
    private final ComboBox<MappingProfileSummary> savedProfiles = new ComboBox<>("Saved Expense Mapping Profiles");
    private final ComboBox<DateFormatOption> txnDateFormat = new ComboBox<>("Transaction Date Format");
    private final ComboBox<ImportRowStatus> previewFilter = new ComboBox<>("Preview Filter");
    private final TextField profileName = new TextField("New Profile Name");
    private final FormLayout mappingForm = new FormLayout();
    private final Grid<ExpenseImportPreviewRow> previewGrid = new Grid<>(ExpenseImportPreviewRow.class, false);
    private final Paragraph summary = new Paragraph("Upload an expense CSV to begin.");

    private final Map<NormalizedExpenseField, ComboBox<String>> fieldSelectors = new EnumMap<>(NormalizedExpenseField.class);

    private byte[] uploadedBytes;
    private String uploadedFileName;
    private String trackingFileName;
    private List<String> currentHeaders = List.of();
    private ExpenseImportPreview currentPreview;

    public ExpenseImportView(InvoiceCsvParser parser,
                             ExpenseMappingProfileService mappingProfileService,
                             ExpenseImportService expenseImportService,
                             ImportBackgroundService backgroundService,
                             ImportHistoryService importHistoryService) {
        this.parser = parser;
        this.mappingProfileService = mappingProfileService;
        this.expenseImportService = expenseImportService;
        this.backgroundService = backgroundService;
        this.importHistoryService = importHistoryService;
        addClassName("corp-page");
        setSizeFull();
        getUI().ifPresent(ui -> ui.addPollListener(event -> refreshBackgroundProgress()));
        add(new H2("Expense Import"),
                new Paragraph("Upload, map, validate, review, and import expense transactions into QuickBooks."),
                UiComponents.importStepper("Upload"));
        configureUpload();
        configureProfiles();
        configureMappingForm();
        configurePreviewGrid();
        configureActions();
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
            Map<NormalizedExpenseField, String> defaults = mappingProfileService.defaultExpenseMapping(currentHeaders);
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
            Map<NormalizedExpenseField, String> mapping = mappingProfileService.loadProfile(event.getValue().id());
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
        mappingForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("800px", 2));
        for (NormalizedExpenseField field : NormalizedExpenseField.values()) {
            ComboBox<String> selector = new ComboBox<>(field.name());
            selector.setWidthFull();
            selector.setPlaceholder(field.sampleHeader());
            fieldSelectors.put(field, selector);
            mappingForm.add(selector);
        }
        add(UiComponents.card(new H3("Stage 3: Column Mapping"), mappingForm));
    }

    private void configurePreviewGrid() {
        previewGrid.addColumn(ExpenseImportPreviewRow::rowNumber).setHeader("Row");
        previewGrid.addColumn(ExpenseImportPreviewRow::vendor).setHeader("Vendor");
        previewGrid.addColumn(ExpenseImportPreviewRow::category).setHeader("Category");
        previewGrid.addColumn(ExpenseImportPreviewRow::referenceNo).setHeader("Reference #");
        previewGrid.addColumn(ExpenseImportPreviewRow::status).setHeader("Status");
        previewGrid.addColumn(ExpenseImportPreviewRow::message).setHeader("Message").setAutoWidth(true).setFlexGrow(1);
        previewGrid.setHeight("360px");
        previewGrid.addClassName("corp-grid");
        previewFilter.setItems(ImportRowStatus.values());
        previewFilter.addValueChangeListener(event -> applyPreviewFilter());
        add(UiComponents.card(new H3("Stage 4: Validation Preview"), summary, previewFilter, previewGrid));
    }

    private void configureActions() {
        Button previewButton = new Button("Preview & Validate", event -> previewImport());
        Button saveProfileButton = new Button("Save Mapping Profile", event -> saveProfile());
        Button importButton = new Button("Import Expenses", event -> importPreview(ImportExecutionMode.STRICT_ALL_ROWS));
        Button importReadyOnlyButton = new Button("Import Ready Rows Only", event -> importPreview(ImportExecutionMode.IMPORT_READY_ONLY));
        Button historyButton = new Button("Open History", event -> UI.getCurrent().navigate(ImportHistoryView.class));
        previewButton.addThemeName("primary");
        importButton.addThemeName("primary");
        HorizontalLayout actions = new HorizontalLayout(previewButton, saveProfileButton, importButton, importReadyOnlyButton, historyButton);
        actions.addClassName("corp-action-bar");
        add(UiComponents.card(UiComponents.sectionTitle("Stage 5: Execute"), actions));
    }

    private void previewImport() {
        if (uploadedBytes == null) {
            notifyWarning("Upload a CSV file first.");
            return;
        }
        currentPreview = expenseImportService.preview(
                uploadedFileName,
                uploadedBytes,
                currentMapping(),
                txnDateFormat.getValue() == null ? DateFormatOption.AUTO : txnDateFormat.getValue());
        applyPreviewFilter();
        long readyCount = currentPreview.rows().stream().filter(row -> row.status() == ImportRowStatus.READY).count();
        long invalidCount = currentPreview.rows().stream().filter(row -> row.status() == ImportRowStatus.INVALID).count();
        summary.setText("Preview complete: " + readyCount + " ready, " + invalidCount + " invalid. Use 'Import Ready Rows Only' to skip invalid rows.");
    }

    private void saveProfile() {
        if (profileName.isEmpty()) {
            notifyWarning("Enter a profile name first.");
            return;
        }
        mappingProfileService.saveProfile(profileName.getValue(), currentMapping());
        savedProfiles.setItems(mappingProfileService.listProfiles());
        notifySuccess("Expense mapping profile saved.");
    }

    private void importPreview(ImportExecutionMode mode) {
        if (currentPreview == null) {
            notifyWarning("Run preview first.");
            return;
        }
        backgroundService.enqueueForCurrentCompany(
                com.example.quickbooksimporter.domain.EntityType.EXPENSE,
                uploadedFileName,
                savedProfiles.getOptionalValue().map(MappingProfileSummary::name).orElse(profileName.getValue()),
                currentPreview,
                new ImportExecutionOptions(null, null, null, mode));
        String message = "Background import started. Open Import History to track live progress.";
        notifySuccess(message);
        summary.setText(message);
        trackingFileName = uploadedFileName;
        getUI().ifPresent(ui -> ui.setPollInterval(3000));
        refreshBackgroundProgress();
    }

    private Map<NormalizedExpenseField, String> currentMapping() {
        Map<NormalizedExpenseField, String> mapping = new EnumMap<>(NormalizedExpenseField.class);
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
        if (trackingFileName == null || trackingFileName.isBlank()) {
            return;
        }
        importHistoryService.findLatestRunForFile(com.example.quickbooksimporter.domain.EntityType.EXPENSE, trackingFileName)
                .ifPresent(run -> {
                    summary.setText("Run #" + run.getId() + " is " + run.getStatus()
                            + " | attempted=" + run.getAttemptedRows()
                            + ", imported=" + run.getImportedRows()
                            + ", skipped=" + run.getSkippedRows() + ".");
                    if (run.getStatus() != ImportRunStatus.QUEUED && run.getStatus() != ImportRunStatus.RUNNING) {
                        getUI().ifPresent(ui -> ui.setPollInterval(-1));
                    }
                });
    }
}
