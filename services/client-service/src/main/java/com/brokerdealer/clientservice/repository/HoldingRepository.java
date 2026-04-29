package com.brokerdealer.clientservice.repository;

import com.brokerdealer.clientservice.domain.Holding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface HoldingRepository extends JpaRepository<Holding, UUID> {

    List<Holding> findByClientId(UUID clientId);
}
