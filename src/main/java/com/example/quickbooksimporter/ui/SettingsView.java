package com.example.quickbooksimporter.ui;

import com.example.quickbooksimporter.service.QuickBooksConnectionService;
import com.example.quickbooksimporter.service.QuickBooksConnectionStatus;
import com.example.quickbooksimporter.service.QuickBooksGateway;
import com.example.quickbooksimporter.service.QuickBooksIncomeAccount;
import com.example.quickbooksimporter.service.LegalUrlService;
import com.example.quickbooksimporter.ui.components.LegalLinks;
import com.example.quickbooksimporter.ui.components.UiComponents;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import java.util.List;

@Route(value = "settings", layout = MainLayout.class)
@PageTitle("QuickBooks Settings")
@PermitAll
public class SettingsView extends VerticalLayout {

    public SettingsView(QuickBooksConnectionService connectionService,
                        QuickBooksGateway quickBooksGateway,
                        LegalUrlService legalUrlService) {
        QuickBooksConnectionStatus status = connectionService.getStatus();
        addClassName("corp-page");
        setSpacing(true);
        add(new H2("QuickBooks Online Connection"));

        Long companyId = status.companyId();
        String connectHref = companyId == null ? "#" : "/oauth/quickbooks/connect?companyId=" + companyId;
        Anchor connect = new Anchor(connectHref, "");
        connect.getElement().setAttribute("router-ignore", true);
        Button connectButton = new Button("Connect QuickBooks");
        connectButton.setEnabled(companyId != null);
        connect.add(connectButton);
        Button disconnect = new Button("Disconnect QuickBooks", event -> {
            try {
                if (status.companyId() == null) {
                    Notification.show("Select company first.");
                    return;
                }
                connectionService.disconnect(status.companyId());
                Notification.show("QuickBooks disconnected for company.");
                UI.getCurrent().getPage().reload();
            } catch (Exception exception) {
                Notification.show("Unable to disconnect: " + exception.getMessage());
            }
        });
        HorizontalLayout connectionRow = new HorizontalLayout(
                UiComponents.kpi("Connection", status.connected() ? "Active" : "Not Connected",
                        status.connected() ? "Realm " + status.realmId() : "Connect your QuickBooks sandbox/company"),
                UiComponents.kpi("Company", status.companyName() == null ? "-" : status.companyName(),
                        status.companyId() == null ? "No company selected" : "Company ID " + status.companyId()),
                UiComponents.kpi("Credential Source", status.credentialSource() == null ? "UNKNOWN" : status.credentialSource(),
                        status.clientIdHint() == null || status.clientIdHint().isBlank() ? "Client not available" : "Client " + status.clientIdHint()),
                UiComponents.kpi("Environment", status.environment().toUpperCase(),
                        status.expiresAt() == null ? "Token unavailable" : "Token expires at " + status.expiresAt()));
        connectionRow.setWidthFull();
        connectionRow.setFlexGrow(1);
        add(connectionRow, new HorizontalLayout(connect, disconnect), new Text("Use your Intuit app credentials in application properties or environment variables."));

        Paragraph legalSummary = new Paragraph("Production QuickBooks submission URLs:");
        legalSummary.addClassName("corp-muted");
        Anchor eulaUrl = new Anchor(legalUrlService.eulaUrl(), legalUrlService.eulaUrl());
        eulaUrl.setTarget("_blank");
        eulaUrl.addClassName("legal-url-link");
        Anchor privacyUrl = new Anchor(legalUrlService.privacyUrl(), legalUrlService.privacyUrl());
        privacyUrl.setTarget("_blank");
        privacyUrl.addClassName("legal-url-link");
        add(UiComponents.card(
                new H3("Legal URLs"),
                legalSummary,
                new Paragraph("End-user license agreement URL"),
                eulaUrl,
                new Paragraph("Privacy policy URL"),
                privacyUrl,
                LegalLinks.inline(legalUrlService)));

        Grid<QuickBooksIncomeAccount> accountsGrid = new Grid<>(QuickBooksIncomeAccount.class, false);
        accountsGrid.addColumn(QuickBooksIncomeAccount::id).setHeader("Account ID").setAutoWidth(true);
        accountsGrid.addColumn(QuickBooksIncomeAccount::name).setHeader("Name").setAutoWidth(true).setFlexGrow(1);
        accountsGrid.addColumn(QuickBooksIncomeAccount::accountSubType).setHeader("Sub Type").setAutoWidth(true);
        accountsGrid.setHeight("320px");
        accountsGrid.addClassName("corp-grid");

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
        loadAccounts.addThemeName("primary");

        add(UiComponents.card(new H3("Find QB_SERVICE_ITEM_INCOME_ACCOUNT_ID"),
                new Paragraph("Click Load Income Accounts, then copy an Account ID and set it in app.quickbooks.service-item-income-account-id."),
                loadAccounts,
                accountsGrid));
    }
}
