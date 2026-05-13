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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WatchlistServiceTest {

    @Mock
    private UserWatchlistRepository userWatchlistRepository;

    @Mock
    private InstrumentRepository instrumentRepository;

    @InjectMocks
    private WatchlistService watchlistService;

    private static final UUID USER_ID = UUID.randomUUID();

    // --- getWatchlist ---

    @Test
    void shouldReturnEmptyListWhenUserHasNoWatchlistEntries() {
        when(userWatchlistRepository.findByUserIdOrderByAddedAtAsc(USER_ID))
                .thenReturn(List.of());

        List<WatchlistEntryResponse> result = watchlistService.getWatchlist(USER_ID.toString());

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnWatchlistEntriesForCurrentUser() {
        UserWatchlist entry = buildEntry(USER_ID, "AAPL");
        when(userWatchlistRepository.findByUserIdOrderByAddedAtAsc(USER_ID))
                .thenReturn(List.of(entry));

        List<WatchlistEntryResponse> result = watchlistService.getWatchlist(USER_ID.toString());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSymbol()).isEqualTo("AAPL");
        assertThat(result.get(0).getAddedAt()).isNotNull();
    }

    @Test
    void shouldNotReturnEntriesFromOtherUsers() {
        when(userWatchlistRepository.findByUserIdOrderByAddedAtAsc(USER_ID))
                .thenReturn(List.of());

        List<WatchlistEntryResponse> result = watchlistService.getWatchlist(USER_ID.toString());

        assertThat(result).isEmpty();
    }

    // --- addToWatchlist ---

    @Test
    void shouldAddSymbolSuccessfully() {
        when(instrumentRepository.existsById("AAPL")).thenReturn(true);
        when(userWatchlistRepository.existsByUserIdAndSymbol(USER_ID, "AAPL")).thenReturn(false);
        when(userWatchlistRepository.countByUserId(USER_ID)).thenReturn(0L);

        WatchlistEntryResponse result = watchlistService.addToWatchlist(USER_ID.toString(), "aapl");

        assertThat(result.getSymbol()).isEqualTo("AAPL");
        assertThat(result.getAddedAt()).isNotNull();

        ArgumentCaptor<UserWatchlist> captor = ArgumentCaptor.forClass(UserWatchlist.class);
        verify(userWatchlistRepository).save(captor.capture());
        assertThat(captor.getValue().getSymbol()).isEqualTo("AAPL");
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
    }

    @Test
    void shouldThrowSymbolNotFoundWhenSymbolNotInInstruments() {
        when(instrumentRepository.existsById("UNKNOWN")).thenReturn(false);

        assertThatThrownBy(() -> watchlistService.addToWatchlist(USER_ID.toString(), "unknown"))
                .isInstanceOf(SymbolNotFoundException.class)
                .hasMessageContaining("UNKNOWN");

        verify(userWatchlistRepository, never()).save(any());
    }

    @Test
    void shouldThrowSymbolAlreadyInWatchlistWhenDuplicate() {
        when(instrumentRepository.existsById("AAPL")).thenReturn(true);
        when(userWatchlistRepository.existsByUserIdAndSymbol(USER_ID, "AAPL")).thenReturn(true);

        assertThatThrownBy(() -> watchlistService.addToWatchlist(USER_ID.toString(), "AAPL"))
                .isInstanceOf(SymbolAlreadyInWatchlistException.class);

        verify(userWatchlistRepository, never()).save(any());
    }

    @Test
    void shouldThrowWatchlistLimitExceededWhenUserHas50Symbols() {
        when(instrumentRepository.existsById("NVDA")).thenReturn(true);
        when(userWatchlistRepository.existsByUserIdAndSymbol(USER_ID, "NVDA")).thenReturn(false);
        when(userWatchlistRepository.countByUserId(USER_ID))
                .thenReturn((long) WatchlistService.MAX_WATCHLIST_SIZE);

        assertThatThrownBy(() -> watchlistService.addToWatchlist(USER_ID.toString(), "NVDA"))
                .isInstanceOf(WatchlistLimitExceededException.class);

        verify(userWatchlistRepository, never()).save(any());
    }

    // --- removeFromWatchlist ---

    @Test
    void shouldRemoveSymbolSuccessfully() {
        UserWatchlist entry = buildEntry(USER_ID, "AAPL");
        when(userWatchlistRepository.findByUserIdAndSymbol(USER_ID, "AAPL"))
                .thenReturn(Optional.of(entry));

        watchlistService.removeFromWatchlist(USER_ID.toString(), "aapl");

        verify(userWatchlistRepository).delete(entry);
    }

    @Test
    void shouldThrowSymbolNotInWatchlistWhenRemovingAbsentSymbol() {
        when(userWatchlistRepository.findByUserIdAndSymbol(USER_ID, "TSLA"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> watchlistService.removeFromWatchlist(USER_ID.toString(), "TSLA"))
                .isInstanceOf(SymbolNotInWatchlistException.class);

        verify(userWatchlistRepository, never()).delete(any());
    }

    // --- parseUserId: auth guard ---

    @Test
    void shouldThrowUnauthorizedWhenUserIdHeaderMissing() {
        assertThatThrownBy(() -> watchlistService.getWatchlist(null))
                .isInstanceOf(AccessForbiddenException.class);
        assertThatThrownBy(() -> watchlistService.getWatchlist(""))
                .isInstanceOf(AccessForbiddenException.class);
        assertThatThrownBy(() -> watchlistService.getWatchlist("  "))
                .isInstanceOf(AccessForbiddenException.class);
    }

    @Test
    void shouldThrowUnauthorizedWhenUserIdHeaderIsNotValidUuid() {
        assertThatThrownBy(() -> watchlistService.getWatchlist("not-a-uuid"))
                .isInstanceOf(AccessForbiddenException.class);
    }

    // --- helpers ---

    private UserWatchlist buildEntry(UUID userId, String symbol) {
        return UserWatchlist.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .symbol(symbol)
                .addedAt(Instant.now())
                .build();
    }
}
