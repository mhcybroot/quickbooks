package com.example.quickbooksimporter.ui;

import com.example.quickbooksimporter.service.QboCleanupEntityType;
import com.example.quickbooksimporter.service.QboCleanupService;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route(value = "cleanup/bill-payments", layout = MainLayout.class)
@PageTitle("Cleanup Bill Payments")
@PermitAll
public class BillPaymentCleanupView extends QboCleanupPageBase {

    public BillPaymentCleanupView(QboCleanupService cleanupService) {
        super(cleanupService, QboCleanupEntityType.BILL_PAYMENT, "Cleanup: Bill Payments");
    }
}
