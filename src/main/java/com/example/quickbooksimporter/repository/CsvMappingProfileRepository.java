package com.example.quickbooksimporter.repository;

import com.example.quickbooksimporter.domain.EntityType;
import com.example.quickbooksimporter.persistence.CsvMappingProfileEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CsvMappingProfileRepository extends JpaRepository<CsvMappingProfileEntity, Long> {

    List<CsvMappingProfileEntity> findByEntityTypeOrderByNameAsc(EntityType entityType);
}
