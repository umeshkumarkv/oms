package com.brokerdealer.marketdataservice.service;

import com.brokerdealer.marketdataservice.domain.Instrument;
import com.brokerdealer.marketdataservice.dto.response.ResetResponse;
import com.brokerdealer.marketdataservice.exception.ValidationException;
import com.brokerdealer.marketdataservice.repository.InstrumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminResetService {

    private static final String CACHE_KEY_PREFIX = "market:price:";

    private final InstrumentRepository instrumentRepository;
    private final ScenarioService scenarioService;
    private final RedisTemplate<String, Object> redisTemplate;

    public ResetResponse reset(Boolean confirmReset) {
        if (!Boolean.TRUE.equals(confirmReset)) {
            throw new ValidationException("RESET_NOT_CONFIRMED",
                    "confirmReset must be true to proceed with market data reset");
        }
        List<Instrument> instruments = instrumentRepository.findAll();
        for (Instrument instrument : instruments) {
            if (instrument.getSeedLastPrice() != null) {
                instrument.setLastPrice(instrument.getSeedLastPrice());
                instrument.setBidPrice(instrument.getSeedBidPrice());
                instrument.setAskPrice(instrument.getSeedAskPrice());
            }
            instrument.setDayHigh(null);
            instrument.setDayLow(null);
            instrument.setPreviousClose(null);
            instrument.setVolume(0L);
            instrument.setDataTimestamp(Instant.now());
        }
        instrumentRepository.saveAll(instruments);

        instruments.forEach(i -> redisTemplate.delete(CACHE_KEY_PREFIX + i.getSymbol()));

        scenarioService.clearAllScenarios();

        log.info("Market data reset complete: instrumentsReset={}", instruments.size());

        return ResetResponse.builder()
                .status("RESET_COMPLETE")
                .timestamp(Instant.now().toString())
                .build();
    }
}
