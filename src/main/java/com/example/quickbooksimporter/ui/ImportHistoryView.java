package com.example.quickbooksimporter.ui;

import com.example.quickbooksimporter.persistence.ImportRowResultEntity;
import com.example.quickbooksimporter.persistence.ImportRunEntity;
import com.example.quickbooksimporter.service.InvoiceImportService;
import com.example.quickbooksimporter.ui.components.UiComponents;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import java.util.List;

@Route(value = "history", layout = MainLayout.class)
@PageTitle("Import History")
@PermitAll
public class ImportHistoryView extends VerticalLayout {

    public ImportHistoryView(InvoiceImportService invoiceImportService) {
        addClassName("corp-page");
        setSizeFull();
        List<ImportRunEntity> runs = invoiceImportService.recentRuns();

        int totalRows = runs.stream().mapToInt(ImportRunEntity::getTotalRows).sum();
        int importedRows = runs.stream().mapToInt(ImportRunEntity::getImportedRows).sum();
        int failedRows = Math.max(0, totalRows - importedRows);

        HorizontalLayout summaryRow = new HorizontalLayout(
                UiComponents.kpi("Recent Runs", String.valueOf(runs.size()), "Latest runs stored in history"),
                UiComponents.kpi("Imported Rows", String.valueOf(importedRows), "Successful invoice rows"),
                UiComponents.kpi("Failed Rows", String.valueOf(failedRows), "Rows that failed import"));
        summaryRow.setWidthFull();
        summaryRow.setFlexGrow(1);

        VerticalLayout analyticsCard = UiComponents.card(
                new H3("Import Performance"),
                new Paragraph("Imported: " + importedRows + " | Failed: " + failedRows + " | Total Rows: " + totalRows));
        add(new H2("Import History & Performance"), summaryRow, analyticsCard);

        Grid<ImportRunEntity> runGrid = new Grid<>(ImportRunEntity.class, false);
        runGrid.addColumn(ImportRunEntity::getId).setHeader("Run ID");
        runGrid.addColumn(run -> run.getEntityType().name()).setHeader("Entity");
        runGrid.addColumn(ImportRunEntity::getSourceFileName).setHeader("File");
        runGrid.addColumn(ImportRunEntity::getStatus).setHeader("Status");
        runGrid.addColumn(ImportRunEntity::getTotalRows).setHeader("Rows");
        runGrid.addColumn(ImportRunEntity::getImportedRows).setHeader("Imported");
        runGrid.addColumn(ImportRunEntity::getCreatedAt).setHeader("Created");
        runGrid.setItems(runs);
        runGrid.setWidth("45%");
        runGrid.addClassName("corp-grid");

        Grid<ImportRowResultEntity> rowGrid = new Grid<>(ImportRowResultEntity.class, false);
        rowGrid.addColumn(ImportRowResultEntity::getRowNumber).setHeader("Row");
        rowGrid.addColumn(ImportRowResultEntity::getSourceIdentifier).setHeader("Invoice #");
        rowGrid.addColumn(ImportRowResultEntity::getStatus).setHeader("Status");
        rowGrid.addColumn(ImportRowResultEntity::getMessage).setHeader("Message").setAutoWidth(true).setFlexGrow(1);
        rowGrid.setWidthFull();
        rowGrid.addClassName("corp-grid");

        runGrid.asSingleSelect().addValueChangeListener(event -> {
            ImportRunEntity selected = event.getValue();
            rowGrid.setItems(selected == null ? java.util.List.of() : selected.getRowResults());
        });

        VerticalLayout detail = UiComponents.card(new H3("Row Results"), rowGrid);
        detail.setWidth("55%");
        detail.setSizeFull();
        VerticalLayout runsCard = UiComponents.card(new H3("Recent Imports"), runGrid);
        runsCard.setWidth("45%");
        HorizontalLayout grids = new HorizontalLayout(runsCard, detail);
        grids.setSizeFull();
        grids.setFlexGrow(1, detail);
        add(grids);
    }
}
