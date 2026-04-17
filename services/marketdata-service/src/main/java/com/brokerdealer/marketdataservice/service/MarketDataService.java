package com.brokerdealer.marketdataservice.service;

import com.brokerdealer.marketdataservice.repository.InstrumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketDataService {

    private final InstrumentRepository instrumentRepository;

    public Optional<Object> getInstrument(String symbol) {
        // TODO: implement — JIRA story BD-XX
        return Optional.empty();
    }

    public Object getMarketHours() {
        // TODO: implement — JIRA story BD-XX
        return null;
    }

    public Object getInstruments() {
        // TODO: implement — JIRA story BD-XX
        return null;
    }
}
