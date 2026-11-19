package com.eventflow.repository;

import com.eventflow.entity.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {
    Optional<ApiKey> findByKeyHashAndStatus(String keyHash, String status);
    List<ApiKey> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);
}
