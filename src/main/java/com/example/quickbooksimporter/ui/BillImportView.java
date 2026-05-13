package com.example.quickbooksimporter.ui;

import com.example.quickbooksimporter.domain.BillImportPreview;
import com.example.quickbooksimporter.domain.BillImportPreviewRow;
import com.example.quickbooksimporter.domain.EntityType;
import com.example.quickbooksimporter.domain.ImportRowStatus;
import com.example.quickbooksimporter.domain.NormalizedBillField;
import com.example.quickbooksimporter.service.BillImportService;
import com.example.quickbooksimporter.service.BillMappingProfileService;
import com.example.quickbooksimporter.service.ImportBackgroundService;
import com.example.quickbooksimporter.service.ImportExecutionMode;
import com.example.quickbooksimporter.service.ImportExecutionOptions;
import com.example.quickbooksimporter.service.DateFormatOption;
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

@Route(value = "bills", layout = MainLayout.class)
@PageTitle("Bill Import")
@PermitAll
public class BillImportView extends VerticalLayout {
    private final InvoiceCsvParser parser;
    private final BillMappingProfileService mappingProfileService;
    private final BillImportService importService;
    private final ImportBackgroundService backgroundService;
    private final MemoryBuffer uploadBuffer = new MemoryBuffer();
    private final Upload upload = new Upload(uploadBuffer);
    private final ComboBox<MappingProfileSummary> savedProfiles = new ComboBox<>("Saved Bill Mapping Profiles");
    private final ComboBox<DateFormatOption> txnDateFormat = new ComboBox<>("Bill Date Format");
    private final ComboBox<ImportRowStatus> previewFilter = new ComboBox<>("Preview Filter");
    private final TextField profileName = new TextField("New Profile Name");
    private final FormLayout mappingForm = new FormLayout();
    private final Grid<BillImportPreviewRow> previewGrid = new Grid<>(BillImportPreviewRow.class, false);
    private final Paragraph summary = new Paragraph("Upload a bill CSV to begin.");
    private final Map<NormalizedBillField, ComboBox<String>> fieldSelectors = new EnumMap<>(NormalizedBillField.class);
    private byte[] uploadedBytes;
    private String uploadedFileName;
    private List<String> currentHeaders = List.of();
    private BillImportPreview currentPreview;

    public BillImportView(InvoiceCsvParser parser,
                          BillMappingProfileService mappingProfileService,
                          BillImportService importService,
                          ImportBackgroundService backgroundService) {
        this.parser = parser;
        this.mappingProfileService = mappingProfileService;
        this.importService = importService;
        this.backgroundService = backgroundService;
        addClassName("corp-page");
        setSizeFull();
        add(new H2("Bill Import"), new Paragraph("Upload, map, validate, review, and import AP bills into QuickBooks."),
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
            Map<NormalizedBillField, String> defaults = mappingProfileService.defaultMapping(currentHeaders);
            fieldSelectors.forEach((f, s) -> { s.setItems(currentHeaders); s.setValue(defaults.get(f)); });
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
            if (event.getValue() == null || currentHeaders.isEmpty()) return;
            Map<NormalizedBillField, String> mapping = mappingProfileService.loadProfile(event.getValue().id());
            fieldSelectors.forEach((f, s) -> { s.setItems(currentHeaders); s.setValue(mapping.get(f)); });
        });
        HorizontalLayout row = new HorizontalLayout(savedProfiles, profileName, txnDateFormat);
        row.setWidthFull(); row.expand(savedProfiles, profileName);
        add(UiComponents.card(UiComponents.sectionTitle("Stage 2: Mapping Profile"), row));
    }
    private void configureMappingForm() {
        mappingForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0",1), new FormLayout.ResponsiveStep("800px",2));
        for (NormalizedBillField field : NormalizedBillField.values()) {
            ComboBox<String> selector = new ComboBox<>(field.name()); selector.setWidthFull(); selector.setPlaceholder(field.sampleHeader());
            fieldSelectors.put(field, selector); mappingForm.add(selector);
        }
        add(UiComponents.card(new H3("Stage 3: Column Mapping"), mappingForm));
    }
    private void configurePreviewGrid() {
        previewGrid.addColumn(BillImportPreviewRow::rowNumber).setHeader("Row");
        previewGrid.addColumn(BillImportPreviewRow::billNo).setHeader("Bill #");
        previewGrid.addColumn(BillImportPreviewRow::vendor).setHeader("Vendor");
        previewGrid.addColumn(BillImportPreviewRow::lineCount).setHeader("Lines");
        previewGrid.addColumn(BillImportPreviewRow::status).setHeader("Status");
        previewGrid.addColumn(BillImportPreviewRow::message).setHeader("Message").setAutoWidth(true).setFlexGrow(1);
        previewGrid.setHeight("360px"); previewGrid.addClassName("corp-grid");
        previewFilter.setItems(ImportRowStatus.values());
        previewFilter.addValueChangeListener(event -> applyPreviewFilter());
        add(UiComponents.card(new H3("Stage 4: Validation Preview"), summary, previewFilter, previewGrid));
    }
    private void configureActions() {
        Button preview = new Button("Preview & Validate", e -> previewImport());
        Button save = new Button("Save Mapping Profile", e -> saveProfile());
        Button run = new Button("Import Bills", e -> importPreview(ImportExecutionMode.STRICT_ALL_ROWS));
        Button runReadyOnly = new Button("Import Ready Rows Only", e -> importPreview(ImportExecutionMode.IMPORT_READY_ONLY));
        Button historyButton = new Button("Open History", e -> UI.getCurrent().navigate("history"));
        preview.addThemeName("primary"); run.addThemeName("primary");
        HorizontalLayout actions = new HorizontalLayout(preview, save, run, runReadyOnly, historyButton); actions.addClassName("corp-action-bar");
        add(UiComponents.card(UiComponents.sectionTitle("Stage 5: Execute"), actions));
    }
    private void previewImport() {
        if (uploadedBytes == null) { notifyWarning("Upload a CSV file first."); return; }
        currentPreview = importService.preview(
                uploadedFileName,
                uploadedBytes,
                currentMapping(),
                txnDateFormat.getValue() == null ? DateFormatOption.AUTO : txnDateFormat.getValue());
        applyPreviewFilter();
        long ready = currentPreview.rows().stream().filter(r -> r.status() == ImportRowStatus.READY).count();
        long invalid = currentPreview.rows().stream().filter(r -> r.status() == ImportRowStatus.INVALID).count();
        summary.setText("Preview complete: " + ready + " ready, " + invalid + " invalid. Use 'Import Ready Rows Only' to skip invalid rows.");
    }
    private void saveProfile() {
        if (profileName.isEmpty()) { notifyWarning("Enter a profile name first."); return; }
        mappingProfileService.saveProfile(profileName.getValue(), currentMapping());
        savedProfiles.setItems(mappingProfileService.listProfiles());
        notifySuccess("Bill mapping profile saved.");
    }
    private void importPreview(ImportExecutionMode mode) {
        if (currentPreview == null) { notifyWarning("Run preview first."); return; }
        backgroundService.enqueue(
                EntityType.BILL,
                uploadedFileName,
                savedProfiles.getOptionalValue().map(MappingProfileSummary::name).orElse(profileName.getValue()),
                currentPreview,
                new ImportExecutionOptions(null, null, null, mode));
        String message = "Background import started. Open Import History to track live progress.";
        notifySuccess(message);
        summary.setText(message);
    }
    private Map<NormalizedBillField, String> currentMapping() {
        Map<NormalizedBillField, String> m = new EnumMap<>(NormalizedBillField.class);
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
