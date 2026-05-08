package com.example.quickbooksimporter.ui;

import com.example.quickbooksimporter.service.QboCleanupEntityType;
import com.example.quickbooksimporter.service.QboCleanupService;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route(value = "cleanup/invoices", layout = MainLayout.class)
@PageTitle("Cleanup Invoices")
@PermitAll
public class InvoiceCleanupView extends QboCleanupPageBase {

    public InvoiceCleanupView(QboCleanupService cleanupService) {
        super(cleanupService, QboCleanupEntityType.INVOICE, "Cleanup: Invoices");
    }
}
