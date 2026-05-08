package com.example.quickbooksimporter.service;

public record QboDependencyLink(
        String parentId,
        QboCleanupEntityType parentType,
        String childId,
        QboCleanupEntityType childType,
        String reason) {
}
