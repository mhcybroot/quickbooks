package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.persistence.InvoiceImportPreferenceEntity;
import com.example.quickbooksimporter.repository.InvoiceImportPreferenceRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceGroupingPreferenceService {

    private final InvoiceImportPreferenceRepository repository;

    public InvoiceGroupingPreferenceService(InvoiceImportPreferenceRepository repository) {
        this.repository = repository;
    }

    public boolean isGroupingEnabled() {
        return repository.findById(InvoiceImportPreferenceEntity.SINGLETON_ID)
                .map(InvoiceImportPreferenceEntity::isGroupingEnabled)
                .orElse(false);
    }

    @Transactional
    public void saveGroupingEnabled(boolean groupingEnabled) {
        InvoiceImportPreferenceEntity entity = repository.findById(InvoiceImportPreferenceEntity.SINGLETON_ID)
                .orElseGet(() -> {
                    InvoiceImportPreferenceEntity created = new InvoiceImportPreferenceEntity();
                    created.setId(InvoiceImportPreferenceEntity.SINGLETON_ID);
                    return created;
                });
        entity.setGroupingEnabled(groupingEnabled);
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);
    }
}
