package com.example.quickbooksimporter.ui;

import com.example.quickbooksimporter.domain.BillPaymentImportPreview;
import com.example.quickbooksimporter.domain.BillPaymentImportPreviewRow;
import com.example.quickbooksimporter.domain.ImportRowStatus;
import com.example.quickbooksimporter.domain.NormalizedBillPaymentField;
import com.example.quickbooksimporter.service.BillPaymentImportService;
import com.example.quickbooksimporter.service.BillPaymentMappingProfileService;
import com.example.quickbooksimporter.service.ImportExecutionResult;
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

@Route(value = "bill-payments", layout = MainLayout.class)
@PageTitle("Bill Payment Import")
@PermitAll
public class BillPaymentImportView extends VerticalLayout {
    private final InvoiceCsvParser parser;
    private final BillPaymentMappingProfileService mappingProfileService;
    private final BillPaymentImportService importService;
    private final MemoryBuffer uploadBuffer = new MemoryBuffer();
    private final Upload upload = new Upload(uploadBuffer);
    private final ComboBox<MappingProfileSummary> savedProfiles = new ComboBox<>("Saved Bill Payment Mapping Profiles");
    private final ComboBox<ImportRowStatus> previewFilter = new ComboBox<>("Preview Filter");
    private final TextField profileName = new TextField("New Profile Name");
    private final FormLayout mappingForm = new FormLayout();
    private final Grid<BillPaymentImportPreviewRow> previewGrid = new Grid<>(BillPaymentImportPreviewRow.class, false);
    private final Paragraph summary = new Paragraph("Upload a bill payment CSV to begin.");
    private final Map<NormalizedBillPaymentField, ComboBox<String>> fieldSelectors = new EnumMap<>(NormalizedBillPaymentField.class);
    private byte[] uploadedBytes;
    private String uploadedFileName;
    private List<String> currentHeaders = List.of();
    private BillPaymentImportPreview currentPreview;

    public BillPaymentImportView(InvoiceCsvParser parser, BillPaymentMappingProfileService mappingProfileService, BillPaymentImportService importService) {
        this.parser = parser;
        this.mappingProfileService = mappingProfileService;
        this.importService = importService;
        addClassName("corp-page");
        setSizeFull();
        add(new H2("Bill Payment Import"), new Paragraph("Upload, map, validate, review, and import bill payments into QuickBooks."),
                UiComponents.importStepper("Upload"));
        configureUpload(); configureProfiles(); configureMappingForm(); configurePreviewGrid(); configureActions();
    }

    private void configureUpload() {
        upload.setAcceptedFileTypes(".csv");
        upload.addSucceededListener(event -> {
            uploadedFileName = event.getFileName();
            try { uploadedBytes = uploadBuffer.getInputStream().readAllBytes(); } catch (IOException e) { throw new IllegalStateException(e); }
            ParsedCsvDocument document = parser.parse(new java.io.ByteArrayInputStream(uploadedBytes));
            currentHeaders = document.headers();
            Map<NormalizedBillPaymentField, String> defaults = mappingProfileService.defaultMapping(currentHeaders);
            fieldSelectors.forEach((f, s) -> { s.setItems(currentHeaders); s.setValue(defaults.get(f)); });
            summary.setText("Loaded " + uploadedFileName + " with " + document.rows().size() + " data rows.");
        });
        add(UiComponents.card(UiComponents.sectionTitle("Stage 1: Upload CSV"), upload));
    }
    private void configureProfiles() {
        savedProfiles.setItems(mappingProfileService.listProfiles());
        savedProfiles.setItemLabelGenerator(MappingProfileSummary::name);
        savedProfiles.addValueChangeListener(event -> {
            if (event.getValue() == null || currentHeaders.isEmpty()) return;
            Map<NormalizedBillPaymentField, String> mapping = mappingProfileService.loadProfile(event.getValue().id());
            fieldSelectors.forEach((f, s) -> { s.setItems(currentHeaders); s.setValue(mapping.get(f)); });
        });
        HorizontalLayout row = new HorizontalLayout(savedProfiles, profileName);
        row.setWidthFull(); row.expand(savedProfiles, profileName);
        add(UiComponents.card(UiComponents.sectionTitle("Stage 2: Mapping Profile"), row));
    }
    private void configureMappingForm() {
        mappingForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0",1), new FormLayout.ResponsiveStep("800px",2));
        for (NormalizedBillPaymentField field : NormalizedBillPaymentField.values()) {
            ComboBox<String> selector = new ComboBox<>(field.name()); selector.setWidthFull(); selector.setPlaceholder(field.sampleHeader());
            fieldSelectors.put(field, selector); mappingForm.add(selector);
        }
        add(UiComponents.card(new H3("Stage 3: Column Mapping"), mappingForm));
    }
    private void configurePreviewGrid() {
        previewGrid.addColumn(BillPaymentImportPreviewRow::rowNumber).setHeader("Row");
        previewGrid.addColumn(BillPaymentImportPreviewRow::vendor).setHeader("Vendor");
        previewGrid.addColumn(BillPaymentImportPreviewRow::billNo).setHeader("Bill #");
        previewGrid.addColumn(BillPaymentImportPreviewRow::referenceNo).setHeader("Reference #");
        previewGrid.addColumn(BillPaymentImportPreviewRow::status).setHeader("Status");
        previewGrid.addColumn(BillPaymentImportPreviewRow::message).setHeader("Message").setAutoWidth(true).setFlexGrow(1);
        previewGrid.setHeight("360px"); previewGrid.addClassName("corp-grid");
        previewFilter.setItems(ImportRowStatus.values());
        previewFilter.addValueChangeListener(event -> applyPreviewFilter());
        add(UiComponents.card(new H3("Stage 4: Validation Preview"), summary, previewFilter, previewGrid));
    }
    private void configureActions() {
        Button preview = new Button("Preview & Validate", e -> previewImport());
        Button save = new Button("Save Mapping Profile", e -> saveProfile());
        Button run = new Button("Import Bill Payments", e -> importPreview());
        Button historyButton = new Button("Open History", e -> UI.getCurrent().navigate("history"));
        preview.addThemeName("primary"); run.addThemeName("primary");
        HorizontalLayout actions = new HorizontalLayout(preview, save, run, historyButton); actions.addClassName("corp-action-bar");
        add(UiComponents.card(UiComponents.sectionTitle("Stage 5: Execute"), actions));
    }
    private void previewImport() {
        if (uploadedBytes == null) { notifyWarning("Upload a CSV file first."); return; }
        currentPreview = importService.preview(uploadedFileName, uploadedBytes, currentMapping());
        applyPreviewFilter();
        long ready = currentPreview.rows().stream().filter(r -> r.status() == ImportRowStatus.READY).count();
        long invalid = currentPreview.rows().stream().filter(r -> r.status() == ImportRowStatus.INVALID).count();
        summary.setText("Preview complete: " + ready + " ready, " + invalid + " invalid.");
    }
    private void saveProfile() {
        if (profileName.isEmpty()) { notifyWarning("Enter a profile name first."); return; }
        mappingProfileService.saveProfile(profileName.getValue(), currentMapping());
        savedProfiles.setItems(mappingProfileService.listProfiles());
        notifySuccess("Bill payment mapping profile saved.");
    }
    private void importPreview() {
        if (currentPreview == null) { notifyWarning("Run preview first."); return; }
        ImportExecutionResult result = importService.execute(uploadedFileName,
                savedProfiles.getOptionalValue().map(MappingProfileSummary::name).orElse(profileName.getValue()),
                currentPreview);
        if (result.success()) notifySuccess(result.message()); else notifyWarning(result.message());
        summary.setText(result.message());
    }
    private Map<NormalizedBillPaymentField, String> currentMapping() {
        Map<NormalizedBillPaymentField, String> m = new EnumMap<>(NormalizedBillPaymentField.class);
        fieldSelectors.forEach((f,s) -> m.put(f, s.getValue()));
        return m;
    }
    private void notifySuccess(String msg){ Notification n=Notification.show(Objects.requireNonNull(msg)); n.addThemeVariants(NotificationVariant.LUMO_SUCCESS); }
    private void notifyWarning(String msg){ Notification n=Notification.show(Objects.requireNonNull(msg)); n.addThemeVariants(NotificationVariant.LUMO_WARNING); }
    private void applyPreviewFilter() {
        if (currentPreview == null) { previewGrid.setItems(List.of()); return; }
        ImportRowStatus filter = previewFilter.getValue();
        previewGrid.setItems(currentPreview.rows().stream().filter(r -> filter == null || r.status() == filter).toList());
    }
}
