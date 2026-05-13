package com.example.quickbooksimporter.service;

import java.time.Instant;

public record QuickBooksConnectionStatus(
        boolean connected,
        Long companyId,
        String companyName,
        String realmId,
        Instant connectedAt,
        Instant expiresAt,
        String environment,
        String credentialSource,
        String clientIdHint) {
}
