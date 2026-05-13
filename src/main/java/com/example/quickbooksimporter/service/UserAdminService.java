package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.CompanyRole;
import com.example.quickbooksimporter.domain.PlatformRole;
import com.example.quickbooksimporter.persistence.AppUserEntity;
import com.example.quickbooksimporter.persistence.CompanyMembershipEntity;
import com.example.quickbooksimporter.repository.AppUserRepository;
import com.example.quickbooksimporter.repository.CompanyMembershipRepository;
import com.example.quickbooksimporter.repository.CompanyRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAdminService {

    private final AppUserRepository appUserRepository;
    private final CompanyMembershipRepository membershipRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public UserAdminService(AppUserRepository appUserRepository,
                            CompanyMembershipRepository membershipRepository,
                            CompanyRepository companyRepository,
                            PasswordEncoder passwordEncoder,
                            AuditLogService auditLogService) {
        this.appUserRepository = appUserRepository;
        this.membershipRepository = membershipRepository;
        this.companyRepository = companyRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    public List<AppUserEntity> listUsers() {
        return appUserRepository.findAll();
    }

    @Transactional
    public AppUserEntity createUser(String username, String rawPassword, PlatformRole platformRole) {
        if (appUserRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }
        Instant now = Instant.now();
        AppUserEntity user = new AppUserEntity();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setPlatformRole(platformRole == null ? PlatformRole.USER : platformRole);
        user.setActive(true);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        AppUserEntity saved = appUserRepository.save(user);
        auditLogService.log("USER_CREATE", null, "Created user " + username);
        return saved;
    }

    @Transactional
    public AppUserEntity updateUser(Long userId, PlatformRole platformRole, boolean active, String rawPasswordOrNull) {
        AppUserEntity user = appUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (user.getPlatformRole() == PlatformRole.PLATFORM_ADMIN && !active
                && appUserRepository.countByActiveTrueAndPlatformRole(PlatformRole.PLATFORM_ADMIN) <= 1) {
            throw new IllegalStateException("Cannot deactivate last platform admin");
        }
        if (platformRole != null) {
            user.setPlatformRole(platformRole);
        }
        user.setActive(active);
        if (rawPasswordOrNull != null && !rawPasswordOrNull.isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(rawPasswordOrNull));
        }
        user.setUpdatedAt(Instant.now());
        AppUserEntity saved = appUserRepository.save(user);
        auditLogService.log("USER_UPDATE", null, "Updated user " + user.getUsername());
        return saved;
    }

    @Transactional
    public void setMembership(Long userId, Long companyId, CompanyRole role) {
        var user = appUserRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        var company = companyRepository.findById(companyId).orElseThrow(() -> new IllegalArgumentException("Company not found"));
        var existing = membershipRepository.findByUserUsernameAndCompanyId(user.getUsername(), companyId);
        Instant now = Instant.now();
        CompanyMembershipEntity membership = existing.orElseGet(CompanyMembershipEntity::new);
        membership.setUser(user);
        membership.setCompany(company);
        membership.setRole(role);
        membership.setUpdatedAt(now);
        if (membership.getCreatedAt() == null) {
            membership.setCreatedAt(now);
        }
        membershipRepository.save(membership);
        auditLogService.log("MEMBERSHIP_SET", company, "Assigned " + user.getUsername() + " as " + role);
    }

    @Transactional
    public void removeMembership(Long userId, Long companyId) {
        var user = appUserRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        CompanyMembershipEntity membership = membershipRepository.findByUserUsernameAndCompanyId(user.getUsername(), companyId)
                .orElseThrow(() -> new IllegalArgumentException("Membership not found"));
        if (membership.getRole() == CompanyRole.OWNER
                && membershipRepository.countByCompanyIdAndRole(companyId, CompanyRole.OWNER) <= 1) {
            throw new IllegalStateException("Cannot remove last company owner");
        }
        membershipRepository.delete(membership);
        auditLogService.log("MEMBERSHIP_REMOVE", membership.getCompany(), "Removed " + user.getUsername() + " membership");
    }
}
