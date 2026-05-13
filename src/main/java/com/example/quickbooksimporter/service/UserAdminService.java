package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.CompanyRole;
import com.example.quickbooksimporter.domain.CompanyStatus;
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
    private final CurrentUserService currentUserService;

    public UserAdminService(AppUserRepository appUserRepository,
                            CompanyMembershipRepository membershipRepository,
                            CompanyRepository companyRepository,
                            PasswordEncoder passwordEncoder,
                            AuditLogService auditLogService,
                            CurrentUserService currentUserService) {
        this.appUserRepository = appUserRepository;
        this.membershipRepository = membershipRepository;
        this.companyRepository = companyRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
        this.currentUserService = currentUserService;
    }

    public List<AppUserEntity> listUsers() {
        return appUserRepository.findAll();
    }

    @Transactional
    public AppUserEntity createUser(String username,
                                    String rawPassword,
                                    PlatformRole platformRole,
                                    List<MembershipAssignment> memberships) {
        if (appUserRepository.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }
        PlatformRole role = platformRole == null ? PlatformRole.USER : platformRole;
        if (role != PlatformRole.PLATFORM_ADMIN && (memberships == null || memberships.isEmpty())) {
            throw new IllegalArgumentException("At least one company membership is required for non-admin users");
        }

        Instant now = Instant.now();
        AppUserEntity user = new AppUserEntity();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setPlatformRole(role);
        user.setActive(true);
        user.setBlocked(false);
        user.setMustChangePassword(false);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        AppUserEntity saved = appUserRepository.save(user);

        if (memberships != null) {
            for (MembershipAssignment assignment : memberships) {
                setMembership(saved.getId(), assignment.companyId(), assignment.role());
            }
        }
        auditLogService.log("USER_CREATE", null, "Created user " + username);
        return saved;
    }

    @Transactional
    public AppUserEntity updateUser(Long userId, PlatformRole platformRole, boolean active) {
        AppUserEntity user = appUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (user.getPlatformRole() == PlatformRole.PLATFORM_ADMIN && !active
                && appUserRepository.countByActiveTrueAndBlockedFalseAndPlatformRole(PlatformRole.PLATFORM_ADMIN) <= 1) {
            throw new IllegalStateException("Cannot deactivate last active platform admin");
        }
        if (platformRole != null) {
            user.setPlatformRole(platformRole);
        }
        if (!active) {
            long memberships = membershipRepository.countByUserId(userId);
            if (memberships > 0) {
                enforceNotLastOwnerForUser(userId);
                membershipRepository.deleteByUserId(userId);
            }
            user.setBlocked(false);
            user.setBlockedAt(null);
            user.setBlockedByUserId(null);
            user.setBlockedReason(null);
        }
        user.setActive(active);
        user.setUpdatedAt(Instant.now());
        AppUserEntity saved = appUserRepository.save(user);
        auditLogService.log("USER_UPDATE", null, "Updated user " + user.getUsername());
        return saved;
    }

    @Transactional
    public void softDeleteUser(Long userId) {
        AppUserEntity user = appUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!user.isActive()) {
            return;
        }
        updateUser(userId, user.getPlatformRole(), false);
        auditLogService.log("USER_SOFT_DELETE", null, "Soft-deleted user " + user.getUsername());
    }

    @Transactional
    public AppUserEntity blockUser(Long userId, String reason) {
        AppUserEntity user = appUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setBlocked(true);
        user.setBlockedReason((reason == null || reason.isBlank()) ? "Blocked by admin" : reason.trim());
        user.setBlockedAt(Instant.now());
        user.setBlockedByUserId(currentUserService.requireUser().getId());
        user.setUpdatedAt(Instant.now());
        AppUserEntity saved = appUserRepository.save(user);
        auditLogService.log("USER_BLOCK", null, "Blocked user " + user.getUsername());
        return saved;
    }

    @Transactional
    public AppUserEntity unblockUser(Long userId) {
        AppUserEntity user = appUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setBlocked(false);
        user.setBlockedReason(null);
        user.setBlockedAt(null);
        user.setBlockedByUserId(null);
        user.setUpdatedAt(Instant.now());
        AppUserEntity saved = appUserRepository.save(user);
        auditLogService.log("USER_UNBLOCK", null, "Unblocked user " + user.getUsername());
        return saved;
    }

    @Transactional
    public AppUserEntity adminResetPassword(Long userId, String temporaryPassword) {
        if (temporaryPassword == null || temporaryPassword.isBlank()) {
            throw new IllegalArgumentException("Temporary password is required");
        }
        AppUserEntity user = appUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        user.setMustChangePassword(true);
        user.setUpdatedAt(Instant.now());
        AppUserEntity saved = appUserRepository.save(user);
        auditLogService.log("USER_ADMIN_PASSWORD_RESET", null, "Reset password for user " + user.getUsername());
        return saved;
    }

    @Transactional
    public void changeOwnPassword(String currentPassword, String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("New password is required");
        }
        AppUserEntity user = currentUserService.requireUser();
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        user.setUpdatedAt(Instant.now());
        appUserRepository.save(user);
        auditLogService.log("USER_PASSWORD_CHANGED", null, "User changed own password " + user.getUsername());
    }

    @Transactional
    public void setMembership(Long userId, Long companyId, CompanyRole role) {
        if (role == null) {
            throw new IllegalArgumentException("Company role is required");
        }
        var user = appUserRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        var company = companyRepository.findById(companyId).orElseThrow(() -> new IllegalArgumentException("Company not found"));
        if (company.getStatus() != CompanyStatus.ACTIVE) {
            throw new IllegalStateException("Cannot assign membership to inactive company");
        }
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

    private void enforceNotLastOwnerForUser(Long userId) {
        List<CompanyMembershipEntity> memberships = membershipRepository.findByUserId(userId);
        for (CompanyMembershipEntity membership : memberships) {
            if (membership.getRole() == CompanyRole.OWNER
                    && membershipRepository.countByCompanyIdAndRole(membership.getCompany().getId(), CompanyRole.OWNER) <= 1) {
                throw new IllegalStateException("Cannot deactivate/delete user because they are the last owner of company "
                        + membership.getCompany().getName());
            }
        }
    }

    public record MembershipAssignment(Long companyId, CompanyRole role) {
    }
}
