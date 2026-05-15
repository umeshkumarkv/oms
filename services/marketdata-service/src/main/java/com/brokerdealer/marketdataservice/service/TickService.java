package com.brokerdealer.marketdataservice.service;

import com.brokerdealer.marketdataservice.dto.response.TickHistoryResponse;
import com.brokerdealer.marketdataservice.dto.response.TickResponse;
import com.brokerdealer.marketdataservice.repository.TickRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Serves the intraday tick history for a single symbol.
 *
 * Time range returned:
 *   from = today at midnight ET (00:00:00)      [inclusive, UTC Instant]
 *   to   = current time truncated to the minute [exclusive, UTC Instant]
 *
 * Anchoring to midnight (rather than market-open time) ensures the window
 * is always valid — in particular when bypass-market-hours=true causes ticks
 * to be saved before the configured open-time, or when tests run in a timezone
 * where the local clock is earlier than the ET open-time.
 *
 * tick_time is stored as TIMESTAMPTZ (UTC). The {@code time} field in each
 * TickResponse is formatted as ET local time ("HH:mm") for chart display.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TickService {

    private static final DateTimeFormatter ET_MINUTE_FMT = DateTimeFormatter.ofPattern("HH:mm");

    @Value("${market.hours.timezone}")
    private String timezone;

    private final TickRepository tickRepository;

    public TickHistoryResponse getHistory(String symbol) {
        String upper = symbol.toUpperCase();
        ZoneId et = ZoneId.of(timezone);
        ZonedDateTime now = ZonedDateTime.now(et);

        // Anchor to today midnight ET so the window is always valid regardless of
        // what time the configured open-time is or what timezone the developer is in.
        Instant from = now.toLocalDate().atStartOfDay(et).toInstant();
        Instant to = now.truncatedTo(ChronoUnit.MINUTES).toInstant();

        log.debug("Tick history: symbol={} from={} to={}", upper, from, to);

        List<TickResponse> ticks = tickRepository.findHistory(upper, from, to)
                .stream()
                .map(t -> {
                    String etTime = t.getTickTime().atZone(et).format(ET_MINUTE_FMT);
                    return TickResponse.builder()
                            .time(etTime)
                            .price(format4dp(t.getPrice()))
                            .bid(format4dp(t.getBid()))
                            .ask(format4dp(t.getAsk()))
                            .volume(t.getVolume())
                            .build();
                })
                .toList();

        return TickHistoryResponse.builder()
                .symbol(upper)
                .from(from.toString())
                .to(to.toString())
                .ticks(ticks)
                .build();
    }

    private static String format4dp(BigDecimal value) {
        return value != null ? value.setScale(4, RoundingMode.HALF_UP).toPlainString() : null;
    }
}
