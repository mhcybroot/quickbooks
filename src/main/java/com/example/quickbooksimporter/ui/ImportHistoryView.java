package com.example.quickbooksimporter.ui;

import com.example.quickbooksimporter.domain.EntityType;
import com.example.quickbooksimporter.domain.ImportRunStatus;
import com.example.quickbooksimporter.persistence.ImportBatchEntity;
import com.example.quickbooksimporter.persistence.ImportRowResultEntity;
import com.example.quickbooksimporter.persistence.ImportRunEntity;
import com.example.quickbooksimporter.service.ImportHistoryService;
import com.example.quickbooksimporter.service.ImportProgressService;
import com.example.quickbooksimporter.ui.components.UiComponents;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import com.vaadin.flow.server.StreamResource;

@Route(value = "history", layout = MainLayout.class)
@PageTitle("Import History")
@PermitAll
public class ImportHistoryView extends VerticalLayout {

    private final ImportHistoryService historyService;
    private final ImportProgressService progressService;
    private final Grid<ImportRunEntity> runGrid = new Grid<>(ImportRunEntity.class, false);
    private final Grid<ImportRowResultEntity> rowGrid = new Grid<>(ImportRowResultEntity.class, false);
    private final Grid<ImportBatchEntity> batchGrid = new Grid<>(ImportBatchEntity.class, false);
    private final ComboBox<EntityType> entityFilter = new ComboBox<>("Entity");
    private final ComboBox<ImportRunStatus> statusFilter = new ComboBox<>("Run Status");
    private final DatePicker dateFilter = new DatePicker("Created On/After");
    private final TextField fileFilter = new TextField("Source File");
    private final Paragraph runScopeSummary = new Paragraph("Showing the latest runs across all import types.");
    private final Anchor downloadRunCsv = new Anchor();

    public ImportHistoryView(ImportHistoryService historyService,
                             ImportProgressService progressService) {
        this.historyService = historyService;
        this.progressService = progressService;
        addClassName("corp-page");
        setSizeFull();

        add(new H2("Import History & Operations"),
                new Paragraph("Review cross-entity runs, batch membership, row-level failures, and recent operational throughput."));
        getUI().ifPresent(ui -> ui.addPollListener(event -> refreshRuns()));
        configureFilters();
        configureBatchGrid();
        configureRunGrid();
        configureRowGrid();
        refreshRuns();
    }

    private void configureFilters() {
        entityFilter.setItems(EntityType.values());
        entityFilter.setItemLabelGenerator(EntityType::displayName);
        statusFilter.setItems(ImportRunStatus.values());

        Button apply = new Button("Apply Filters", event -> refreshRuns());
        Button refresh = new Button("Refresh", event -> refreshRuns());
        Button reset = new Button("Reset", event -> {
            batchGrid.deselectAll();
            entityFilter.clear();
            statusFilter.clear();
            dateFilter.clear();
            fileFilter.clear();
            refreshRuns();
        });
        apply.addThemeName("primary");

        HorizontalLayout filters = new HorizontalLayout(entityFilter, statusFilter, dateFilter, fileFilter, apply, refresh, reset);
        filters.setAlignItems(Alignment.END);
        add(UiComponents.card(new H3("Filters"), filters));
    }

    private void configureBatchGrid() {
        batchGrid.addColumn(ImportBatchEntity::getId).setHeader("Batch").setAutoWidth(true);
        batchGrid.addColumn(ImportBatchEntity::getBatchName).setHeader("Name").setAutoWidth(true).setFlexGrow(1);
        batchGrid.addColumn(batch -> batch.getStatus().name()).setHeader("Status").setAutoWidth(true);
        batchGrid.addColumn(ImportBatchEntity::getTotalFiles).setHeader("Files").setAutoWidth(true);
        batchGrid.addColumn(ImportBatchEntity::getCompletedFiles).setHeader("Completed").setAutoWidth(true);
        batchGrid.addColumn(batch -> progressService.findBatchProgress(batch.getId())
                .map(snapshot -> snapshot.percentLabel() + " | " + snapshot.remainingLabel())
                .orElse("-")).setHeader("Progress").setAutoWidth(true);
        batchGrid.addColumn(ImportBatchEntity::getUpdatedAt).setHeader("Updated").setAutoWidth(true).setFlexGrow(1);
        batchGrid.setItems(historyService.recentBatches());
        batchGrid.setHeight("220px");
        batchGrid.addClassName("corp-grid");
        batchGrid.asSingleSelect().addValueChangeListener(event -> {
            ImportBatchEntity selectedBatch = event.getValue();
            if (selectedBatch == null) {
                refreshRuns();
                return;
            }
            List<ImportRunEntity> batchRuns = historyService.runsForBatch(selectedBatch.getId());
            runGrid.setItems(batchRuns);
            rowGrid.setItems(List.of());
            runScopeSummary.setText("Showing " + batchRuns.size() + " runs for batch " + selectedBatch.getBatchName() + ".");
        });
        add(UiComponents.card(new H3("Recent Batches"), batchGrid));
    }

    private void configureRunGrid() {
        runGrid.addColumn(ImportRunEntity::getId).setHeader("Run ID").setAutoWidth(true).setFlexGrow(0);
        runGrid.addColumn(run -> run.getEntityType().displayName()).setHeader("Entity").setAutoWidth(true).setFlexGrow(0);
        runGrid.addColumn(ImportRunEntity::getSourceFileName).setHeader("File").setAutoWidth(true).setFlexGrow(1);
        runGrid.addColumn(run -> run.getBatch() == null ? "-" : run.getBatch().getBatchName()).setHeader("Batch").setAutoWidth(true).setFlexGrow(0);
        runGrid.addColumn(ImportRunEntity::getBatchOrder).setHeader("Order").setAutoWidth(true).setFlexGrow(0);
        runGrid.addColumn(ImportRunEntity::getDependencyGroup).setHeader("Dependency").setAutoWidth(true).setFlexGrow(0);
        runGrid.addColumn(ImportRunEntity::getStatus).setHeader("Status").setAutoWidth(true).setFlexGrow(0);
        runGrid.addColumn(ImportRunEntity::getTotalRows).setHeader("Rows").setAutoWidth(true).setFlexGrow(0);
        runGrid.addColumn(ImportRunEntity::getAttemptedRows).setHeader("Attempted").setAutoWidth(true).setFlexGrow(0);
        runGrid.addColumn(ImportRunEntity::getSkippedRows).setHeader("Skipped").setAutoWidth(true).setFlexGrow(0);
        runGrid.addColumn(ImportRunEntity::getImportedRows).setHeader("Imported").setAutoWidth(true).setFlexGrow(0);
        runGrid.addColumn(run -> progressService.findRunProgress(run.getId())
                .map(snapshot -> snapshot.percentLabel() + " | " + snapshot.remainingLabel())
                .orElse("-")).setHeader("Progress").setAutoWidth(true).setFlexGrow(0);
        runGrid.addColumn(ImportRunEntity::getCreatedAt).setHeader("Created").setAutoWidth(true).setFlexGrow(1);
        runGrid.setWidthFull();
        runGrid.addClassName("corp-grid");
        runGrid.asSingleSelect().addValueChangeListener(event -> {
            ImportRunEntity selected = event.getValue();
            rowGrid.setItems(selected == null ? List.of() : selected.getRowResults());
            configureRunDownload(selected);
        });
    }

    private void configureRowGrid() {
        rowGrid.addColumn(ImportRowResultEntity::getRowNumber).setHeader("Row").setAutoWidth(true);
        rowGrid.addColumn(ImportRowResultEntity::getSourceIdentifier).setHeader("Source ID").setAutoWidth(true).setFlexGrow(0);
        rowGrid.addColumn(ImportRowResultEntity::getStatus).setHeader("Status").setAutoWidth(true).setFlexGrow(0);
        rowGrid.addColumn(ImportRowResultEntity::getMessage).setHeader("Message").setAutoWidth(true).setFlexGrow(1);
        rowGrid.setWidthFull();
        rowGrid.addClassName("corp-grid");

        VerticalLayout detail = UiComponents.card(new H3("Row Results"), rowGrid);
        detail.setWidth("58%");
        detail.setSizeFull();
        downloadRunCsv.setText("Download Run CSV");
        downloadRunCsv.setVisible(false);
        downloadRunCsv.getElement().setAttribute("download", true);
        VerticalLayout runsCard = UiComponents.card(new H3("Run Results"), runScopeSummary, downloadRunCsv, runGrid);
        runsCard.setWidth("42%");
        runsCard.setMinWidth("480px");

        HorizontalLayout grids = new HorizontalLayout(runsCard, detail);
        grids.setSizeFull();
        grids.setFlexGrow(0, runsCard);
        grids.setFlexGrow(1, detail);
        add(grids);
    }

    private void refreshRuns() {
        Long selectedRunId = runGrid.asSingleSelect().getValue() == null ? null : runGrid.asSingleSelect().getValue().getId();
        List<ImportRunEntity> runs = historyService.filterRuns(
                entityFilter.getValue(),
                statusFilter.getValue(),
                dateFilter.getValue(),
                fileFilter.getValue());
        runGrid.setItems(runs);
        ImportRunEntity selectedRun = selectedRunId == null ? null : runs.stream()
                .filter(run -> selectedRunId.equals(run.getId()))
                .findFirst()
                .orElse(null);
        if (selectedRun == null) {
            rowGrid.setItems(List.of());
            runGrid.deselectAll();
            configureRunDownload(null);
        } else {
            runGrid.asSingleSelect().setValue(selectedRun);
            rowGrid.setItems(selectedRun.getRowResults() == null ? List.of() : selectedRun.getRowResults());
            configureRunDownload(selectedRun);
        }
        batchGrid.setItems(historyService.recentBatches());
        runScopeSummary.setText("Showing " + runs.size() + " filtered runs across all batches and standalone imports.");
        boolean hasLive = runs.stream().anyMatch(run -> run.getStatus() == ImportRunStatus.QUEUED || run.getStatus() == ImportRunStatus.RUNNING);
        getUI().ifPresent(ui -> ui.setPollInterval(hasLive ? 5000 : -1));
    }

    private void configureRunDownload(ImportRunEntity selected) {
        if (selected == null || selected.getId() == null) {
            downloadRunCsv.setVisible(false);
            downloadRunCsv.setHref("");
            return;
        }
        Optional<ImportRunEntity> run = historyService.findRun(selected.getId());
        if (run.isEmpty()) {
            downloadRunCsv.setVisible(false);
            downloadRunCsv.setHref("");
            return;
        }
        String csv = historyService.buildRunExportCsv(run.get());
        String fileName = historyService.runExportFileName(run.get());
        downloadRunCsv.setHref(new StreamResource(fileName,
                () -> new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8))));
        downloadRunCsv.setVisible(true);
    }
}
