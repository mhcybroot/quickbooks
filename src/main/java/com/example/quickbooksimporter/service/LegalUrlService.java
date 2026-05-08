package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.config.AppPublicProperties;
import org.springframework.stereotype.Service;

@Service
public class LegalUrlService {

    private final AppPublicProperties properties;

    public LegalUrlService(AppPublicProperties properties) {
        this.properties = properties;
    }

    public String eulaUrl() {
        return join("/legal/eula");
    }

    public String privacyUrl() {
        return join("/legal/privacy");
    }

    public String companyName() {
        return blankToFallback(properties.companyName(), "QuickBooks Importer");
    }

    public String supportEmail() {
        return blankToFallback(properties.supportEmail(), "support@example.com");
    }

    private String join(String path) {
        String baseUrl = blankToFallback(properties.baseUrl(), "https://example.com");
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) + path : baseUrl + path;
    }

    private String blankToFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
