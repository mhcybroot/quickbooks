package com.example.quickbooksimporter.service;

import com.example.quickbooksimporter.config.QuickBooksProperties;
import com.example.quickbooksimporter.domain.QboEnvironment;
import com.example.quickbooksimporter.persistence.CompanyEntity;
import com.example.quickbooksimporter.persistence.CompanyQboCredentialsEntity;
import com.example.quickbooksimporter.repository.CompanyQboCredentialsRepository;
import com.example.quickbooksimporter.repository.CompanyRepository;
import java.time.Instant;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyQboCredentialsService {

    private final CompanyQboCredentialsRepository repository;
    private final CompanyRepository companyRepository;
    private final CurrentUserService currentUserService;
    private final QboCredentialsCryptoService cryptoService;
    private final QuickBooksProperties properties;
    private final AuditLogService auditLogService;

    public CompanyQboCredentialsService(CompanyQboCredentialsRepository repository,
                                        CompanyRepository companyRepository,
                                        CurrentUserService currentUserService,
                                        QboCredentialsCryptoService cryptoService,
                                        QuickBooksProperties properties,
                                        AuditLogService auditLogService) {
        this.repository = repository;
        this.companyRepository = companyRepository;
        this.currentUserService = currentUserService;
        this.cryptoService = cryptoService;
        this.properties = properties;
        this.auditLogService = auditLogService;
    }

    public EffectiveQboCredentials getEffective(Long companyId) {
        Optional<CompanyQboCredentialsEntity> found = repository.findByCompanyIdAndActiveTrue(companyId);
        if (found.isPresent()) {
            CompanyQboCredentialsEntity entity = found.get();
            return new EffectiveQboCredentials(
                    entity.getClientId(),
                    cryptoService.decrypt(entity.getClientSecretEncrypted()),
                    StringUtils.defaultIfBlank(entity.getRedirectUriOverride(), properties.redirectUri()),
                    baseUrlForEnvironment(entity.getQboEnvironment()),
                    CredentialSource.COMPANY,
                    maskClientId(entity.getClientId()),
                    entity.getQboEnvironment());
        }
        return new EffectiveQboCredentials(
                properties.clientId(),
                properties.clientSecret(),
                properties.redirectUri(),
                properties.baseUrl(),
                CredentialSource.GLOBAL_FALLBACK,
                maskClientId(properties.clientId()),
                parseGlobalEnvironment(properties.environment()));
    }

    public Optional<CompanyQboCredentialsEntity> findByCompanyId(Long companyId) {
        return repository.findByCompanyId(companyId);
    }

    @Transactional
    public CompanyQboCredentialsEntity upsertForCompany(Long companyId,
                                                        String clientId,
                                                        String clientSecret,
                                                        String redirectUriOverride,
                                                        QboEnvironment qboEnvironment,
                                                        boolean active) {
        if (StringUtils.isBlank(clientId)) {
            throw new IllegalArgumentException("Client ID is required");
        }
        if (qboEnvironment == null) {
            throw new IllegalArgumentException("QBO environment is required");
        }
        CompanyEntity company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));
        CompanyQboCredentialsEntity entity = repository.findByCompanyId(companyId)
                .orElseGet(CompanyQboCredentialsEntity::new);
        if (entity.getId() == null && StringUtils.isBlank(clientSecret)) {
            throw new IllegalArgumentException("Client secret is required for first-time credential setup");
        }
        Instant now = Instant.now();
        entity.setCompany(company);
        entity.setClientId(clientId.trim());
        if (StringUtils.isNotBlank(clientSecret)) {
            entity.setClientSecretEncrypted(cryptoService.encrypt(clientSecret));
        }
        entity.setRedirectUriOverride(StringUtils.trimToNull(redirectUriOverride));
        entity.setQboEnvironment(qboEnvironment);
        entity.setActive(active);
        entity.setUpdatedAt(now);
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(now);
        }
        entity.setUpdatedByUser(currentUserService.requireUser());
        CompanyQboCredentialsEntity saved = repository.save(entity);
        auditLogService.log("QBO_COMPANY_CREDENTIALS_UPSERT", company,
                "Updated QuickBooks app credentials for company " + company.getName());
        return saved;
    }

    @Transactional
    public void disableForCompany(Long companyId) {
        CompanyQboCredentialsEntity entity = repository.findByCompanyId(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company credentials not found"));
        entity.setActive(false);
        entity.setUpdatedAt(Instant.now());
        entity.setUpdatedByUser(currentUserService.requireUser());
        repository.save(entity);
        auditLogService.log("QBO_COMPANY_CREDENTIALS_DISABLED", entity.getCompany(),
                "Disabled QuickBooks app credentials for company " + entity.getCompany().getName());
    }

    public enum CredentialSource {
        COMPANY,
        GLOBAL_FALLBACK
    }

    public record EffectiveQboCredentials(
            String clientId,
            String clientSecret,
            String redirectUri,
            String baseUrl,
            CredentialSource source,
            String clientIdHint,
            QboEnvironment qboEnvironment) {
    }

    private String baseUrlForEnvironment(QboEnvironment environment) {
        return switch (environment) {
            case SANDBOX -> "https://sandbox-quickbooks.api.intuit.com";
            case PRODUCTION -> "https://quickbooks.api.intuit.com";
        };
    }

    private QboEnvironment parseGlobalEnvironment(String rawEnvironment) {
        if (rawEnvironment == null) {
            return QboEnvironment.SANDBOX;
        }
        return rawEnvironment.equalsIgnoreCase("production") ? QboEnvironment.PRODUCTION : QboEnvironment.SANDBOX;
    }

    public static String maskClientId(String clientId) {
        if (StringUtils.isBlank(clientId)) {
            return "";
        }
        String normalized = clientId.trim();
        if (normalized.length() <= 4) {
            return "****" + normalized;
        }
        return "****" + normalized.substring(normalized.length() - 4);
    }
}
