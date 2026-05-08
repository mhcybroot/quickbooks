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

@Route("legal/eula")
@PageTitle("End-User License Agreement")
@AnonymousAllowed
public class LegalEulaView extends VerticalLayout {

    public LegalEulaView(LegalUrlService legalUrlService) {
        setSizeFull();
        addClassName("corp-page");

        Anchor loginLink = new Anchor("/login", "Back to Login");
        loginLink.getElement().setAttribute("router-ignore", true);

        add(
                new H1("End-User License Agreement"),
                new Paragraph("Last updated: May 8, 2026"),
                new Paragraph("This End-User License Agreement applies to " + legalUrlService.companyName()
                        + " and governs your use of the QuickBooks import application and related services."),
                UiComponents.card(
                        new H3("License And Permitted Use"),
                        new Paragraph("You are granted a limited, non-exclusive, revocable license to use the application for your internal business import, validation, reconciliation, and QuickBooks synchronization workflows."),
                        new Paragraph("You agree not to reverse engineer, misuse, overload, bypass security controls, or use the application in violation of law or Intuit platform requirements.")),
                UiComponents.card(
                        new H3("Your Data And QuickBooks Access"),
                        new Paragraph("You authorize the application to access the QuickBooks company data you explicitly connect through Intuit OAuth."),
                        new Paragraph("You remain responsible for the accuracy of uploaded files, mapping selections, approvals, and any resulting accounting records created in QuickBooks.")),
                UiComponents.card(
                        new H3("Service Scope, Warranty, And Liability"),
                        new Paragraph("The service is provided on an as-is and as-available basis without warranties of uninterrupted availability, merchantability, fitness for a particular purpose, or error-free operation."),
                        new Paragraph("To the maximum extent permitted by law, " + legalUrlService.companyName()
                                + " is not liable for indirect, incidental, special, consequential, or punitive damages, including data loss, accounting errors, or business interruption arising from use of the application.")),
                UiComponents.card(
                        new H3("Termination And Contact"),
                        new Paragraph("We may suspend or terminate access if the service is misused, if security is at risk, or if continued access would violate legal or platform obligations."),
                        new Paragraph("Support and legal contact: " + legalUrlService.supportEmail() + ".")),
                LegalLinks.inline(legalUrlService),
                loginLink);
    }
}
