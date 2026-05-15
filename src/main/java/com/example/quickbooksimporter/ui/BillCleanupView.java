package com.example.quickbooksimporter.ui;

import com.example.quickbooksimporter.service.QboCleanupEntityType;
import com.example.quickbooksimporter.service.QboCleanupService;
import com.example.quickbooksimporter.service.AppJobService;
import com.example.quickbooksimporter.service.QuickBooksJobService;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route(value = "cleanup/bills", layout = MainLayout.class)
@PageTitle("Cleanup Bills")
@PermitAll
public class BillCleanupView extends QboCleanupPageBase {

    public BillCleanupView(QboCleanupService cleanupService,
                           QuickBooksJobService quickBooksJobService,
                           AppJobService appJobService) {
        super(cleanupService, quickBooksJobService, appJobService, QboCleanupEntityType.BILL, "Cleanup: Bills");
    }
}
