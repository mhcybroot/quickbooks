package com.example.quickbooksimporter.ui;

import com.example.quickbooksimporter.domain.ImportRowStatus;
import com.example.quickbooksimporter.domain.NormalizedPaymentField;
import com.example.quickbooksimporter.domain.PaymentImportPreview;
import com.example.quickbooksimporter.domain.PaymentImportPreviewRow;
import com.example.quickbooksimporter.service.ImportExecutionResult;
import com.example.quickbooksimporter.service.InvoiceCsvParser;
import com.example.quickbooksimporter.service.MappingProfileSummary;
import com.example.quickbooksimporter.service.ParsedCsvDocument;
import com.example.quickbooksimporter.service.DateFormatOption;
import com.example.quickbooksimporter.service.PaymentImportService;
import com.example.quickbooksimporter.service.PaymentMappingProfileService;
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

@Route(value = "payments", layout = MainLayout.class)
@PageTitle("Receive Payment Import")
@PermitAll
public class PaymentImportView extends VerticalLayout {

    private final InvoiceCsvParser parser;
    private final PaymentMappingProfileService mappingProfileService;
    private final PaymentImportService paymentImportService;

    private final MemoryBuffer uploadBuffer = new MemoryBuffer();
    private final Upload upload = new Upload(uploadBuffer);
    private final ComboBox<MappingProfileSummary> savedProfiles = new ComboBox<>("Saved Payment Mapping Profiles");
    private final ComboBox<DateFormatOption> paymentDateFormat = new ComboBox<>("Payment Date Format");
    private final ComboBox<ImportRowStatus> previewFilter = new ComboBox<>("Preview Filter");
    private final TextField profileName = new TextField("New Profile Name");
    private final FormLayout mappingForm = new FormLayout();
    private final Grid<PaymentImportPreviewRow> previewGrid = new Grid<>(PaymentImportPreviewRow.class, false);
    private final Paragraph summary = new Paragraph("Upload a payment CSV to begin.");

    private final Map<NormalizedPaymentField, ComboBox<String>> fieldSelectors = new EnumMap<>(NormalizedPaymentField.class);

    private byte[] uploadedBytes;
    private String uploadedFileName;
    private List<String> currentHeaders = List.of();
    private PaymentImportPreview currentPreview;

    public PaymentImportView(InvoiceCsvParser parser,
                             PaymentMappingProfileService mappingProfileService,
                             PaymentImportService paymentImportService) {
        this.parser = parser;
        this.mappingProfileService = mappingProfileService;
        this.paymentImportService = paymentImportService;
        addClassName("corp-page");
        setSizeFull();
        add(new H2("Receive Payment Import"),
                new Paragraph("Upload, map, validate, review, and import payment applications to open invoices."),
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
            Map<NormalizedPaymentField, String> defaults = mappingProfileService.defaultPaymentMapping(currentHeaders);
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
        paymentDateFormat.setItems(DateFormatOption.values());
        paymentDateFormat.setItemLabelGenerator(DateFormatOption::label);
        paymentDateFormat.setValue(DateFormatOption.AUTO);
        savedProfiles.addValueChangeListener(event -> {
            if (event.getValue() == null || currentHeaders.isEmpty()) {
                return;
            }
            Map<NormalizedPaymentField, String> mapping = mappingProfileService.loadProfile(event.getValue().id());
            paymentDateFormat.setValue(mappingProfileService.loadPaymentDateFormat(event.getValue().id()));
            fieldSelectors.forEach((field, selector) -> {
                selector.setItems(currentHeaders);
                selector.setValue(mapping.get(field));
            });
        });
        HorizontalLayout profileRow = new HorizontalLayout(savedProfiles, profileName, paymentDateFormat);
        profileRow.setWidthFull();
        profileRow.expand(savedProfiles, profileName);
        add(UiComponents.card(UiComponents.sectionTitle("Stage 2: Mapping Profile"), profileRow));
    }

    private void configureMappingForm() {
        mappingForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("800px", 2));
        for (NormalizedPaymentField field : NormalizedPaymentField.values()) {
            ComboBox<String> selector = new ComboBox<>(field.name());
            selector.setWidthFull();
            selector.setPlaceholder(field.sampleHeader());
            fieldSelectors.put(field, selector);
            mappingForm.add(selector);
        }
        add(UiComponents.card(new H3("Stage 3: Column Mapping"), mappingForm));
    }

    private void configurePreviewGrid() {
        previewGrid.addColumn(PaymentImportPreviewRow::rowNumber).setHeader("Row");
        previewGrid.addColumn(PaymentImportPreviewRow::customer).setHeader("Customer");
        previewGrid.addColumn(PaymentImportPreviewRow::invoiceNo).setHeader("Invoice #");
        previewGrid.addColumn(PaymentImportPreviewRow::referenceNo).setHeader("Reference #");
        previewGrid.addColumn(PaymentImportPreviewRow::status).setHeader("Status");
        previewGrid.addColumn(PaymentImportPreviewRow::message).setHeader("Message").setAutoWidth(true).setFlexGrow(1);
        previewGrid.setHeight("360px");
        previewGrid.addClassName("corp-grid");
        previewFilter.setItems(ImportRowStatus.values());
        previewFilter.addValueChangeListener(event -> applyPreviewFilter());
        add(UiComponents.card(new H3("Stage 4: Validation Preview"), summary, previewFilter, previewGrid));
    }

    private void configureActions() {
        Button previewButton = new Button("Preview & Validate", event -> previewImport());
        Button saveProfileButton = new Button("Save Mapping Profile", event -> saveProfile());
        Button importButton = new Button("Import Payments", event -> importPreview());
        Button historyButton = new Button("Open History", event -> UI.getCurrent().navigate("history"));
        previewButton.addThemeName("primary");
        importButton.addThemeName("primary");
        HorizontalLayout actions = new HorizontalLayout(previewButton, saveProfileButton, importButton, historyButton);
        actions.addClassName("corp-action-bar");
        add(UiComponents.card(UiComponents.sectionTitle("Stage 5: Execute"), actions));
    }

    private void previewImport() {
        if (uploadedBytes == null) {
            notifyWarning("Upload a CSV file first.");
            return;
        }
        currentPreview = paymentImportService.preview(
                uploadedFileName,
                uploadedBytes,
                currentMapping(),
                java.util.Map.of(),
                paymentDateFormat.getValue() == null ? DateFormatOption.AUTO : paymentDateFormat.getValue());
        applyPreviewFilter();
        long readyCount = currentPreview.rows().stream().filter(row -> row.status() == ImportRowStatus.READY).count();
        long invalidCount = currentPreview.rows().stream().filter(row -> row.status() == ImportRowStatus.INVALID).count();
        summary.setText("Preview complete: " + readyCount + " ready, " + invalidCount + " invalid.");
    }

    private void saveProfile() {
        if (profileName.isEmpty()) {
            notifyWarning("Enter a profile name first.");
            return;
        }
        mappingProfileService.saveProfile(
                profileName.getValue(),
                currentMapping(),
                paymentDateFormat.getValue() == null ? DateFormatOption.AUTO : paymentDateFormat.getValue());
        savedProfiles.setItems(mappingProfileService.listProfiles());
        notifySuccess("Payment mapping profile saved.");
    }

    private void importPreview() {
        if (currentPreview == null) {
            notifyWarning("Run preview first.");
            return;
        }
        ImportExecutionResult result = paymentImportService.execute(
                uploadedFileName,
                savedProfiles.getOptionalValue().map(MappingProfileSummary::name).orElse(profileName.getValue()),
                currentPreview);
        if (result.success()) {
            notifySuccess(result.message());
        } else {
            notifyWarning(result.message());
        }
        summary.setText(result.message());
    }

    private Map<NormalizedPaymentField, String> currentMapping() {
        Map<NormalizedPaymentField, String> mapping = new EnumMap<>(NormalizedPaymentField.class);
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
