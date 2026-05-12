package com.example.quickbooksimporter.service;

public record QuickBooksReconcileMarkResult(
        boolean success,
        String message,
        String intuitTid) {
}
