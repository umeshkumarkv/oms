package com.brokerdealer.clientservice.repository;

import com.brokerdealer.clientservice.domain.Client;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ClientRepository extends JpaRepository<Client, UUID> {

    @Query("SELECT c FROM Client c ORDER BY c.turnoverToday DESC")
    List<Client> findTopByTurnoverTodayDesc(Pageable pageable);
}
