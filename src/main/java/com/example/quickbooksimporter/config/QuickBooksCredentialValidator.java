package com.example.quickbooksimporter.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class QuickBooksCredentialValidator {

    private final QuickBooksProperties properties;

    public QuickBooksCredentialValidator(QuickBooksProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void validateCredentials() {
        if (isBlank(properties.clientId()) || isBlank(properties.clientSecret())) {
            throw new IllegalStateException(
                    "QuickBooks credentials are not configured. Set QB_CLIENT_ID and QB_CLIENT_SECRET as secure environment variables.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
