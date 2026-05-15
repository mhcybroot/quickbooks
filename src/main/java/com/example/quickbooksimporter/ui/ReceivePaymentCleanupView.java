package com.example.quickbooksimporter.ui;

import com.example.quickbooksimporter.service.QboCleanupEntityType;
import com.example.quickbooksimporter.service.QboCleanupService;
import com.example.quickbooksimporter.service.AppJobService;
import com.example.quickbooksimporter.service.QuickBooksJobService;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route(value = "cleanup/receive-payments", layout = MainLayout.class)
@PageTitle("Cleanup Receive Payments")
@PermitAll
public class ReceivePaymentCleanupView extends QboCleanupPageBase {

    public ReceivePaymentCleanupView(QboCleanupService cleanupService,
                                     QuickBooksJobService quickBooksJobService,
                                     AppJobService appJobService) {
        super(cleanupService, quickBooksJobService, appJobService, QboCleanupEntityType.RECEIVE_PAYMENT, "Cleanup: Receive Payments");
    }
}
