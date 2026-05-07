package com.example.quickbooksimporter.repository;

import com.example.quickbooksimporter.persistence.PaymentMappingProfileEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentMappingProfileRepository extends JpaRepository<PaymentMappingProfileEntity, Long> {

    List<PaymentMappingProfileEntity> findByOrderByNameAsc();
}
