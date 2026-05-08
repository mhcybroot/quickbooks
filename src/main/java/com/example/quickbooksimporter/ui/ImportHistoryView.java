package com.example.quickbooksimporter.ui;

import com.example.quickbooksimporter.domain.EntityType;
import com.example.quickbooksimporter.domain.ImportRunStatus;
import com.example.quickbooksimporter.persistence.ImportBatchEntity;
import com.example.quickbooksimporter.persistence.ImportRowResultEntity;
import com.example.quickbooksimporter.persistence.ImportRunEntity;
import com.example.quickbooksimporter.service.ImportHistoryService;
import com.example.quickbooksimporter.ui.components.UiComponents;
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
import java.util.List;

@Route(value = "history", layout = MainLayout.class)
@PageTitle("Import History")
@PermitAll
public class ImportHistoryView extends VerticalLayout {

    private final ImportHistoryService historyService;
    private final Grid<ImportRunEntity> runGrid = new Grid<>(ImportRunEntity.class, false);
    private final Grid<ImportRowResultEntity> rowGrid = new Grid<>(ImportRowResultEntity.class, false);
    private final Grid<ImportBatchEntity> batchGrid = new Grid<>(ImportBatchEntity.class, false);
    private final ComboBox<EntityType> entityFilter = new ComboBox<>("Entity");
    private final ComboBox<ImportRunStatus> statusFilter = new ComboBox<>("Run Status");
    private final DatePicker dateFilter = new DatePicker("Created On/After");
    private final TextField fileFilter = new TextField("Source File");

    public ImportHistoryView(ImportHistoryService historyService) {
        this.historyService = historyService;
        addClassName("corp-page");
        setSizeFull();

        add(new H2("Import History & Operations"),
                new Paragraph("Review cross-entity runs, batch membership, row-level failures, and recent operational throughput."));
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
        Button reset = new Button("Reset", event -> {
            entityFilter.clear();
            statusFilter.clear();
            dateFilter.clear();
            fileFilter.clear();
            refreshRuns();
        });
        apply.addThemeName("primary");

        HorizontalLayout filters = new HorizontalLayout(entityFilter, statusFilter, dateFilter, fileFilter, apply, reset);
        filters.setAlignItems(Alignment.END);
        add(UiComponents.card(new H3("Filters"), filters));
    }

    private void configureBatchGrid() {
        batchGrid.addColumn(ImportBatchEntity::getId).setHeader("Batch").setAutoWidth(true);
        batchGrid.addColumn(ImportBatchEntity::getBatchName).setHeader("Name").setAutoWidth(true).setFlexGrow(1);
        batchGrid.addColumn(batch -> batch.getStatus().name()).setHeader("Status").setAutoWidth(true);
        batchGrid.addColumn(ImportBatchEntity::getTotalFiles).setHeader("Files").setAutoWidth(true);
        batchGrid.addColumn(ImportBatchEntity::getCompletedFiles).setHeader("Completed").setAutoWidth(true);
        batchGrid.addColumn(ImportBatchEntity::getUpdatedAt).setHeader("Updated").setAutoWidth(true).setFlexGrow(1);
        batchGrid.setItems(historyService.recentBatches());
        batchGrid.setHeight("220px");
        batchGrid.addClassName("corp-grid");
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
        runGrid.addColumn(ImportRunEntity::getImportedRows).setHeader("Imported").setAutoWidth(true).setFlexGrow(0);
        runGrid.addColumn(ImportRunEntity::getCreatedAt).setHeader("Created").setAutoWidth(true).setFlexGrow(1);
        runGrid.setWidthFull();
        runGrid.addClassName("corp-grid");
        runGrid.asSingleSelect().addValueChangeListener(event -> {
            ImportRunEntity selected = event.getValue();
            rowGrid.setItems(selected == null ? List.of() : selected.getRowResults());
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
        VerticalLayout runsCard = UiComponents.card(new H3("Run Results"), runGrid);
        runsCard.setWidth("42%");
        runsCard.setMinWidth("480px");

        HorizontalLayout grids = new HorizontalLayout(runsCard, detail);
        grids.setSizeFull();
        grids.setFlexGrow(0, runsCard);
        grids.setFlexGrow(1, detail);
        add(grids);
    }

    private void refreshRuns() {
        List<ImportRunEntity> runs = historyService.filterRuns(
                entityFilter.getValue(),
                statusFilter.getValue(),
                dateFilter.getValue(),
                fileFilter.getValue());
        runGrid.setItems(runs);
        rowGrid.setItems(List.of());
        batchGrid.setItems(historyService.recentBatches());
    }
}
