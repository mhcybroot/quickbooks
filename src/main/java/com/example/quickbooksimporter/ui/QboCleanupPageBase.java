package com.example.quickbooksimporter.ui;

import com.example.quickbooksimporter.service.QboCleanupEntityType;
import com.example.quickbooksimporter.service.QboCleanupFilter;
import com.example.quickbooksimporter.service.QboCleanupResult;
import com.example.quickbooksimporter.service.QboCleanupService;
import com.example.quickbooksimporter.service.QboTransactionRow;
import com.example.quickbooksimporter.ui.components.UiComponents;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.TextField;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class QboCleanupPageBase extends VerticalLayout {

    private final QboCleanupService cleanupService;
    private final QboCleanupEntityType entityType;

    private final DatePicker fromDate = new DatePicker("From Date");
    private final DatePicker toDate = new DatePicker("To Date");
    private final TextField docRef = new TextField("Doc/Reference Contains");
    private final TextField party = new TextField("Customer/Vendor Contains");
    private final Grid<QboTransactionRow> grid = new Grid<>(QboTransactionRow.class, false);
    private final Grid<QboCleanupResult> resultGrid = new Grid<>(QboCleanupResult.class, false);
    private final Paragraph summary = new Paragraph("Select filters then click Search.");
    private final ComboBox<Integer> pageSize = new ComboBox<>("Page Size");
    private final Button searchButton = new Button("Search");

    private List<QboTransactionRow> currentRows = List.of();

    protected QboCleanupPageBase(QboCleanupService cleanupService, QboCleanupEntityType entityType, String pageTitle) {
        this.cleanupService = cleanupService;
        this.entityType = entityType;
        addClassName("corp-page");
        setSizeFull();
        add(new H3(pageTitle), new Paragraph("Manage existing live QuickBooks records."));
        configureFilters();
        configureGrid();
        configureResultsGrid();
        configureActions();
    }

    private void configureFilters() {
        pageSize.setItems(50, 100, 200, 500);
        pageSize.setValue(200);
        searchButton.addThemeName("primary");
        searchButton.addClickListener(event -> search(false));
        HorizontalLayout row = new HorizontalLayout(fromDate, toDate, docRef, party, pageSize, searchButton);
        row.setWidthFull();
        row.expand(docRef, party);
        row.setAlignItems(Alignment.END);
        add(UiComponents.card(UiComponents.sectionTitle("Filters"), row, summary));
    }

    private void configureGrid() {
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setSelectionMode(Grid.SelectionMode.MULTI);
        grid.addColumn(QboTransactionRow::externalNumber).setHeader("Doc/Reference").setAutoWidth(true);
        grid.addColumn(QboTransactionRow::txnDate).setHeader("Txn Date").setAutoWidth(true);
        grid.addColumn(QboTransactionRow::partyName).setHeader("Customer/Vendor").setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(QboTransactionRow::totalAmount).setHeader("Total").setAutoWidth(true);
        grid.addColumn(QboTransactionRow::balance).setHeader("Balance").setAutoWidth(true);
        grid.addColumn(QboTransactionRow::id).setHeader("QBO ID").setAutoWidth(true);
        grid.setHeight("360px");
        add(UiComponents.card(UiComponents.sectionTitle("QuickBooks Records"), grid));
    }

    private void configureResultsGrid() {
        resultGrid.addColumn(QboCleanupResult::action).setHeader("Action").setAutoWidth(true);
        resultGrid.addColumn(QboCleanupResult::externalNumber).setHeader("Doc/Reference").setAutoWidth(true);
        resultGrid.addColumn(QboCleanupResult::success).setHeader("Success").setAutoWidth(true);
        resultGrid.addComponentColumn(result -> {
                    Span message = new Span(result.message());
                    message.getElement().setProperty("title", result.message());
                    message.addClassName("cleanup-message-cell");
                    return message;
                }).setHeader("Message").setFlexGrow(1);
        resultGrid.addColumn(QboCleanupResult::intuitTid).setHeader("intuit_tid").setAutoWidth(true);
        resultGrid.setHeight("260px");
        resultGrid.setAllRowsVisible(true);
        resultGrid.addClassNames("corp-grid", "cleanup-results-grid");
        add(UiComponents.card(UiComponents.sectionTitle("Operation Results"), resultGrid));
    }

    private void configureActions() {
        Button deleteSelected = new Button("Delete Selected", event -> confirmTwoStep("Delete selected records?", () -> runDeleteSelected()));
        Button voidSelected = new Button("Void Selected", event -> confirmTwoStep("Void selected records?", () -> runVoidSelected()));
        Button deleteAll = new Button("Delete All", event -> askDeleteAllScope());
        Button voidAll = new Button("Void All", event -> confirmTwoStep("Void all currently visible records?", () -> runVoidAllVisible()));

        deleteSelected.addThemeName("error");
        deleteAll.addThemeName("error");
        if (!entityType.voidSupported()) {
            voidSelected.setEnabled(false);
            voidAll.setEnabled(false);
        }
        HorizontalLayout actionBar = new HorizontalLayout(deleteSelected, voidSelected, deleteAll, voidAll);
        actionBar.addClassName("corp-action-bar");
        add(UiComponents.card(UiComponents.sectionTitle("Actions"), actionBar));
    }

    private void search(boolean includeAll) {
        QboCleanupFilter filter = new QboCleanupFilter(
                fromDate.getValue(),
                toDate.getValue(),
                docRef.getValue(),
                party.getValue(),
                pageSize.getValue() == null ? 200 : pageSize.getValue());
        currentRows = cleanupService.list(entityType, filter, includeAll);
        grid.setItems(currentRows);
        summary.setText("Loaded " + currentRows.size() + " records.");
    }

    private void runDeleteSelected() {
        List<QboTransactionRow> selected = new ArrayList<>(grid.getSelectedItems());
        if (selected.isEmpty()) {
            notifyWarning("Select at least one record.");
            return;
        }
        QboCleanupService.CleanupActionResponse response = cleanupService.delete(entityType, selected);
        resultGrid.setItems(response.results());
        notifyOutcome(response.results(), "Delete completed for selected records.");
        search(false);
    }

    private void runVoidSelected() {
        List<QboTransactionRow> selected = new ArrayList<>(grid.getSelectedItems());
        if (selected.isEmpty()) {
            notifyWarning("Select at least one record.");
            return;
        }
        QboCleanupService.CleanupActionResponse response = cleanupService.voidTransactions(entityType, selected);
        resultGrid.setItems(response.results());
        notifyOutcome(response.results(), "Void completed for selected records.");
        search(false);
    }

    private void runVoidAllVisible() {
        if (currentRows.isEmpty()) {
            notifyWarning("Run Search first.");
            return;
        }
        QboCleanupService.CleanupActionResponse response = cleanupService.voidTransactions(entityType, currentRows);
        resultGrid.setItems(response.results());
        notifyOutcome(response.results(), "Void completed for visible records.");
        search(false);
    }

    private void askDeleteAllScope() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Delete All Scope");
        RadioButtonGroup<String> mode = new RadioButtonGroup<>();
        mode.setItems("Visible records only", "Entire company records of this type");
        mode.setValue("Visible records only");
        Button cancel = new Button("Cancel", event -> dialog.close());
        Button next = new Button("Next", event -> {
            dialog.close();
            boolean includeAll = Objects.equals(mode.getValue(), "Entire company records of this type");
            confirmTwoStep("Delete all records in selected scope?", () -> runDeleteAll(includeAll));
        });
        next.addThemeName("primary");
        dialog.add(new VerticalLayout(new Paragraph("Choose the delete scope for this run."), mode));
        dialog.getFooter().add(cancel, next);
        dialog.open();
    }

    private void runDeleteAll(boolean includeAll) {
        if (includeAll) {
            search(true);
        } else if (currentRows.isEmpty()) {
            notifyWarning("Run Search first.");
            return;
        }
        QboCleanupService.CleanupActionResponse response = cleanupService.delete(entityType, currentRows);
        resultGrid.setItems(response.results());
        notifyOutcome(response.results(), "Delete-all completed.");
        search(false);
    }

    private void confirmTwoStep(String message, Runnable action) {
        Dialog first = new Dialog();
        first.setHeaderTitle("Confirmation Step 1");
        first.add(new Paragraph(message));
        Button cancel = new Button("Cancel", event -> first.close());
        Button continueButton = new Button("Continue", event -> {
            first.close();
            Dialog second = new Dialog();
            second.setHeaderTitle("Confirmation Step 2");
            second.add(new Paragraph("Final confirmation required. This action affects live QuickBooks data."));
            Button back = new Button("Back", backEvent -> second.close());
            Button confirm = new Button("Confirm", confirmEvent -> {
                second.close();
                action.run();
            });
            confirm.addThemeName("error");
            second.getFooter().add(back, confirm);
            second.open();
        });
        continueButton.addThemeName("primary");
        first.getFooter().add(cancel, continueButton);
        first.open();
    }

    private void notifyOutcome(List<QboCleanupResult> results, String successMessage) {
        long failures = results.stream().filter(result -> !result.success()).count();
        if (failures == 0) {
            notifySuccess(successMessage);
            return;
        }
        notifyWarning(successMessage + " Failures: " + failures + ".");
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
