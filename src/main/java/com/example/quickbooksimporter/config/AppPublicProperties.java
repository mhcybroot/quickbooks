package com.example.quickbooksimporter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.public")
public record AppPublicProperties(
        String baseUrl,
        String companyName,
        String supportEmail) {
}
