package com.example.quickbooksimporter.ui;

import com.example.quickbooksimporter.service.QboCleanupEntityType;
import com.example.quickbooksimporter.service.QboCleanupService;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

@Route(value = "cleanup/expenses", layout = MainLayout.class)
@PageTitle("Cleanup Expenses")
@PermitAll
public class ExpenseCleanupView extends QboCleanupPageBase {

    public ExpenseCleanupView(QboCleanupService cleanupService) {
        super(cleanupService, QboCleanupEntityType.EXPENSE, "Cleanup: Expenses");
    }
}
