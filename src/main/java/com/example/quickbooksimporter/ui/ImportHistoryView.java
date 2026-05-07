package com.example.quickbooksimporter.ui;

import com.example.quickbooksimporter.persistence.ImportRowResultEntity;
import com.example.quickbooksimporter.persistence.ImportRunEntity;
import com.example.quickbooksimporter.service.InvoiceImportService;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route(value = "history", layout = MainLayout.class)
@PageTitle("Import History")
@PermitAll
public class ImportHistoryView extends HorizontalLayout {

    public ImportHistoryView(InvoiceImportService invoiceImportService) {
        setSizeFull();

        Grid<ImportRunEntity> runGrid = new Grid<>(ImportRunEntity.class, false);
        runGrid.addColumn(ImportRunEntity::getId).setHeader("Run ID");
        runGrid.addColumn(ImportRunEntity::getSourceFileName).setHeader("File");
        runGrid.addColumn(ImportRunEntity::getStatus).setHeader("Status");
        runGrid.addColumn(ImportRunEntity::getTotalRows).setHeader("Rows");
        runGrid.addColumn(ImportRunEntity::getImportedRows).setHeader("Imported");
        runGrid.addColumn(ImportRunEntity::getCreatedAt).setHeader("Created");
        runGrid.setItems(invoiceImportService.recentRuns());
        runGrid.setWidth("45%");

        Grid<ImportRowResultEntity> rowGrid = new Grid<>(ImportRowResultEntity.class, false);
        rowGrid.addColumn(ImportRowResultEntity::getRowNumber).setHeader("Row");
        rowGrid.addColumn(ImportRowResultEntity::getSourceIdentifier).setHeader("Invoice #");
        rowGrid.addColumn(ImportRowResultEntity::getStatus).setHeader("Status");
        rowGrid.addColumn(ImportRowResultEntity::getMessage).setHeader("Message").setAutoWidth(true).setFlexGrow(1);
        rowGrid.setWidthFull();

        runGrid.asSingleSelect().addValueChangeListener(event -> {
            ImportRunEntity selected = event.getValue();
            rowGrid.setItems(selected == null ? java.util.List.of() : selected.getRowResults());
        });

        VerticalLayout detail = new VerticalLayout(new H2("Row Results"), rowGrid);
        detail.setWidth("55%");
        detail.setSizeFull();
        add(new VerticalLayout(new H2("Recent Imports"), runGrid), detail);
    }
}
