package com.example.quickbooksimporter.ui;

import com.example.quickbooksimporter.service.LegalUrlService;
import com.example.quickbooksimporter.ui.components.LegalLinks;
import com.example.quickbooksimporter.ui.components.UiComponents;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route("legal/privacy")
@PageTitle("Privacy Policy")
@AnonymousAllowed
public class LegalPrivacyView extends VerticalLayout {

    public LegalPrivacyView(LegalUrlService legalUrlService) {
        setSizeFull();
        addClassName("corp-page");

        Anchor loginLink = new Anchor("/login", "Back to Login");
        loginLink.getElement().setAttribute("router-ignore", true);

        add(
                new H1("Privacy Policy"),
                new Paragraph("Last updated: May 8, 2026"),
                new Paragraph("This Privacy Policy explains how " + legalUrlService.companyName()
                        + " collects, uses, stores, and protects information when you use the QuickBooks import application."),
                UiComponents.card(
                        new H3("Information We Process"),
                        new Paragraph("We process account access information, application login activity, uploaded CSV or import files, mapping selections, validation results, import history, and the QuickBooks company data you authorize through Intuit OAuth."),
                        new Paragraph("This may include customer, vendor, invoice, bill, payment, expense, and related accounting metadata necessary to validate and submit imports.")),
                UiComponents.card(
                        new H3("How We Use Information"),
                        new Paragraph("We use the information to authenticate users, connect to QuickBooks, validate imported files, create requested accounting records, retain import history, troubleshoot failures, and improve operational reliability and support.")),
                UiComponents.card(
                        new H3("Storage, Sharing, And Retention"),
                        new Paragraph("Import data and connection metadata may be stored in the application database for workflow continuity, auditability, and support. We do not sell your data."),
                        new Paragraph("Information is shared only with service providers and platforms necessary to operate the application, including Intuit QuickBooks, hosting infrastructure, and internal support personnel with a legitimate operational need.")),
                UiComponents.card(
                        new H3("Security And Your Choices"),
                        new Paragraph("We use reasonable administrative and technical safeguards to protect access tokens, application credentials, and stored import records, but no system can be guaranteed completely secure."),
                        new Paragraph("For privacy requests, deletion requests, or policy questions, contact " + legalUrlService.supportEmail() + ".")),
                LegalLinks.inline(legalUrlService),
                loginLink);
    }
}
