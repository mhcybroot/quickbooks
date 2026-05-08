package com.example.quickbooksimporter.ui;

import com.example.quickbooksimporter.ui.components.UiComponents;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route(value = "cleanup", layout = MainLayout.class)
@PageTitle("QBO Cleanup Hub")
@PermitAll
public class QboCleanupHubView extends VerticalLayout {

    public QboCleanupHubView() {
        addClassName("corp-page");
        setSizeFull();
        add(new H2("QuickBooks Cleanup Console"),
                new Paragraph("Delete or void existing live QuickBooks transactions by type. This works on QBO company data, not import history."));
        add(gridRow(
                navButton("Invoices", "cleanup/invoices"),
                navButton("Sales Receipts", "cleanup/sales-receipts"),
                navButton("Bills", "cleanup/bills")));
        add(gridRow(
                navButton("Bill Payments", "cleanup/bill-payments"),
                navButton("Receive Payments", "cleanup/receive-payments"),
                navButton("Expenses", "cleanup/expenses")));
    }

    private HorizontalLayout gridRow(Button... buttons) {
        HorizontalLayout row = new HorizontalLayout();
        row.setWidthFull();
        row.setSpacing(true);
        for (Button button : buttons) {
            VerticalLayout card = UiComponents.card(button);
            card.setWidthFull();
            row.add(card);
            row.setFlexGrow(1, card);
        }
        return row;
    }

    private Button navButton(String label, String route) {
        Button button = new Button(label, event -> getUI().ifPresent(ui -> ui.navigate(route)));
        button.addThemeName("primary");
        button.setWidthFull();
        return button;
    }
}
