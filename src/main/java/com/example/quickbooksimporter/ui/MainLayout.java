package com.example.quickbooksimporter.ui;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.PermitAll;

@PermitAll
public class MainLayout extends AppLayout {

    public MainLayout() {
        DrawerToggle toggle = new DrawerToggle();
        H1 title = new H1("QuickBooks Importer");
        title.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.Margin.NONE);
        HorizontalLayout header = new HorizontalLayout(toggle, title);
        header.addClassNames(LumoUtility.Display.FLEX, LumoUtility.AlignItems.CENTER, LumoUtility.Gap.MEDIUM,
                LumoUtility.Padding.MEDIUM);
        header.setWidthFull();
        addToNavbar(header);

        SideNav nav = new SideNav();
        nav.addItem(new SideNavItem("Invoice Import", InvoiceImportView.class));
        nav.addItem(new SideNavItem("QuickBooks Settings", SettingsView.class));
        nav.addItem(new SideNavItem("Import History", ImportHistoryView.class));
        addToDrawer(new Scroller(nav));
    }
}
