package com.eventflow.repository;

import com.eventflow.entity.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EventRepository extends JpaRepository<Event, UUID> {
    Page<Event> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);
    Optional<Event> findByOrganizationIdAndIdempotencyKey(UUID organizationId, String idempotencyKey);
}
