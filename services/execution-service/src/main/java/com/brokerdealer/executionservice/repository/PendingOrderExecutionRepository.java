package com.brokerdealer.executionservice.repository;

import com.brokerdealer.executionservice.domain.PendingOrderExecution;
import com.brokerdealer.executionservice.domain.PendingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PendingOrderExecutionRepository extends JpaRepository<PendingOrderExecution, UUID> {
    boolean existsByOrderId(UUID orderId);
    List<PendingOrderExecution> findByStatus(PendingStatus status);
}
