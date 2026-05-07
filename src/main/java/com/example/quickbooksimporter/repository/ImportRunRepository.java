package com.example.quickbooksimporter.repository;

import com.example.quickbooksimporter.persistence.ImportRunEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportRunRepository extends JpaRepository<ImportRunEntity, Long> {

    List<ImportRunEntity> findTop20ByOrderByCreatedAtDesc();
}
