package com.eventflow.repository;

import com.eventflow.entity.DeliveryStatus;
import com.eventflow.entity.EventDelivery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface EventDeliveryRepository extends JpaRepository<EventDelivery, UUID> {

    List<EventDelivery> findByEventId(UUID eventId);

    Page<EventDelivery> findByEndpointOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

    @Query(value = """
        SELECT * FROM event_deliveries
        WHERE status IN ('PENDING', 'RETRY_SCHEDULED')
          AND next_retry_at <= :now
        ORDER BY next_retry_at ASC
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<EventDelivery> findAndLockDueDeliveries(@Param("now") Instant now, @Param("limit") int limit);

    long countByEndpointOrganizationIdAndStatus(UUID organizationId, DeliveryStatus status);
}
