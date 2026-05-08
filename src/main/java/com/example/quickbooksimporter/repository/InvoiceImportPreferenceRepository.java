package com.example.quickbooksimporter.repository;

import com.example.quickbooksimporter.persistence.InvoiceImportPreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceImportPreferenceRepository extends JpaRepository<InvoiceImportPreferenceEntity, Long> {
}
