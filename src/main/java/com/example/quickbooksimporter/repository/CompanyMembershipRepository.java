package com.example.quickbooksimporter.repository;

import com.example.quickbooksimporter.domain.CompanyRole;
import com.example.quickbooksimporter.persistence.CompanyMembershipEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyMembershipRepository extends JpaRepository<CompanyMembershipEntity, Long> {

    List<CompanyMembershipEntity> findByUserUsernameAndCompanyStatusOrderByCompanyNameAsc(String username, com.example.quickbooksimporter.domain.CompanyStatus status);

    Optional<CompanyMembershipEntity> findByUserUsernameAndCompanyId(String username, Long companyId);

    long countByCompanyIdAndRole(Long companyId, CompanyRole role);

    List<CompanyMembershipEntity> findByCompanyIdOrderByUserUsernameAsc(Long companyId);

    List<CompanyMembershipEntity> findByUserId(Long userId);

    long countByUserId(Long userId);

    void deleteByUserId(Long userId);
}
