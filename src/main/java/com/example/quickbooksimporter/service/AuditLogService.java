package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.persistence.AuditLogEntity;
import com.example.quickbooksimporter.persistence.CompanyEntity;
import com.example.quickbooksimporter.repository.AuditLogRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final CurrentUserService currentUserService;

    public AuditLogService(AuditLogRepository auditLogRepository,
                           CurrentUserService currentUserService) {
        this.auditLogRepository = auditLogRepository;
        this.currentUserService = currentUserService;
    }

    public void log(String actionType, CompanyEntity company, String summary) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setActionType(actionType);
        entity.setCompany(company);
        entity.setSummary(summary);
        entity.setCreatedAt(Instant.now());
        try {
            entity.setActorUser(currentUserService.requireUser());
        } catch (Exception ignored) {
            entity.setActorUser(null);
        }
        auditLogRepository.save(entity);
    }
}
