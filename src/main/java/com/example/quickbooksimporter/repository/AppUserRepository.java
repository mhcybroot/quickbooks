package com.example.quickbooksimporter.repository;

import com.example.quickbooksimporter.persistence.AppUserEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUserEntity, Long> {

    Optional<AppUserEntity> findByUsername(String username);

    long countByActiveTrueAndPlatformRole(com.example.quickbooksimporter.domain.PlatformRole role);

    long countByActiveTrueAndBlockedFalseAndPlatformRole(com.example.quickbooksimporter.domain.PlatformRole role);
}
