package com.example.quickbooksimporter.service;

import java.util.List;

public record SettingsIncomeAccountsJobResult(
        List<QuickBooksIncomeAccount> accounts) {
}
