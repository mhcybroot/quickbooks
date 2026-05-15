package com.example.quickbooksimporter.ui;

import com.example.quickbooksimporter.service.QboCleanupEntityType;
import com.example.quickbooksimporter.service.QboCleanupService;
import com.example.quickbooksimporter.service.AppJobService;
import com.example.quickbooksimporter.service.QuickBooksJobService;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route(value = "cleanup/sales-receipts", layout = MainLayout.class)
@PageTitle("Cleanup Sales Receipts")
@PermitAll
public class SalesReceiptCleanupView extends QboCleanupPageBase {

    public SalesReceiptCleanupView(QboCleanupService cleanupService,
                                   QuickBooksJobService quickBooksJobService,
                                   AppJobService appJobService) {
        super(cleanupService, quickBooksJobService, appJobService, QboCleanupEntityType.SALES_RECEIPT, "Cleanup: Sales Receipts");
    }
}
