package com.example.quickbooksimporter.repository;

import com.example.quickbooksimporter.persistence.CsvCompareMappingProfileEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CsvCompareMappingProfileRepository extends JpaRepository<CsvCompareMappingProfileEntity, Long> {

    List<CsvCompareMappingProfileEntity> findByOrderByNameAsc();
}
