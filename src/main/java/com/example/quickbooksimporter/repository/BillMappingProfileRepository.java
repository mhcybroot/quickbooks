package com.example.quickbooksimporter.repository;

import com.example.quickbooksimporter.persistence.BillMappingProfileEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillMappingProfileRepository extends JpaRepository<BillMappingProfileEntity, Long> {
    List<BillMappingProfileEntity> findByOrderByNameAsc();
}
