package com.example.quickbooksimporter.service;

import java.time.Instant;

public record QuickBooksConnectionStatus(
        boolean connected,
        String realmId,
        Instant connectedAt,
        Instant expiresAt,
        String environment) {
}
