package com.example.quickbooksimporter.ui;

import com.example.quickbooksimporter.persistence.ReconciliationSessionEntity;
import com.example.quickbooksimporter.service.InvoiceCsvParser;
import com.example.quickbooksimporter.service.ParsedCsvDocument;
import com.example.quickbooksimporter.service.ReconciliationApplyResult;
import com.example.quickbooksimporter.service.ReconciliationMatchResult;
import com.example.quickbooksimporter.service.ReconciliationPreview;
import com.example.quickbooksimporter.service.ReconciliationService;
import com.example.quickbooksimporter.ui.components.UiComponents;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
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
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import jakarta.annotation.security.PermitAll;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Route(value = "reconciliation", layout = MainLayout.class)
@PageTitle("Bank Reconciliation")
@PermitAll
public class ReconciliationView extends VerticalLayout {

    private final InvoiceCsvParser parser;
    private final ReconciliationService reconciliationService;

    private final MemoryBuffer uploadBuffer = new MemoryBuffer();
    private final Upload upload = new Upload(uploadBuffer);
    private final ComboBox<String> txnDateHeader = new ComboBox<>("TxnDate Column");
    private final ComboBox<String> amountHeader = new ComboBox<>("Amount Column");
    private final ComboBox<String> debitHeader = new ComboBox<>("Debit Column (optional)");
    private final ComboBox<String> creditHeader = new ComboBox<>("Credit Column (optional)");
    private final ComboBox<String> referenceHeader = new ComboBox<>("Reference Column");
    private final ComboBox<String> memoHeader = new ComboBox<>("Memo Column");
    private final ComboBox<String> counterpartyHeader = new ComboBox<>("Counterparty Column");
    private final Checkbox dryRun = new Checkbox("Dry run (no QBO writeback)");
    private final ComboBox<String> matchMode = new ComboBox<>("View Mode");

    private final Grid<ReconciliationMatchResult> autoGrid = new Grid<>(ReconciliationMatchResult.class, false);
    private final Grid<ReconciliationMatchResult> reviewGrid = new Grid<>(ReconciliationMatchResult.class, false);
    private final Grid<ReconciliationMatchResult> bankOnlyGrid = new Grid<>(ReconciliationMatchResult.class, false);
    private final Paragraph summary = new Paragraph("Upload bank statement CSV to begin.");
    private final Anchor downloadReport = new Anchor();

    private byte[] uploadedBytes;
    private String uploadedFileName;
    private Long currentSessionId;
    private ReconciliationPreview currentPreview;

    public ReconciliationView(InvoiceCsvParser parser, ReconciliationService reconciliationService) {
        this.parser = parser;
        this.reconciliationService = reconciliationService;

        addClassName("corp-page");
        setSizeFull();
        add(new H2("Bank Reconciliation"),
                new Paragraph("Upload bank statement CSV, auto-match with live QuickBooks transactions, review, then apply."));

        configureUpload();
        configureMapping();
        configureGrids();
        configureActions();
    }

    private void configureUpload() {
        upload.setAcceptedFileTypes(".csv");
        upload.addSucceededListener(event -> {
            uploadedFileName = event.getFileName();
            uploadedBytes = readUpload();
            ParsedCsvDocument document = parser.parse(new ByteArrayInputStream(uploadedBytes));
            List<String> headers = document.headers();
            txnDateHeader.setItems(headers);
            amountHeader.setItems(headers);
            debitHeader.setItems(headers);
            creditHeader.setItems(headers);
            referenceHeader.setItems(headers);
            memoHeader.setItems(headers);
            counterpartyHeader.setItems(headers);
            summary.setText("Loaded " + uploadedFileName + " with " + document.rows().size() + " rows.");
        });
        add(UiComponents.card(UiComponents.sectionTitle("Stage 1: Upload Bank CSV"), upload));
    }

    private void configureMapping() {
        HorizontalLayout mapping = new HorizontalLayout(txnDateHeader, amountHeader, debitHeader, creditHeader,
                referenceHeader, memoHeader, counterpartyHeader, dryRun);
        mapping.setWidthFull();
        mapping.setWrap(true);
        matchMode.setItems("All", "Batch only", "Single only", "WO-only", "Zelle-only", "ACH-only", "Card-only", "Unknown");
        matchMode.setValue("All");
        matchMode.addValueChangeListener(event -> refreshModeFilter());
        mapping.add(matchMode);
        add(UiComponents.card(new H3("Stage 2: Map Columns"), mapping, summary));
    }

    private void configureGrids() {
        configureGrid(autoGrid, "Auto Matched");
        configureGrid(reviewGrid, "Needs Review");
        reviewGrid.setSelectionMode(Grid.SelectionMode.MULTI);
        configureGrid(bankOnlyGrid, "Bank-only");
        add(UiComponents.card(new H3("Stage 3: Results"),
                new Paragraph("Auto Matched"), autoGrid,
                new Paragraph("Needs Review"), reviewGrid,
                new Paragraph("Bank-only"), bankOnlyGrid));
    }

    private void configureGrid(Grid<ReconciliationMatchResult> grid, String bucketName) {
        grid.addColumn(ReconciliationMatchResult::bankRowNumber).setHeader("Bank Row");
        grid.addColumn(result -> result.candidate() == null ? "" : result.candidate().txnId()).setHeader("Primary TxnId");
        grid.addColumn(result -> result.candidate() == null ? "" : result.candidate().entityType()).setHeader("Type");
        grid.addColumn(result -> result.batch() ? "Y" : "N").setHeader("Batch");
        grid.addColumn(ReconciliationMatchResult::candidateCount).setHeader("Cand #");
        grid.addColumn(ReconciliationMatchResult::candidateTxnIds).setHeader("Candidate Txn IDs").setFlexGrow(1);
        grid.addColumn(ReconciliationMatchResult::groupKey).setHeader("Group Key");
        grid.addColumn(ReconciliationMatchResult::patternType).setHeader("Pattern");
        grid.addColumn(ReconciliationMatchResult::patternKey).setHeader("Pattern Key").setFlexGrow(1);
        grid.addColumn(ReconciliationMatchResult::woKey).setHeader("WO Key");
        grid.addColumn(ReconciliationMatchResult::tier).setHeader("Tier");
        grid.addColumn(ReconciliationMatchResult::confidence).setHeader("Confidence");
        grid.addColumn(ReconciliationMatchResult::disposition).setHeader("Disposition");
        grid.addColumn(ReconciliationMatchResult::rationale).setHeader("Reason").setFlexGrow(1);
        grid.setHeight("220px");
        grid.addClassName("corp-grid");
    }

    private void configureActions() {
        Button preview = new Button("Preview Matches", event -> preview());
        Button apply = new Button("Apply to QuickBooks", event -> apply());
        preview.addThemeName("primary");
        apply.addThemeName("error");
        downloadReport.setText("Download Reconciliation Report");
        downloadReport.setVisible(false);
        downloadReport.getElement().setAttribute("download", true);
        add(UiComponents.card(UiComponents.sectionTitle("Stage 4: Execute"), new HorizontalLayout(preview, apply, downloadReport)));
    }

    private void preview() {
        if (uploadedBytes == null) {
            notifyWarning("Upload a CSV file first.");
            return;
        }
        if (txnDateHeader.isEmpty()) {
            notifyWarning("Select transaction date column.");
            return;
        }
        if (amountHeader.isEmpty() && (debitHeader.isEmpty() || creditHeader.isEmpty())) {
            notifyWarning("Select amount column or both debit and credit columns.");
            return;
        }
        ReconciliationService.ReconciliationColumnMapping mapping = new ReconciliationService.ReconciliationColumnMapping(
                txnDateHeader.getValue(),
                amountHeader.getValue(),
                debitHeader.getValue(),
                creditHeader.getValue(),
                referenceHeader.getValue(),
                memoHeader.getValue(),
                counterpartyHeader.getValue());
        ReconciliationPreview preview = reconciliationService.previewMatches(uploadedFileName, uploadedBytes, mapping, dryRun.getValue(), 20);
        currentPreview = preview;
        currentSessionId = preview.sessionId();
        autoGrid.setItems(preview.autoMatched());
        reviewGrid.setItems(preview.needsReview());
        bankOnlyGrid.setItems(preview.bankOnly());
        summary.setText("Session " + currentSessionId + ": auto=" + preview.autoMatched().size() + ", review="
                + preview.needsReview().size() + ", bank-only=" + preview.bankOnly().size() + ", qbo-only=" + preview.qboOnly().size());
        refreshModeFilter();
        configureDownload();
    }

    private void apply() {
        if (currentSessionId == null) {
            notifyWarning("Run preview first.");
            return;
        }
        if (dryRun.getValue()) {
            notifyWarning("Dry run is enabled. Disable dry run to apply writeback.");
            return;
        }
        List<Integer> selectedReviewRows = new ArrayList<>(reviewGrid.getSelectedItems().stream()
                .map(ReconciliationMatchResult::bankRowNumber)
                .toList());
        ReconciliationApplyResult result = reconciliationService.applyMatches(currentSessionId, selectedReviewRows);
        if (result.success()) {
            notifySuccess(result.message());
        } else {
            notifyWarning(result.message());
        }
        configureDownload();
    }

    private void refreshModeFilter() {
        if (currentPreview == null) {
            return;
        }
        String mode = matchMode.getValue() == null ? "All" : matchMode.getValue();
        autoGrid.setItems(filterMode(currentPreview.autoMatched(), mode));
        reviewGrid.setItems(filterMode(currentPreview.needsReview(), mode));
        bankOnlyGrid.setItems(currentPreview.bankOnly());
    }

    private List<ReconciliationMatchResult> filterMode(List<ReconciliationMatchResult> rows, String mode) {
        return rows.stream().filter(row -> switch (mode) {
            case "Batch only" -> row.batch();
            case "Single only" -> !row.batch();
            case "WO-only" -> row.woMatched();
            case "Zelle-only" -> "ZELLE".equals(row.patternType());
            case "ACH-only" -> "ACH".equals(row.patternType());
            case "Card-only" -> "CARD_PURCHASE".equals(row.patternType());
            case "Unknown" -> "UNKNOWN".equals(row.patternType());
            default -> true;
        }).toList();
    }

    private void configureDownload() {
        if (currentSessionId == null) {
            downloadReport.setVisible(false);
            return;
        }
        ReconciliationSessionEntity session = reconciliationService.findSession(currentSessionId).orElse(null);
        if (session == null) {
            downloadReport.setVisible(false);
            return;
        }
        String csv = reconciliationService.exportSessionCsv(currentSessionId);
        downloadReport.setHref(new StreamResource(reconciliationService.exportFileName(currentSessionId),
                () -> new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))));
        downloadReport.setVisible(true);
    }

    private byte[] readUpload() {
        try {
            return uploadBuffer.getInputStream().readAllBytes();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read upload", exception);
        }
    }

    private void notifySuccess(String message) {
        Notification notification = Notification.show(message);
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private void notifyWarning(String message) {
        Notification notification = Notification.show(message);
        notification.addThemeVariants(NotificationVariant.LUMO_WARNING);
    }
}
