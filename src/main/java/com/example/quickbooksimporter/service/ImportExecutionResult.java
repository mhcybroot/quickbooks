package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.persistence.ImportRunEntity;

public record ImportExecutionResult(ImportRunEntity importRun, boolean success, String message) {
}
