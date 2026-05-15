package com.example.quickbooksimporter.ui;

import com.example.quickbooksimporter.service.QboCleanupEntityType;
import com.example.quickbooksimporter.service.QboCleanupService;
import com.example.quickbooksimporter.service.AppJobService;
import com.example.quickbooksimporter.service.QuickBooksJobService;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route(value = "cleanup/bill-payments", layout = MainLayout.class)
@PageTitle("Cleanup Bill Payments")
@PermitAll
public class BillPaymentCleanupView extends QboCleanupPageBase {

    public BillPaymentCleanupView(QboCleanupService cleanupService,
                                  QuickBooksJobService quickBooksJobService,
                                  AppJobService appJobService) {
        super(cleanupService, quickBooksJobService, appJobService, QboCleanupEntityType.BILL_PAYMENT, "Cleanup: Bill Payments");
    }
}
