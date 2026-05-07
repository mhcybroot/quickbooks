package com.example.quickbooksimporter.ui;

import com.example.quickbooksimporter.domain.ImportPreview;
import com.example.quickbooksimporter.domain.ImportPreviewRow;
import com.example.quickbooksimporter.domain.ImportRowStatus;
import com.example.quickbooksimporter.domain.NormalizedInvoiceField;
import com.example.quickbooksimporter.service.CsvMappingProfileService;
import com.example.quickbooksimporter.service.ImportExecutionResult;
import com.example.quickbooksimporter.service.InvoiceCsvParser;
import com.example.quickbooksimporter.service.InvoiceImportService;
import com.example.quickbooksimporter.service.MappingProfileSummary;
import com.example.quickbooksimporter.service.ParsedCsvDocument;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import jakarta.annotation.security.PermitAll;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Route(value = "", layout = MainLayout.class)
@PageTitle("Invoice Import")
@PermitAll
public class InvoiceImportView extends VerticalLayout {

    private final InvoiceCsvParser parser;
    private final CsvMappingProfileService mappingProfileService;
    private final InvoiceImportService invoiceImportService;

    private final MemoryBuffer uploadBuffer = new MemoryBuffer();
    private final Upload upload = new Upload(uploadBuffer);
    private final ComboBox<MappingProfileSummary> savedProfiles = new ComboBox<>("Saved Mapping Profiles");
    private final TextField profileName = new TextField("New Profile Name");
    private final FormLayout mappingForm = new FormLayout();
    private final Grid<ImportPreviewRow> previewGrid = new Grid<>(ImportPreviewRow.class, false);
    private final Paragraph summary = new Paragraph("Upload a CSV to begin.");
    private final Anchor downloadAnchor = new Anchor();

    private final Map<NormalizedInvoiceField, ComboBox<String>> fieldSelectors = new EnumMap<>(NormalizedInvoiceField.class);

    private byte[] uploadedBytes;
    private String uploadedFileName;
    private List<String> currentHeaders = List.of();
    private ImportPreview currentPreview;

    public InvoiceImportView(InvoiceCsvParser parser,
                             CsvMappingProfileService mappingProfileService,
                             InvoiceImportService invoiceImportService) {
        this.parser = parser;
        this.mappingProfileService = mappingProfileService;
        this.invoiceImportService = invoiceImportService;

        setSizeFull();
        add(new H2("Invoice CSV Import"), new Text("Upload, map, validate, export, then import into QuickBooks."));
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
            Map<NormalizedInvoiceField, String> defaults = mappingProfileService.defaultInvoiceMapping(currentHeaders);
            fieldSelectors.forEach((field, selector) -> {
                selector.setItems(currentHeaders);
                selector.setValue(defaults.get(field));
            });
            summary.setText("Loaded " + uploadedFileName + " with " + document.rows().size() + " data rows.");
        });
        add(upload);
    }

    private void configureProfiles() {
        savedProfiles.setItems(mappingProfileService.listProfiles());
        savedProfiles.setItemLabelGenerator(MappingProfileSummary::name);
        savedProfiles.addValueChangeListener(event -> {
            if (event.getValue() == null || currentHeaders.isEmpty()) {
                return;
            }
            Map<NormalizedInvoiceField, String> mapping = mappingProfileService.loadProfile(event.getValue().id());
            fieldSelectors.forEach((field, selector) -> {
                selector.setItems(currentHeaders);
                selector.setValue(mapping.get(field));
            });
        });
        add(new HorizontalLayout(savedProfiles, profileName));
    }

    private void configureMappingForm() {
        mappingForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("800px", 2));
        for (NormalizedInvoiceField field : NormalizedInvoiceField.values()) {
            ComboBox<String> selector = new ComboBox<>(field.name());
            selector.setWidthFull();
            selector.setPlaceholder(field.sampleHeader());
            fieldSelectors.put(field, selector);
            mappingForm.add(selector);
        }
        add(new H3("Column Mapping"), mappingForm);
    }

    private void configurePreviewGrid() {
        previewGrid.addColumn(ImportPreviewRow::rowNumber).setHeader("Row");
        previewGrid.addColumn(ImportPreviewRow::invoiceNo).setHeader("Invoice #");
        previewGrid.addColumn(ImportPreviewRow::customer).setHeader("Customer");
        previewGrid.addColumn(ImportPreviewRow::itemName).setHeader("Item");
        previewGrid.addColumn(ImportPreviewRow::status).setHeader("Status");
        previewGrid.addColumn(ImportPreviewRow::message).setHeader("Message").setAutoWidth(true).setFlexGrow(1);
        previewGrid.setHeight("360px");
        add(summary, previewGrid);
    }

    private void configureActions() {
        Button previewButton = new Button("Preview & Validate", event -> previewImport());
        Button saveProfileButton = new Button("Save Mapping Profile", event -> saveProfile());
        Button importButton = new Button("Import to QuickBooks", event -> importPreview());
        downloadAnchor.setText("Download normalized CSV");
        downloadAnchor.setVisible(false);

        add(new HorizontalLayout(previewButton, saveProfileButton, importButton), downloadAnchor);
    }

    private void previewImport() {
        if (uploadedBytes == null) {
            Notification.show("Upload a CSV file first.");
            return;
        }
        currentPreview = invoiceImportService.preview(uploadedFileName, uploadedBytes, currentMapping());
        previewGrid.setItems(currentPreview.rows());
        long readyCount = currentPreview.rows().stream().filter(row -> row.status() == ImportRowStatus.READY).count();
        long invalidCount = currentPreview.rows().stream().filter(row -> row.status() == ImportRowStatus.INVALID).count();
        summary.setText("Preview complete: " + readyCount + " ready, " + invalidCount + " invalid.");
        downloadAnchor.setHref(new StreamResource("normalized-" + uploadedFileName,
                () -> new java.io.ByteArrayInputStream(currentPreview.exportCsv().getBytes(StandardCharsets.UTF_8))));
        downloadAnchor.setVisible(true);
    }

    private void saveProfile() {
        if (profileName.isEmpty()) {
            Notification.show("Enter a profile name first.");
            return;
        }
        mappingProfileService.saveProfile(profileName.getValue(), currentMapping());
        savedProfiles.setItems(mappingProfileService.listProfiles());
        Notification.show("Mapping profile saved.");
    }

    private void importPreview() {
        if (currentPreview == null) {
            Notification.show("Run preview first.");
            return;
        }
        ImportExecutionResult result = invoiceImportService.execute(
                uploadedFileName,
                savedProfiles.getOptionalValue().map(MappingProfileSummary::name).orElse(profileName.getValue()),
                currentPreview);
        Notification.show(result.message());
        summary.setText(result.message());
    }

    private Map<NormalizedInvoiceField, String> currentMapping() {
        Map<NormalizedInvoiceField, String> mapping = new EnumMap<>(NormalizedInvoiceField.class);
        fieldSelectors.forEach((field, selector) -> mapping.put(field, selector.getValue()));
        return mapping;
    }
}
