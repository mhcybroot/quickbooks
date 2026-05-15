package com.example.quickbooksimporter.service;

public record EtaEstimate(
        double throughputRowsPerSecond,
        Long remainingSeconds,
        boolean live,
        boolean historical,
        boolean estimating,
        String remainingLabel,
        String throughputLabel) {
}
