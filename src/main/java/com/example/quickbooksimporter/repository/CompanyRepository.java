package com.example.quickbooksimporter.repository;

import com.example.quickbooksimporter.domain.CompanyStatus;
import com.example.quickbooksimporter.persistence.CompanyEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<CompanyEntity, Long> {

    Optional<CompanyEntity> findByCode(String code);

    List<CompanyEntity> findByStatusOrderByNameAsc(CompanyStatus status);
}
