package com.brokerdealer.executionservice.repository;

import com.brokerdealer.executionservice.domain.Execution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ExecutionRepository extends JpaRepository<Execution, UUID> {
    boolean existsByOrderId(UUID orderId);
    List<Execution> findByOrderIdOrderByExecutedAtAsc(UUID orderId);
}
