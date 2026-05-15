package com.example.quickbooksimporter.service;

@FunctionalInterface
public interface PreviewProgressListener {

    void onProgress(int completedUnits, int totalUnits, String summaryMessage);

    static PreviewProgressListener noop() {
        return (completedUnits, totalUnits, summaryMessage) -> {
        };
    }
}
