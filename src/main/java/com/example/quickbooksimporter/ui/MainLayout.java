package com.example.quickbooksimporter.ui;

import com.example.quickbooksimporter.service.QuickBooksConnectionService;
import com.example.quickbooksimporter.service.QuickBooksConnectionStatus;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.PermitAll;

@PermitAll
public class MainLayout extends AppLayout {

    public MainLayout(QuickBooksConnectionService connectionService) {
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
        nav.addItem(new SideNavItem("Invoice Import", InvoiceImportView.class, VaadinIcon.UPLOAD.create()));
        nav.addItem(new SideNavItem("Receive Payments", PaymentImportView.class, VaadinIcon.MONEY_DEPOSIT.create()));
        nav.addItem(new SideNavItem("QuickBooks Settings", SettingsView.class, VaadinIcon.COG.create()));
        nav.addItem(new SideNavItem("Import History", ImportHistoryView.class, VaadinIcon.CHART.create()));
        addToDrawer(new Scroller(nav));
    }
}
