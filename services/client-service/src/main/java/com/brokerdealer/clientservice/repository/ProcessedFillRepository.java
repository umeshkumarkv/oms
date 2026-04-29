package com.brokerdealer.clientservice.repository;

import com.brokerdealer.clientservice.domain.ProcessedFill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedFillRepository extends JpaRepository<ProcessedFill, UUID> {

    boolean existsByExecutionId(UUID executionId);
}
