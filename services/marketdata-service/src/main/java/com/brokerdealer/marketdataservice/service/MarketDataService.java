package com.brokerdealer.marketdataservice.service;

import com.brokerdealer.marketdataservice.dto.response.InstrumentResponse;
import com.brokerdealer.marketdataservice.exception.SymbolNotFoundException;
import com.brokerdealer.marketdataservice.mapper.InstrumentMapper;
import com.brokerdealer.marketdataservice.repository.InstrumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketDataService {

    private final InstrumentRepository instrumentRepository;
    private final InstrumentMapper instrumentMapper;

    public InstrumentResponse getInstrument(String symbol) {
        log.debug("Fetching instrument: symbol={}", symbol);
        return instrumentRepository.findById(symbol)
                .map(instrumentMapper::toResponse)
                .orElseThrow(() -> new SymbolNotFoundException("SYMBOL_NOT_FOUND",
                        "Symbol not found: " + symbol));
    }

    public Object getMarketHours() {
        // TODO: implement — JIRA story SCRUM-22
        return null;
    }

    public Object getInstruments() {
        // TODO: implement — JIRA story SCRUM-22
        return null;
    }
}
