package com.brokerdealer.marketdataservice.repository;

import com.brokerdealer.marketdataservice.domain.MarketHoliday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface MarketHolidayRepository extends JpaRepository<MarketHoliday, LocalDate> {

    List<MarketHoliday> findAllByOrderByHolidayDateAsc();
}
