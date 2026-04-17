package com.brokerdealer.marketdataservice.repository;

import com.brokerdealer.marketdataservice.domain.Instrument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InstrumentRepository extends JpaRepository<Instrument, String> {
}
