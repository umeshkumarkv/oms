package com.brokerdealer.marketdataservice.repository;

import com.brokerdealer.marketdataservice.domain.UserWatchlist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserWatchlistRepository extends JpaRepository<UserWatchlist, UUID> {

    List<UserWatchlist> findByUserIdOrderByAddedAtAsc(UUID userId);

    long countByUserId(UUID userId);

    boolean existsByUserIdAndSymbol(UUID userId, String symbol);

    Optional<UserWatchlist> findByUserIdAndSymbol(UUID userId, String symbol);
}
