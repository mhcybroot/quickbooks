package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.CompanyRole;
import com.example.quickbooksimporter.domain.CompanyStatus;
import com.example.quickbooksimporter.persistence.CompanyEntity;
import com.example.quickbooksimporter.persistence.CompanyMembershipEntity;
import com.example.quickbooksimporter.repository.CompanyMembershipRepository;
import com.example.quickbooksimporter.repository.CompanyRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyAdminService {

    private final CompanyRepository companyRepository;
    private final CompanyMembershipRepository membershipRepository;
    private final AuditLogService auditLogService;

    public CompanyAdminService(CompanyRepository companyRepository,
                               CompanyMembershipRepository membershipRepository,
                               AuditLogService auditLogService) {
        this.companyRepository = companyRepository;
        this.membershipRepository = membershipRepository;
        this.auditLogService = auditLogService;
    }

    public List<CompanyEntity> listAll() {
        return companyRepository.findAll();
    }

    @Transactional
    public CompanyEntity create(String name, String code) {
        if (companyRepository.findByCode(code).isPresent()) {
            throw new IllegalArgumentException("Company code already exists");
        }
        Instant now = Instant.now();
        CompanyEntity company = new CompanyEntity();
        company.setName(name);
        company.setCode(code);
        company.setStatus(CompanyStatus.ACTIVE);
        company.setCreatedAt(now);
        company.setUpdatedAt(now);
        CompanyEntity saved = companyRepository.save(company);
        auditLogService.log("COMPANY_CREATE", saved, "Created company " + name + " (" + code + ")");
        return saved;
    }

    @Transactional
    public CompanyEntity update(Long companyId, String name, String code, CompanyStatus status) {
        CompanyEntity company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));
        if (!company.getCode().equals(code) && companyRepository.findByCode(code).isPresent()) {
            throw new IllegalArgumentException("Company code already exists");
        }
        company.setName(name);
        company.setCode(code);
        company.setStatus(status);
        company.setUpdatedAt(Instant.now());
        CompanyEntity saved = companyRepository.save(company);
        auditLogService.log("COMPANY_UPDATE", saved, "Updated company " + name + " (" + code + ")");
        return saved;
    }

    @Transactional
    public CompanyEntity archive(Long companyId) {
        CompanyEntity company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));
        long ownerCount = membershipRepository.countByCompanyIdAndRole(companyId, CompanyRole.OWNER);
        if (ownerCount <= 0) {
            throw new IllegalStateException("Cannot archive company without owner membership");
        }
        company.setStatus(CompanyStatus.ARCHIVED);
        company.setUpdatedAt(Instant.now());
        CompanyEntity saved = companyRepository.save(company);
        auditLogService.log("COMPANY_ARCHIVE", saved, "Archived company " + company.getName());
        return saved;
    }

    @Transactional
    public CompanyMembershipEntity upsertMembership(Long companyId, Long userId, CompanyRole role,
                                                     com.example.quickbooksimporter.repository.AppUserRepository appUserRepository) {
        CompanyEntity company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));
        var user = appUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Instant now = Instant.now();
        CompanyMembershipEntity membership = membershipRepository.findByUserUsernameAndCompanyId(user.getUsername(), companyId)
                .orElseGet(CompanyMembershipEntity::new);
        membership.setUser(user);
        membership.setCompany(company);
        membership.setRole(role);
        membership.setUpdatedAt(now);
        if (membership.getCreatedAt() == null) {
            membership.setCreatedAt(now);
        }
        CompanyMembershipEntity saved = membershipRepository.save(membership);
        auditLogService.log("MEMBERSHIP_UPSERT", company, "Set membership for user " + user.getUsername() + " role " + role);
        return saved;
    }
}
