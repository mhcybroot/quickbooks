package com.example.quickbooksimporter.persistence;

import com.example.quickbooksimporter.domain.QboEnvironment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "company_qbo_app_credentials")
public class CompanyQboCredentialsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "company_id", nullable = false)
    private CompanyEntity company;

    @Column(nullable = false)
    private String clientId;

    @Column(nullable = false, columnDefinition = "text")
    private String clientSecretEncrypted;

    private String redirectUriOverride;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QboEnvironment qboEnvironment;

    @Column(nullable = false)
    private boolean active;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "updated_by_user_id")
    private AppUserEntity updatedByUser;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public CompanyEntity getCompany() {
        return company;
    }

    public void setCompany(CompanyEntity company) {
        this.company = company;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecretEncrypted() {
        return clientSecretEncrypted;
    }

    public void setClientSecretEncrypted(String clientSecretEncrypted) {
        this.clientSecretEncrypted = clientSecretEncrypted;
    }

    public String getRedirectUriOverride() {
        return redirectUriOverride;
    }

    public void setRedirectUriOverride(String redirectUriOverride) {
        this.redirectUriOverride = redirectUriOverride;
    }

    public QboEnvironment getQboEnvironment() {
        return qboEnvironment;
    }

    public void setQboEnvironment(QboEnvironment qboEnvironment) {
        this.qboEnvironment = qboEnvironment;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public AppUserEntity getUpdatedByUser() {
        return updatedByUser;
    }

    public void setUpdatedByUser(AppUserEntity updatedByUser) {
        this.updatedByUser = updatedByUser;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
