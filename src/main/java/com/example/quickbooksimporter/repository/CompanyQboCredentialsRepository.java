package com.example.quickbooksimporter.repository;

import com.example.quickbooksimporter.persistence.CompanyQboCredentialsEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyQboCredentialsRepository extends JpaRepository<CompanyQboCredentialsEntity, Long> {

    Optional<CompanyQboCredentialsEntity> findByCompanyId(Long companyId);

    Optional<CompanyQboCredentialsEntity> findByCompanyIdAndActiveTrue(Long companyId);
}
