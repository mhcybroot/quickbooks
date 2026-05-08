package com.example.quickbooksimporter.ui;

import com.example.quickbooksimporter.service.QboCleanupEntityType;
import com.example.quickbooksimporter.service.QboCleanupService;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route(value = "cleanup/sales-receipts", layout = MainLayout.class)
@PageTitle("Cleanup Sales Receipts")
@PermitAll
public class SalesReceiptCleanupView extends QboCleanupPageBase {

    public SalesReceiptCleanupView(QboCleanupService cleanupService) {
        super(cleanupService, QboCleanupEntityType.SALES_RECEIPT, "Cleanup: Sales Receipts");
    }
}
