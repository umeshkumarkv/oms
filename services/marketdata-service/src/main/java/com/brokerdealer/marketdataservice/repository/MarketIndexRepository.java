package com.brokerdealer.marketdataservice.repository;

import com.brokerdealer.marketdataservice.domain.MarketIndex;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MarketIndexRepository extends JpaRepository<MarketIndex, UUID> {

    List<MarketIndex> findAllByOrderByNameAsc();
}
