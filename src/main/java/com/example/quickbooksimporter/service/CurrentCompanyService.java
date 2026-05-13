package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.domain.PlatformRole;
import com.example.quickbooksimporter.persistence.CompanyEntity;
import com.example.quickbooksimporter.persistence.CompanyMembershipEntity;
import com.example.quickbooksimporter.repository.CompanyMembershipRepository;
import com.example.quickbooksimporter.repository.CompanyRepository;
import com.vaadin.flow.server.VaadinSession;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class CurrentCompanyService {

    private static final String SESSION_KEY = "selected.company.id";
    private static final ThreadLocal<Long> ASYNC_COMPANY_ID = new ThreadLocal<>();

    private final CurrentUserService currentUserService;
    private final CompanyRepository companyRepository;
    private final CompanyMembershipRepository membershipRepository;

    public CurrentCompanyService(CurrentUserService currentUserService,
                                 CompanyRepository companyRepository,
                                 CompanyMembershipRepository membershipRepository) {
        this.currentUserService = currentUserService;
        this.companyRepository = companyRepository;
        this.membershipRepository = membershipRepository;
    }

    public List<CompanyEntity> availableCompanies() {
        var user = currentUserService.requireUser();
        if (user.getPlatformRole() == PlatformRole.PLATFORM_ADMIN) {
            return companyRepository.findByStatusOrderByNameAsc(com.example.quickbooksimporter.domain.CompanyStatus.ACTIVE);
        }
        return membershipRepository.findByUserUsernameAndCompanyStatusOrderByCompanyNameAsc(user.getUsername(), com.example.quickbooksimporter.domain.CompanyStatus.ACTIVE)
                .stream().map(CompanyMembershipEntity::getCompany).toList();
    }

    public boolean hasAccessibleCompanies() {
        return !availableCompanies().isEmpty();
    }

    public Long requireCurrentCompanyId() {
        Long asyncCompanyId = ASYNC_COMPANY_ID.get();
        if (asyncCompanyId != null) {
            return asyncCompanyId;
        }
        Optional<Long> selected = selectedCompanyIdFromSession();
        List<CompanyEntity> companies = availableCompanies();
        if (companies.isEmpty()) {
            return null;
        }
        if (selected.isPresent() && companies.stream().anyMatch(company -> company.getId().equals(selected.get()))) {
            return selected.get();
        }
        Long fallback = companies.getFirst().getId();
        setCurrentCompanyId(fallback);
        return fallback;
    }

    public CompanyEntity requireCurrentCompany() {
        Long companyId = requireCurrentCompanyId();
        if (companyId == null) {
            throw new IllegalStateException("No accessible company assigned for current user");
        }
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalStateException("Company not found: " + companyId));
    }

    public void setCurrentCompanyId(Long companyId) {
        if (companyId == null) {
            return;
        }
        List<CompanyEntity> companies = availableCompanies();
        boolean allowed = companies.stream().anyMatch(company -> company.getId().equals(companyId));
        if (!allowed) {
            throw new IllegalArgumentException("Company is not accessible by current user");
        }
        VaadinSession session = VaadinSession.getCurrent();
        if (session != null) {
            session.setAttribute(SESSION_KEY, companyId);
        }
    }

    private Optional<Long> selectedCompanyIdFromSession() {
        VaadinSession session = VaadinSession.getCurrent();
        if (session == null) {
            return Optional.empty();
        }
        Object value = session.getAttribute(SESSION_KEY);
        if (value instanceof Long id) {
            return Optional.of(id);
        }
        if (value instanceof Number number) {
            return Optional.of(number.longValue());
        }
        return Optional.empty();
    }

    public void runWithCompanyContext(Long companyId, Runnable runnable) {
        if (companyId == null) {
            runnable.run();
            return;
        }
        Long previous = ASYNC_COMPANY_ID.get();
        ASYNC_COMPANY_ID.set(companyId);
        try {
            runnable.run();
        } finally {
            if (previous == null) {
                ASYNC_COMPANY_ID.remove();
            } else {
                ASYNC_COMPANY_ID.set(previous);
            }
        }
    }
}
