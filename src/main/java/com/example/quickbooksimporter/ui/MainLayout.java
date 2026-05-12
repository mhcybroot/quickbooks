package com.example.quickbooksimporter.ui;

import com.example.quickbooksimporter.service.QuickBooksConnectionService;
import com.example.quickbooksimporter.service.QuickBooksConnectionStatus;
import com.example.quickbooksimporter.service.LegalUrlService;
import com.example.quickbooksimporter.ui.components.LegalLinks;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.PermitAll;

@PermitAll
public class MainLayout extends AppLayout {

    public MainLayout(QuickBooksConnectionService connectionService, LegalUrlService legalUrlService) {
        QuickBooksConnectionStatus status = connectionService.getStatus();
        DrawerToggle toggle = new DrawerToggle();
        H1 title = new H1("QuickBooks Importer");
        title.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.Margin.NONE, LumoUtility.FontWeight.SEMIBOLD);

        Span envBadge = new Span(status.environment().toUpperCase());
        envBadge.addClassNames("corp-badge", "corp-badge-gold");

        Anchor logout = new Anchor("/logout", "");
        logout.getElement().setAttribute("router-ignore", true);
        logout.add(new Button("Sign out", VaadinIcon.SIGN_OUT.create()));

        HorizontalLayout header = new HorizontalLayout(toggle, title, envBadge, logout);
        header.addClassNames(LumoUtility.Display.FLEX, LumoUtility.AlignItems.CENTER, LumoUtility.Gap.MEDIUM,
                LumoUtility.Padding.MEDIUM);
        header.expand(title);
        header.setWidthFull();
        header.addClassName("app-shell-header");
        addToNavbar(header);

        SideNav nav = new SideNav();
        nav.addItem(new SideNavItem("Import Workspace", ImportWorkspaceView.class, VaadinIcon.DASHBOARD.create()));
        nav.addItem(new SideNavItem("Batch Import", BatchImportView.class, VaadinIcon.FOLDER_OPEN.create()));
        nav.addItem(new SideNavItem("Invoice Import", InvoiceImportView.class, VaadinIcon.UPLOAD.create()));
        nav.addItem(new SideNavItem("Sales Receipt Import", SalesReceiptImportView.class, VaadinIcon.CART.create()));
        nav.addItem(new SideNavItem("Bill Import", BillImportView.class, VaadinIcon.FILE_TEXT.create()));
        nav.addItem(new SideNavItem("Bill Payments", BillPaymentImportView.class, VaadinIcon.WALLET.create()));
        nav.addItem(new SideNavItem("Receive Payments", PaymentImportView.class, VaadinIcon.MONEY_DEPOSIT.create()));
        nav.addItem(new SideNavItem("Expense Import", ExpenseImportView.class, VaadinIcon.CREDIT_CARD.create()));
        nav.addItem(new SideNavItem("Bank Reconciliation", ReconciliationView.class, VaadinIcon.CONNECT_O.create()));
        nav.addItem(new SideNavItem("CSV Compare", CsvCompareView.class, VaadinIcon.TABLE.create()));
        nav.addItem(new SideNavItem("Cleanup Hub", QboCleanupHubView.class, VaadinIcon.TRASH.create()));
        nav.addItem(new SideNavItem("Cleanup Invoices", InvoiceCleanupView.class, VaadinIcon.ARCHIVE.create()));
        nav.addItem(new SideNavItem("Cleanup Sales Receipts", SalesReceiptCleanupView.class, VaadinIcon.CART_O.create()));
        nav.addItem(new SideNavItem("Cleanup Bills", BillCleanupView.class, VaadinIcon.FILE_TREE_SUB.create()));
        nav.addItem(new SideNavItem("Cleanup Bill Payments", BillPaymentCleanupView.class, VaadinIcon.MONEY_EXCHANGE.create()));
        nav.addItem(new SideNavItem("Cleanup Receive Payments", ReceivePaymentCleanupView.class, VaadinIcon.MONEY.create()));
        nav.addItem(new SideNavItem("Cleanup Expenses", ExpenseCleanupView.class, VaadinIcon.BACKWARDS.create()));
        nav.addItem(new SideNavItem("QuickBooks Settings", SettingsView.class, VaadinIcon.COG.create()));
        nav.addItem(new SideNavItem("Import History", ImportHistoryView.class, VaadinIcon.CHART.create()));
        Scroller navScroller = new Scroller(nav);
        VerticalLayout drawer = new VerticalLayout(navScroller, LegalLinks.inline(legalUrlService));
        drawer.setSizeFull();
        drawer.expand(navScroller);
        drawer.setPadding(true);
        drawer.setSpacing(true);
        addToDrawer(drawer);
    }
}
