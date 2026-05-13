package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.config.AppSecurityProperties;
import com.example.quickbooksimporter.domain.CompanyRole;
import com.example.quickbooksimporter.domain.CompanyStatus;
import com.example.quickbooksimporter.domain.PlatformRole;
import com.example.quickbooksimporter.persistence.AppUserEntity;
import com.example.quickbooksimporter.persistence.CompanyEntity;
import com.example.quickbooksimporter.persistence.CompanyMembershipEntity;
import com.example.quickbooksimporter.repository.AppUserRepository;
import com.example.quickbooksimporter.repository.CompanyMembershipRepository;
import com.example.quickbooksimporter.repository.CompanyRepository;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SecurityBootstrapService {

    private final AppUserRepository appUserRepository;
    private final CompanyRepository companyRepository;
    private final CompanyMembershipRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppSecurityProperties appSecurityProperties;

    public SecurityBootstrapService(AppUserRepository appUserRepository,
                                    CompanyRepository companyRepository,
                                    CompanyMembershipRepository membershipRepository,
                                    PasswordEncoder passwordEncoder,
                                    AppSecurityProperties appSecurityProperties) {
        this.appUserRepository = appUserRepository;
        this.companyRepository = companyRepository;
        this.membershipRepository = membershipRepository;
        this.passwordEncoder = passwordEncoder;
        this.appSecurityProperties = appSecurityProperties;
    }

    @PostConstruct
    @Transactional
    public void bootstrapAdminAndDefaultCompany() {
        Instant now = Instant.now();
        CompanyEntity company = companyRepository.findByCode("DEFAULT")
                .orElseGet(() -> {
                    CompanyEntity created = new CompanyEntity();
                    created.setCode("DEFAULT");
                    created.setName("Default Company");
                    created.setStatus(CompanyStatus.ACTIVE);
                    created.setCreatedAt(now);
                    created.setUpdatedAt(now);
                    return companyRepository.save(created);
                });

        AppUserEntity admin = appUserRepository.findByUsername(appSecurityProperties.username())
                .orElseGet(() -> {
                    AppUserEntity created = new AppUserEntity();
                    created.setUsername(appSecurityProperties.username());
                    created.setPasswordHash(passwordEncoder.encode(appSecurityProperties.password()));
                    created.setPlatformRole(PlatformRole.PLATFORM_ADMIN);
                    created.setActive(true);
                    created.setBlocked(false);
                    created.setMustChangePassword(false);
                    created.setCreatedAt(now);
                    created.setUpdatedAt(now);
                    return appUserRepository.save(created);
                });

        membershipRepository.findByUserUsernameAndCompanyId(admin.getUsername(), company.getId())
                .orElseGet(() -> {
                    CompanyMembershipEntity membership = new CompanyMembershipEntity();
                    membership.setUser(admin);
                    membership.setCompany(company);
                    membership.setRole(CompanyRole.OWNER);
                    membership.setCreatedAt(now);
                    membership.setUpdatedAt(now);
                    return membershipRepository.save(membership);
                });
    }
}
