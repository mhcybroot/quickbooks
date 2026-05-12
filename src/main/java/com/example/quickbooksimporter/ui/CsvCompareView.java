package com.example.quickbooksimporter.ui;

import com.example.quickbooksimporter.domain.CsvCompareMappingPair;
import com.example.quickbooksimporter.domain.CsvCompareMismatchRow;
import com.example.quickbooksimporter.domain.CsvCompareRequest;
import com.example.quickbooksimporter.domain.CsvCompareResult;
import com.example.quickbooksimporter.service.CsvCompareMappingProfileService;
import com.example.quickbooksimporter.service.CsvCompareService;
import com.example.quickbooksimporter.service.InvoiceCsvParser;
import com.example.quickbooksimporter.service.MappingProfileSummary;
import com.example.quickbooksimporter.service.ParsedCsvDocument;
import com.example.quickbooksimporter.ui.components.UiComponents;
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
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import jakarta.annotation.security.PermitAll;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Route(value = "csv-compare", layout = MainLayout.class)
@PageTitle("CSV Compare")
@PermitAll
public class CsvCompareView extends VerticalLayout {

    private final InvoiceCsvParser parser;
    private final CsvCompareService compareService;
    private final CsvCompareMappingProfileService profileService;

    private final MemoryBuffer file1Buffer = new MemoryBuffer();
    private final MemoryBuffer file2Buffer = new MemoryBuffer();
    private final Upload file1Upload = new Upload(file1Buffer);
    private final Upload file2Upload = new Upload(file2Buffer);
    private final ComboBox<Integer> pairCount = new ComboBox<>("Mapped Pair Count");
    private final ComboBox<MappingProfileSummary> savedProfiles = new ComboBox<>("Saved Compare Profiles");
    private final TextField profileName = new TextField("New Profile Name");
    private final VerticalLayout mappingRows = new VerticalLayout();
    private final Grid<CsvCompareMismatchRow> mismatchGrid = new Grid<>(CsvCompareMismatchRow.class, false);
    private final Paragraph summary = new Paragraph("Upload both CSV files to begin.");
    private final Anchor downloadFile1Mismatch = new Anchor();
    private final Anchor downloadFile2Mismatch = new Anchor();
    private final Anchor downloadCombinedMismatch = new Anchor();

    private final List<PairSelectorRow> selectors = new ArrayList<>();

    private byte[] file1Bytes;
    private byte[] file2Bytes;
    private String file1Name;
    private String file2Name;
    private List<String> file1Headers = List.of();
    private List<String> file2Headers = List.of();

    public CsvCompareView(InvoiceCsvParser parser,
                          CsvCompareService compareService,
                          CsvCompareMappingProfileService profileService) {
        this.parser = parser;
        this.compareService = compareService;
        this.profileService = profileService;

        addClassName("corp-page");
        setSizeFull();
        add(new H2("CSV Compare Workspace"),
                new Paragraph("Upload two CSV files, map matching columns, then review mismatch rows."),
                UiComponents.importStepper("Upload"));

        configureUploads();
        configureProfiles();
        configurePairCount();
        configureMappingArea();
        configureGrid();
        configureActions();
        rebuildMappingRows(1);
    }

    private void configureUploads() {
        file1Upload.setAcceptedFileTypes(".csv");
        file2Upload.setAcceptedFileTypes(".csv");

        file1Upload.addSucceededListener(event -> {
            file1Name = event.getFileName();
            file1Bytes = readUpload(file1Buffer);
            ParsedCsvDocument document = parser.parse(new java.io.ByteArrayInputStream(file1Bytes));
            file1Headers = document.headers();
            refreshMappingDefaults();
            clearPreviewState();
            summary.setText("Loaded file 1: " + file1Name + " (" + document.rows().size() + " rows)");
        });

        file2Upload.addSucceededListener(event -> {
            file2Name = event.getFileName();
            file2Bytes = readUpload(file2Buffer);
            ParsedCsvDocument document = parser.parse(new java.io.ByteArrayInputStream(file2Bytes));
            file2Headers = document.headers();
            refreshMappingDefaults();
            clearPreviewState();
            summary.setText("Loaded file 2: " + file2Name + " (" + document.rows().size() + " rows)");
        });

        HorizontalLayout uploads = new HorizontalLayout(file1Upload, file2Upload);
        uploads.setWidthFull();
        uploads.expand(file1Upload, file2Upload);
        add(UiComponents.card(UiComponents.sectionTitle("Stage 1: Upload CSV Files"), uploads));
    }

    private void configureProfiles() {
        savedProfiles.setItems(profileService.listProfiles());
        savedProfiles.setItemLabelGenerator(MappingProfileSummary::name);
        savedProfiles.addValueChangeListener(event -> {
            if (event.getValue() == null) {
                return;
            }
            List<CsvCompareMappingPair> pairs = profileService.loadProfile(event.getValue().id());
            pairCount.setValue(Math.max(1, pairs.size()));
            applyPairs(pairs);
        });

        HorizontalLayout profileRow = new HorizontalLayout(savedProfiles, profileName);
        profileRow.setWidthFull();
        profileRow.expand(savedProfiles, profileName);
        add(UiComponents.card(UiComponents.sectionTitle("Stage 2: Compare Profile"), profileRow));
    }

    private void configurePairCount() {
        pairCount.setItems(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        pairCount.setValue(1);
        pairCount.addValueChangeListener(event -> {
            int value = event.getValue() == null ? 1 : event.getValue();
            rebuildMappingRows(value);
            refreshMappingDefaults();
            clearPreviewState();
        });
        add(UiComponents.card(new H3("Stage 3: Pair Count"), pairCount));
    }

    private void configureMappingArea() {
        mappingRows.setPadding(false);
        mappingRows.setSpacing(true);
        add(UiComponents.card(new H3("Stage 4: Column Mapping"), mappingRows));
    }

    private void configureGrid() {
        mismatchGrid.addColumn(CsvCompareMismatchRow::key).setHeader("Key (A)");
        mismatchGrid.addColumn(CsvCompareMismatchRow::file1RowNumber).setHeader("File1 Row");
        mismatchGrid.addColumn(CsvCompareMismatchRow::file2RowNumber).setHeader("File2 Row");
        mismatchGrid.addColumn(CsvCompareMismatchRow::mismatchField).setHeader("Field");
        mismatchGrid.addColumn(CsvCompareMismatchRow::file1Value).setHeader("File1 Value");
        mismatchGrid.addColumn(CsvCompareMismatchRow::file2Value).setHeader("File2 Value");
        mismatchGrid.addColumn(CsvCompareMismatchRow::reason).setHeader("Reason");
        mismatchGrid.setHeight("420px");
        mismatchGrid.addClassName("corp-grid");
        add(UiComponents.card(new H3("Stage 5: Mismatch Table"), summary, mismatchGrid));
    }

    private void configureActions() {
        Button previewButton = new Button("Preview Compare", event -> runCompare());
        Button saveProfileButton = new Button("Save Compare Profile", event -> saveProfile());
        downloadFile1Mismatch.setText("Download File1 Mismatches CSV");
        downloadFile2Mismatch.setText("Download File2 Mismatches CSV");
        downloadCombinedMismatch.setText("Download Combined Mismatches CSV");
        setDownloadVisibility(false);
        previewButton.addThemeName("primary");
        HorizontalLayout actions = new HorizontalLayout(
                previewButton,
                saveProfileButton,
                downloadFile1Mismatch,
                downloadFile2Mismatch,
                downloadCombinedMismatch);
        actions.addClassName("corp-action-bar");
        add(UiComponents.card(UiComponents.sectionTitle("Stage 6: Execute"), actions));
    }

    private void runCompare() {
        if (file1Bytes == null || file2Bytes == null) {
            notifyWarning("Upload both CSV files first.");
            return;
        }
        CsvCompareResult result = compareService.preview(new CsvCompareRequest(
                file1Name,
                file1Bytes,
                file2Name,
                file2Bytes,
                currentPairs()));
        mismatchGrid.setItems(result.mismatchRows());
        configureDownloads(result);
        summary.setText("Checked " + result.totalKeysChecked() + " keys. Matched: " + result.matchedKeys()
                + ", mismatched: " + result.mismatchedKeys() + ", missing: " + result.missingKeys() + ".");
    }

    private void saveProfile() {
        if (profileName.isEmpty()) {
            notifyWarning("Enter a profile name first.");
            return;
        }
        profileService.saveProfile(profileName.getValue(), currentPairs());
        savedProfiles.setItems(profileService.listProfiles());
        notifySuccess("CSV compare mapping profile saved.");
    }

    private void rebuildMappingRows(int count) {
        selectors.clear();
        mappingRows.removeAll();
        for (int index = 1; index <= count; index++) {
            String label = String.valueOf((char) ('A' + index - 1));
            ComboBox<String> file1 = new ComboBox<>(label + "1");
            ComboBox<String> file2 = new ComboBox<>(label + "2");
            file1.setWidthFull();
            file2.setWidthFull();
            file1.setPlaceholder("Select file 1 column");
            file2.setPlaceholder("Select file 2 column");
            file1.addValueChangeListener(event -> clearPreviewState());
            file2.addValueChangeListener(event -> clearPreviewState());
            HorizontalLayout row = new HorizontalLayout(file1, file2);
            row.setWidthFull();
            row.expand(file1, file2);
            mappingRows.add(row);
            selectors.add(new PairSelectorRow(index, file1, file2));
        }
    }

    private void refreshMappingDefaults() {
        selectors.forEach(row -> {
            row.file1().setItems(file1Headers);
            row.file2().setItems(file2Headers);
        });
        if (file1Headers.isEmpty() || file2Headers.isEmpty()) {
            return;
        }
        applyPairs(profileService.defaultMapping(file1Headers, file2Headers, selectors.size()));
    }

    private void applyPairs(List<CsvCompareMappingPair> pairs) {
        for (PairSelectorRow selector : selectors) {
            CsvCompareMappingPair pair = pairs.stream()
                    .filter(entry -> entry.index() == selector.index())
                    .findFirst()
                    .orElse(null);
            String value1 = pair == null ? null : pair.file1Header();
            String value2 = pair == null ? null : pair.file2Header();
            selector.file1().setValue(file1Headers.contains(value1) ? value1 : null);
            selector.file2().setValue(file2Headers.contains(value2) ? value2 : null);
        }
        clearPreviewState();
    }

    private List<CsvCompareMappingPair> currentPairs() {
        return selectors.stream()
                .map(selector -> new CsvCompareMappingPair(selector.index(), selector.file1().getValue(), selector.file2().getValue()))
                .toList();
    }

    private byte[] readUpload(MemoryBuffer buffer) {
        try {
            return buffer.getInputStream().readAllBytes();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void notifySuccess(String message) {
        Notification notification = Notification.show(Objects.requireNonNull(message));
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private void notifyWarning(String message) {
        Notification notification = Notification.show(Objects.requireNonNull(message));
        notification.addThemeVariants(NotificationVariant.LUMO_WARNING);
    }

    private void configureDownloads(CsvCompareResult result) {
        if (result.mismatchRows().isEmpty()) {
            setDownloadVisibility(false);
            return;
        }
        String base1 = normalizedBaseName(result.file1Name(), "file1");
        String base2 = normalizedBaseName(result.file2Name(), "file2");
        downloadFile1Mismatch.setHref(new StreamResource(base1 + "-mismatches.csv",
                () -> new java.io.ByteArrayInputStream(result.file1MismatchCsv().getBytes(StandardCharsets.UTF_8))));
        downloadFile2Mismatch.setHref(new StreamResource(base2 + "-mismatches.csv",
                () -> new java.io.ByteArrayInputStream(result.file2MismatchCsv().getBytes(StandardCharsets.UTF_8))));
        downloadCombinedMismatch.setHref(new StreamResource("combined-mismatches.csv",
                () -> new java.io.ByteArrayInputStream(result.combinedMismatchCsv().getBytes(StandardCharsets.UTF_8))));
        setDownloadVisibility(true);
    }

    private String normalizedBaseName(String fileName, String fallback) {
        if (fileName == null || fileName.isBlank()) {
            return fallback;
        }
        String name = fileName.endsWith(".csv") ? fileName.substring(0, fileName.length() - 4) : fileName;
        return name.isBlank() ? fallback : name;
    }

    private void clearPreviewState() {
        mismatchGrid.setItems(List.of());
        setDownloadVisibility(false);
    }

    private void setDownloadVisibility(boolean visible) {
        downloadFile1Mismatch.setVisible(visible);
        downloadFile2Mismatch.setVisible(visible);
        downloadCombinedMismatch.setVisible(visible);
    }

    private record PairSelectorRow(int index, ComboBox<String> file1, ComboBox<String> file2) {
    }
}
