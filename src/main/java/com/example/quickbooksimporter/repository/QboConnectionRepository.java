package com.example.quickbooksimporter.repository;

import com.example.quickbooksimporter.persistence.QboConnectionEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QboConnectionRepository extends JpaRepository<QboConnectionEntity, Long> {

    Optional<QboConnectionEntity> findByRealmIdAndConnectedTrue(String realmId);

    Optional<QboConnectionEntity> findTopByCompanyIdAndConnectedTrueOrderByUpdatedAtDesc(Long companyId);

    Optional<QboConnectionEntity> findTopByCompanyIdOrderByUpdatedAtDesc(Long companyId);
}
