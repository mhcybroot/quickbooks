package com.example.quickbooksimporter.service;

public record QboDependencyBlocker(
        String recordId,
        String externalNumber,
        String reason) {
}
