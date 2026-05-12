package com.example.quickbooksimporter.repository;

import com.example.quickbooksimporter.persistence.ReconciliationSessionRowEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationSessionRowRepository extends JpaRepository<ReconciliationSessionRowEntity, Long> {
    List<ReconciliationSessionRowEntity> findBySessionIdOrderByBankRowNumberAsc(Long sessionId);
}
