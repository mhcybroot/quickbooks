package com.example.quickbooksimporter.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route("access-required")
@PageTitle("Access Required")
@PermitAll
public class AccessRequiredView extends VerticalLayout {

    public AccessRequiredView() {
        addClassName("corp-page");
        add(new H2("No Company Access Assigned"));
        add(new Paragraph("Your account is active but has no accessible company memberships. Ask a platform administrator to assign you to at least one active company."));
        Button logout = new Button("Sign Out", event -> getUI().ifPresent(ui -> ui.getPage().setLocation("/logout")));
        add(logout);
    }
}
