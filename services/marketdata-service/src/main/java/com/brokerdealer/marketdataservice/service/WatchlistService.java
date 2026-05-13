package com.brokerdealer.marketdataservice.service;

import com.brokerdealer.marketdataservice.domain.UserWatchlist;
import com.brokerdealer.marketdataservice.dto.response.WatchlistEntryResponse;
import com.brokerdealer.marketdataservice.exception.AccessForbiddenException;
import com.brokerdealer.marketdataservice.exception.SymbolAlreadyInWatchlistException;
import com.brokerdealer.marketdataservice.exception.SymbolNotFoundException;
import com.brokerdealer.marketdataservice.exception.SymbolNotInWatchlistException;
import com.brokerdealer.marketdataservice.exception.WatchlistLimitExceededException;
import com.brokerdealer.marketdataservice.repository.InstrumentRepository;
import com.brokerdealer.marketdataservice.repository.UserWatchlistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WatchlistService {

    static final int MAX_WATCHLIST_SIZE = 50;

    private final UserWatchlistRepository userWatchlistRepository;
    private final InstrumentRepository instrumentRepository;

    public List<WatchlistEntryResponse> getWatchlist(String userIdHeader) {
        UUID userId = parseUserId(userIdHeader);
        log.debug("Getting watchlist: userId={}", userId);
        return userWatchlistRepository.findByUserIdOrderByAddedAtAsc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public WatchlistEntryResponse addToWatchlist(String userIdHeader, String symbol) {
        UUID userId = parseUserId(userIdHeader);
        String upperSymbol = symbol.toUpperCase();
        log.debug("Adding to watchlist: userId={} symbol={}", userId, upperSymbol);

        if (!instrumentRepository.existsById(upperSymbol)) {
            throw new SymbolNotFoundException("SYMBOL_NOT_FOUND",
                    "Symbol not found: " + upperSymbol);
        }

        if (userWatchlistRepository.existsByUserIdAndSymbol(userId, upperSymbol)) {
            throw new SymbolAlreadyInWatchlistException("SYMBOL_ALREADY_IN_WATCHLIST",
                    "Symbol is already in watchlist: " + upperSymbol);
        }

        if (userWatchlistRepository.countByUserId(userId) >= MAX_WATCHLIST_SIZE) {
            throw new WatchlistLimitExceededException("WATCHLIST_LIMIT_EXCEEDED",
                    "Watchlist limit of " + MAX_WATCHLIST_SIZE + " symbols reached");
        }

        UserWatchlist entry = UserWatchlist.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .symbol(upperSymbol)
                .addedAt(Instant.now())
                .build();

        userWatchlistRepository.save(entry);
        log.info("Symbol added to watchlist: userId={} symbol={}", userId, upperSymbol);
        return toResponse(entry);
    }

    @Transactional
    public void removeFromWatchlist(String userIdHeader, String symbol) {
        UUID userId = parseUserId(userIdHeader);
        String upperSymbol = symbol.toUpperCase();
        log.debug("Removing from watchlist: userId={} symbol={}", userId, upperSymbol);

        UserWatchlist entry = userWatchlistRepository.findByUserIdAndSymbol(userId, upperSymbol)
                .orElseThrow(() -> new SymbolNotInWatchlistException("SYMBOL_NOT_IN_WATCHLIST",
                        "Symbol is not in watchlist: " + upperSymbol));

        userWatchlistRepository.delete(entry);
        log.info("Symbol removed from watchlist: userId={} symbol={}", userId, upperSymbol);
    }

    private UUID parseUserId(String userIdHeader) {
        if (userIdHeader == null || userIdHeader.isBlank()) {
            throw new AccessForbiddenException("UNAUTHORIZED", "Authentication required");
        }
        try {
            return UUID.fromString(userIdHeader);
        } catch (IllegalArgumentException e) {
            throw new AccessForbiddenException("UNAUTHORIZED", "Invalid user identity");
        }
    }

    private WatchlistEntryResponse toResponse(UserWatchlist entry) {
        return WatchlistEntryResponse.builder()
                .symbol(entry.getSymbol())
                .addedAt(entry.getAddedAt().toString())
                .build();
    }
}
