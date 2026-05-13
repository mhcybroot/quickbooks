package com.example.quickbooksimporter.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class QuickBooksCredentialValidator {
    private static final Logger log = LoggerFactory.getLogger(QuickBooksCredentialValidator.class);

    private final QuickBooksProperties properties;

    public QuickBooksCredentialValidator(QuickBooksProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void validateCredentials() {
        if (isBlank(properties.clientId()) || isBlank(properties.clientSecret())) {
            log.warn("Global QuickBooks credentials are not configured. Company-specific credentials must be configured for all active companies, otherwise fallback operations will fail.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
