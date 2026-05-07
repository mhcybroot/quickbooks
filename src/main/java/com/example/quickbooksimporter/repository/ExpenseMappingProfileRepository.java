package com.example.quickbooksimporter.repository;

import com.example.quickbooksimporter.persistence.ExpenseMappingProfileEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseMappingProfileRepository extends JpaRepository<ExpenseMappingProfileEntity, Long> {

    List<ExpenseMappingProfileEntity> findByOrderByNameAsc();
}
