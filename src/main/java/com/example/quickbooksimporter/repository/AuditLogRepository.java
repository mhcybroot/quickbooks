package com.example.quickbooksimporter.repository;

import com.example.quickbooksimporter.persistence.AuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {
}
