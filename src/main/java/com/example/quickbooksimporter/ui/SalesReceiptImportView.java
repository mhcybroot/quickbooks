package com.example.quickbooksimporter.ui;

import com.example.quickbooksimporter.domain.ImportRowStatus;
import com.example.quickbooksimporter.domain.NormalizedSalesReceiptField;
import com.example.quickbooksimporter.domain.SalesReceiptImportPreview;
import com.example.quickbooksimporter.domain.SalesReceiptImportPreviewRow;
import com.example.quickbooksimporter.service.ImportExecutionResult;
import com.example.quickbooksimporter.service.ImportExecutionMode;
import com.example.quickbooksimporter.service.ImportExecutionOptions;
import com.example.quickbooksimporter.service.InvoiceCsvParser;
import com.example.quickbooksimporter.service.MappingProfileSummary;
import com.example.quickbooksimporter.service.ParsedCsvDocument;
import com.example.quickbooksimporter.service.DateFormatOption;
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

@Route(value = "sales-receipts", layout = MainLayout.class)
@PageTitle("Sales Receipt Import")
@PermitAll
public class SalesReceiptImportView extends VerticalLayout {

    private final InvoiceCsvParser parser;
    private final SalesReceiptMappingProfileService mappingProfileService;
    private final SalesReceiptImportService importService;

    private final MemoryBuffer uploadBuffer = new MemoryBuffer();
    private final Upload upload = new Upload(uploadBuffer);
    private final ComboBox<MappingProfileSummary> savedProfiles = new ComboBox<>("Saved Receipt Mapping Profiles");
    private final ComboBox<DateFormatOption> txnDateFormat = new ComboBox<>("Transaction Date Format");
    private final ComboBox<ImportRowStatus> previewFilter = new ComboBox<>("Preview Filter");
    private final TextField profileName = new TextField("New Profile Name");
    private final FormLayout mappingForm = new FormLayout();
    private final Grid<SalesReceiptImportPreviewRow> previewGrid = new Grid<>(SalesReceiptImportPreviewRow.class, false);
    private final Paragraph summary = new Paragraph("Upload a sales receipt CSV to begin.");

    private final Map<NormalizedSalesReceiptField, ComboBox<String>> fieldSelectors = new EnumMap<>(NormalizedSalesReceiptField.class);

    private byte[] uploadedBytes;
    private String uploadedFileName;
    private List<String> currentHeaders = List.of();
    private SalesReceiptImportPreview currentPreview;

    public SalesReceiptImportView(InvoiceCsvParser parser,
                                  SalesReceiptMappingProfileService mappingProfileService,
                                  SalesReceiptImportService importService) {
        this.parser = parser;
        this.mappingProfileService = mappingProfileService;
        this.importService = importService;

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
        mappingForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("800px", 2));
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
        previewGrid.addColumn(SalesReceiptImportPreviewRow::message).setHeader("Message").setAutoWidth(true).setFlexGrow(1);
        previewGrid.setHeight("360px");
        previewGrid.addClassName("corp-grid");
        previewFilter.setItems(ImportRowStatus.values());
        previewFilter.addValueChangeListener(event -> applyPreviewFilter());
        add(UiComponents.card(new H3("Stage 4: Validation Preview"), summary, previewFilter, previewGrid));
    }

    private void configureActions() {
        Button previewButton = new Button("Preview & Validate", event -> previewImport());
        Button saveProfileButton = new Button("Save Mapping Profile", event -> saveProfile());
        Button importButton = new Button("Import Sales Receipts", event -> importPreview(ImportExecutionMode.STRICT_ALL_ROWS));
        Button importReadyOnlyButton = new Button("Import Ready Rows Only", event -> importPreview(ImportExecutionMode.IMPORT_READY_ONLY));
        Button historyButton = new Button("Open History", event -> UI.getCurrent().navigate("history"));
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
        currentPreview = importService.preview(
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
        notifySuccess("Sales receipt mapping profile saved.");
    }

    private void importPreview(ImportExecutionMode mode) {
        if (currentPreview == null) {
            notifyWarning("Run preview first.");
            return;
        }
        ImportExecutionResult result = importService.execute(
                uploadedFileName,
                savedProfiles.getOptionalValue().map(MappingProfileSummary::name).orElse(profileName.getValue()),
                currentPreview,
                new ImportExecutionOptions(null, null, null, mode));
        if (result.success()) {
            notifySuccess(result.message());
        } else {
            notifyWarning(result.message());
        }
        summary.setText(result.message());
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
}
