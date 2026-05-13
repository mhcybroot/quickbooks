package com.example.quickbooksimporter.ui;

import com.example.quickbooksimporter.domain.EntityType;
import com.example.quickbooksimporter.persistence.ImportBatchEntity;
import com.example.quickbooksimporter.persistence.ImportRunEntity;
import com.example.quickbooksimporter.service.ImportHistoryService;
import com.example.quickbooksimporter.service.QuickBooksConnectionService;
import com.example.quickbooksimporter.service.QuickBooksConnectionStatus;
import com.example.quickbooksimporter.ui.components.UiComponents;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import java.util.List;

@Route(value = "", layout = MainLayout.class)
@PageTitle("Import Workspace")
@PermitAll
public class ImportWorkspaceView extends VerticalLayout {

    public ImportWorkspaceView(QuickBooksConnectionService connectionService,
                               ImportHistoryService historyService) {
        QuickBooksConnectionStatus status = connectionService.getStatus();
        List<ImportRunEntity> recentRuns = historyService.recentRuns().stream().limit(12).toList();
        List<ImportBatchEntity> recentBatches = historyService.recentBatches();

        addClassName("corp-page");
        setSizeFull();
        add(new H2("Import Workspace"),
                new Paragraph("Use one workspace to monitor connection health, launch single imports fast, and coordinate multi-file batch imports."));
        add(UiComponents.importStepper("Upload"));

        HorizontalLayout kpis = new HorizontalLayout(
                UiComponents.kpi("Connection", status.connected() ? "Active" : "Needs Setup",
                        status.connected() ? "Realm " + status.realmId() : "Connect QuickBooks before running imports"),
                UiComponents.kpi("Recent Runs", String.valueOf(recentRuns.size()), "Cross-entity operations shown below"),
                UiComponents.kpi("Recent Batches", String.valueOf(recentBatches.size()), "Latest multi-file sessions"),
                UiComponents.kpi("Environment", status.environment().toUpperCase(),
                        status.expiresAt() == null ? "Token not loaded" : "Token expiry " + status.expiresAt()));
        kpis.setWidthFull();
        kpis.addClassName("corp-kpi-row");
        add(kpis);

        ComboBox<EntityType> importType = new ComboBox<>("Single Import Type");
        importType.setItems(EntityType.values());
        importType.setItemLabelGenerator(EntityType::displayName);
        importType.setValue(EntityType.INVOICE);

        Button openSingle = new Button("Open Single Import", event ->
                UI.getCurrent().navigate(ImportRoutes.routeFor(importType.getValue())));
        Button openBatch = new Button("Open Batch Workspace", event -> UI.getCurrent().navigate("batch"));
        Button openHistory = new Button("Open History", event -> UI.getCurrent().navigate(ImportHistoryView.class));
        openSingle.addThemeName("primary");
        openBatch.addThemeName("primary");

        HorizontalLayout quickActions = new HorizontalLayout(importType, openSingle, openBatch, openHistory);
        quickActions.setWidthFull();
        quickActions.setWrap(true);
        quickActions.setAlignItems(Alignment.END);
        quickActions.expand(importType);
        add(UiComponents.card(
                new H3("Quick Actions"),
                new Paragraph("Power-user shortcuts for repeat imports, queue management, and operations review."),
                quickActions));

        Grid<ImportBatchEntity> batchGrid = new Grid<>(ImportBatchEntity.class, false);
        batchGrid.addColumn(ImportBatchEntity::getId).setHeader("Batch").setAutoWidth(true);
        batchGrid.addColumn(ImportBatchEntity::getBatchName).setHeader("Name").setAutoWidth(true).setFlexGrow(1);
        batchGrid.addColumn(batch -> batch.getStatus().name()).setHeader("Status").setAutoWidth(true);
        batchGrid.addColumn(ImportBatchEntity::getTotalFiles).setHeader("Files").setAutoWidth(true);
        batchGrid.addColumn(ImportBatchEntity::getRunnableFiles).setHeader("Runnable").setAutoWidth(true);
        batchGrid.addColumn(ImportBatchEntity::getCompletedFiles).setHeader("Completed").setAutoWidth(true);
        batchGrid.addColumn(ImportBatchEntity::getUpdatedAt).setHeader("Updated").setAutoWidth(true).setFlexGrow(1);
        batchGrid.setItems(recentBatches);
        batchGrid.setHeight("240px");
        batchGrid.setWidthFull();
        batchGrid.addClassName("corp-grid");

        Grid<ImportRunEntity> runGrid = new Grid<>(ImportRunEntity.class, false);
        runGrid.addColumn(ImportRunEntity::getId).setHeader("Run").setAutoWidth(true);
        runGrid.addColumn(run -> run.getEntityType().displayName()).setHeader("Entity").setAutoWidth(true);
        runGrid.addColumn(ImportRunEntity::getSourceFileName).setHeader("File").setAutoWidth(true).setFlexGrow(1);
        runGrid.addColumn(run -> run.getBatch() == null ? "-" : run.getBatch().getBatchName()).setHeader("Batch").setAutoWidth(true);
        runGrid.addColumn(ImportRunEntity::getStatus).setHeader("Status").setAutoWidth(true);
        runGrid.addColumn(ImportRunEntity::getImportedRows).setHeader("Imported").setAutoWidth(true);
        runGrid.addColumn(ImportRunEntity::getCreatedAt).setHeader("Created").setAutoWidth(true).setFlexGrow(1);
        runGrid.setItems(recentRuns);
        runGrid.setHeight("320px");
        runGrid.setWidthFull();
        runGrid.addClassName("corp-grid");

        VerticalLayout batchCard = UiComponents.card(new H3("Import Queue"), batchGrid);
        batchCard.setWidthFull();
        batchCard.addClassName("corp-workspace-card");

        VerticalLayout activityCard = UiComponents.card(new H3("Recent Activity"), runGrid);
        activityCard.setWidthFull();
        activityCard.addClassName("corp-workspace-card");

        HorizontalLayout workspaceRow = new HorizontalLayout(batchCard, activityCard);
        workspaceRow.setWidthFull();
        workspaceRow.setSpacing(true);
        workspaceRow.setPadding(false);
        workspaceRow.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.STRETCH);
        workspaceRow.setFlexGrow(1, batchCard, activityCard);
        workspaceRow.addClassName("corp-workspace-grid");
        add(workspaceRow);
    }
}
