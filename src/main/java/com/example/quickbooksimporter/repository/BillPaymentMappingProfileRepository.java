package com.example.quickbooksimporter.repository;

import com.example.quickbooksimporter.persistence.BillPaymentMappingProfileEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillPaymentMappingProfileRepository extends JpaRepository<BillPaymentMappingProfileEntity, Long> {
    List<BillPaymentMappingProfileEntity> findByOrderByNameAsc();
}
