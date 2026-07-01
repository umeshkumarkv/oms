package com.brokerdealer.auditservice.repository;

import com.brokerdealer.auditservice.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByClientIdOrderByEventTimestampDesc(UUID clientId, Pageable pageable);

    List<AuditLog> findByEntityIdOrderByEventTimestampDesc(UUID entityId);

    Page<AuditLog> findAllByOrderByEventTimestampDesc(Pageable pageable);

    boolean existsByEntityIdAndNewStateAndEventTimestamp(UUID entityId, String newState, Instant eventTimestamp);
}
