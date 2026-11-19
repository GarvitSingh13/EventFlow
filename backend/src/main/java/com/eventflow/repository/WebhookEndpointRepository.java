package com.eventflow.repository;

import com.eventflow.entity.WebhookEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, UUID> {
    List<WebhookEndpoint> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);
    List<WebhookEndpoint> findByOrganizationIdAndStatus(UUID organizationId, String status);
}
