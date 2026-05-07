package com.example.quickbooksimporter.repository;

import com.example.quickbooksimporter.persistence.QboConnectionEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QboConnectionRepository extends JpaRepository<QboConnectionEntity, Long> {

    Optional<QboConnectionEntity> findByRealmId(String realmId);
}
