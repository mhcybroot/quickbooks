package com.example.quickbooksimporter.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.quickbooks")
public record QuickBooksProperties(
        String clientId,
        String clientSecret,
        String redirectUri,
        String baseUrl,
        String authorizationUrl,
        String tokenUrl,
        List<String> scopes,
        String environment,
        String serviceItemIncomeAccountId) {
}
