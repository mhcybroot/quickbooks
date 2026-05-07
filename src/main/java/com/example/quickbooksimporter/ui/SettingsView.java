package com.example.quickbooksimporter.ui;

import com.example.quickbooksimporter.service.QuickBooksConnectionService;
import com.example.quickbooksimporter.service.QuickBooksConnectionStatus;
import com.example.quickbooksimporter.service.QuickBooksGateway;
import com.example.quickbooksimporter.service.QuickBooksIncomeAccount;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import java.util.List;

@Route(value = "settings", layout = MainLayout.class)
@PageTitle("QuickBooks Settings")
@PermitAll
public class SettingsView extends VerticalLayout {

    public SettingsView(QuickBooksConnectionService connectionService, QuickBooksGateway quickBooksGateway) {
        QuickBooksConnectionStatus status = connectionService.getStatus();
        setSpacing(true);
        add(new H2("QuickBooks Online Connection"));
        if (status.connected()) {
            add(new Paragraph("Connected to realm " + status.realmId()));
            add(new Paragraph("Environment: " + status.environment()));
            add(new Paragraph("Token expires at: " + status.expiresAt()));
        } else {
            add(new Paragraph("No QuickBooks company connected yet."));
        }

        Anchor connect = new Anchor("/oauth/quickbooks/connect", "");
        connect.getElement().setAttribute("router-ignore", true);
        connect.add(new Button("Connect QuickBooks"));
        add(connect, new Text("Use your Intuit app credentials in application properties or environment variables."));

        Grid<QuickBooksIncomeAccount> accountsGrid = new Grid<>(QuickBooksIncomeAccount.class, false);
        accountsGrid.addColumn(QuickBooksIncomeAccount::id).setHeader("Account ID").setAutoWidth(true);
        accountsGrid.addColumn(QuickBooksIncomeAccount::name).setHeader("Name").setAutoWidth(true).setFlexGrow(1);
        accountsGrid.addColumn(QuickBooksIncomeAccount::accountSubType).setHeader("Sub Type").setAutoWidth(true);
        accountsGrid.setHeight("320px");

        Button loadAccounts = new Button("Load Income Accounts", event -> {
            if (!status.connected()) {
                Notification.show("Connect QuickBooks first.");
                return;
            }
            try {
                List<QuickBooksIncomeAccount> accounts = quickBooksGateway.listIncomeAccounts(status.realmId());
                accountsGrid.setItems(accounts);
                Notification.show("Loaded " + accounts.size() + " income accounts. Copy the Account ID you want.");
            } catch (Exception exception) {
                Notification.show("Unable to load income accounts: " + exception.getMessage());
            }
        });

        add(new H3("Find QB_SERVICE_ITEM_INCOME_ACCOUNT_ID"),
                new Paragraph("Click Load Income Accounts, then copy an Account ID and set it in app.quickbooks.service-item-income-account-id."),
                loadAccounts,
                accountsGrid);
    }
}
