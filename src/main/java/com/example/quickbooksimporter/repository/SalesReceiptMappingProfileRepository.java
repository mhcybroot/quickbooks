package com.example.quickbooksimporter.repository;

import com.example.quickbooksimporter.persistence.SalesReceiptMappingProfileEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesReceiptMappingProfileRepository extends JpaRepository<SalesReceiptMappingProfileEntity, Long> {

    List<SalesReceiptMappingProfileEntity> findByOrderByNameAsc();
}
