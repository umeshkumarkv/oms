package com.brokerdealer.clientservice.service;

import com.brokerdealer.clientservice.client.MarketDataClient;
import com.brokerdealer.clientservice.domain.Client;
import com.brokerdealer.clientservice.domain.Holding;
import com.brokerdealer.clientservice.dto.request.ReservedCashRequest;
import com.brokerdealer.clientservice.dto.response.ClientProfileResponse;
import com.brokerdealer.clientservice.dto.response.HoldingDetailResponse;
import com.brokerdealer.clientservice.dto.response.PortfolioResponse;
import com.brokerdealer.clientservice.dto.response.ReservedCashResponse;
import com.brokerdealer.clientservice.exception.ClientNotFoundException;
import com.brokerdealer.clientservice.exception.InvalidReservationException;
import com.brokerdealer.clientservice.mapper.ClientMapper;
import com.brokerdealer.clientservice.repository.ClientRepository;
import com.brokerdealer.clientservice.repository.HoldingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClientService {

    private final ClientRepository clientRepository;
    private final HoldingRepository holdingRepository;
    private final ClientMapper clientMapper;
    private final MarketDataClient marketDataClient;
    private final RedisTemplate<String, Object> redisTemplate;

    public List<ClientProfileResponse> getAllClients() {
        log.debug("Fetching all client profiles");
        return clientRepository.findAll().stream()
                .map(clientMapper::toProfileResponse)
                .toList();
    }

    public ClientProfileResponse getClient(UUID id) {
        log.debug("Fetching client profile: clientId={}", id);
        return clientRepository.findById(id)
                .map(clientMapper::toProfileResponse)
                .orElseThrow(() -> new ClientNotFoundException("CLIENT_NOT_FOUND",
                        "Client not found: " + id));
    }

    public PortfolioResponse getPortfolio(UUID id) {
        log.debug("Fetching portfolio: clientId={}", id);
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new ClientNotFoundException("CLIENT_NOT_FOUND",
                        "Client not found: " + id));

        List<Holding> holdings = holdingRepository.findByClientId(id);
        BigDecimal buyingPower = client.getCashBalance().subtract(client.getReservedCash());

        List<HoldingDetailResponse> holdingDetails = holdings.stream()
                .map(this::buildHoldingDetail)
                .toList();

        return PortfolioResponse.builder()
                .clientId(client.getId())
                .name(client.getName())
                .status(client.getStatus())
                .tradingEnabled(client.getTradingEnabled())
                .kycStatus(client.getKycStatus())
                .kycExpiryDate(client.getKycExpiryDate())
                .marginCallActive(client.getMarginCallActive())
                .maxOpenOrders(client.getMaxOpenOrders())
                .cashBalance(client.getCashBalance().setScale(2, RoundingMode.HALF_UP).toPlainString())
                .reservedCash(client.getReservedCash().setScale(2, RoundingMode.HALF_UP).toPlainString())
                .buyingPower(buyingPower.setScale(2, RoundingMode.HALF_UP).toPlainString())
                .holdings(holdingDetails)
                .build();
    }

    private HoldingDetailResponse buildHoldingDetail(Holding holding) {
        BigDecimal currentPrice = marketDataClient.getLastPrice(holding.getSymbol());

        String marketValue = null;
        String unrealisedPnL = null;

        if (currentPrice != null) {
            marketValue = BigDecimal.valueOf(holding.getQuantity())
                    .multiply(currentPrice)
                    .setScale(2, RoundingMode.HALF_UP)
                    .toPlainString();
            unrealisedPnL = BigDecimal.valueOf(holding.getQuantity())
                    .multiply(currentPrice.subtract(holding.getAvgPrice()))
                    .setScale(2, RoundingMode.HALF_UP)
                    .toPlainString();
        }

        return HoldingDetailResponse.builder()
                .symbol(holding.getSymbol())
                .quantity(holding.getQuantity())
                .avgPrice(holding.getAvgPrice().toPlainString())
                .currentPrice(currentPrice != null ? currentPrice.toPlainString() : null)
                .marketValue(marketValue)
                .unrealisedPnL(unrealisedPnL)
                .build();
    }

    @Transactional
    public ReservedCashResponse adjustReservedCash(UUID id, ReservedCashRequest request) {
        log.debug("Adjusting reserved cash: clientId={}", id);
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new ClientNotFoundException("CLIENT_NOT_FOUND",
                        "Client not found: " + id));

        BigDecimal adjustment = new BigDecimal(request.getAdjustment());
        BigDecimal newReservedCash = client.getReservedCash().add(adjustment).setScale(2, RoundingMode.HALF_UP);

        if (newReservedCash.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidReservationException("RESERVED_CASH_BELOW_ZERO",
                    "Reserved cash cannot go below 0. Current: " + client.getReservedCash().toPlainString()
                            + ", adjustment: " + adjustment.toPlainString());
        }
        if (newReservedCash.compareTo(client.getCashBalance()) > 0) {
            throw new InvalidReservationException("RESERVED_CASH_EXCEEDS_BALANCE",
                    "Reserved cash cannot exceed cash balance. Cash balance: " + client.getCashBalance().toPlainString()
                            + ", requested reserved: " + newReservedCash.toPlainString());
        }

        client.setReservedCash(newReservedCash);
        clientRepository.save(client);

        BigDecimal buyingPower = client.getCashBalance().subtract(newReservedCash).setScale(2, RoundingMode.HALF_UP);
        log.info("Reserved cash adjusted: clientId={} newReservedCash={} buyingPower={}", id, newReservedCash, buyingPower);

        return ReservedCashResponse.builder()
                .clientId(id)
                .reservedCash(newReservedCash.toPlainString())
                .buyingPower(buyingPower.toPlainString())
                .build();
    }

    @Transactional
    public void processExecutionFill(Map<String, Object> payload) {
        UUID clientId = UUID.fromString((String) payload.get("clientId"));
        String side = (String) payload.get("side");
        String fillType = (String) payload.get("fillType");
        int executedQty = ((Number) payload.get("executedQty")).intValue();
        BigDecimal executedPrice = new BigDecimal((String) payload.get("executedPrice"));
        BigDecimal fillValue = new BigDecimal((String) payload.get("fillValue"));
        String symbol = (String) payload.get("symbol");

        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ClientNotFoundException("CLIENT_NOT_FOUND", "Client not found: " + clientId));

        if ("BUY".equals(side)) {
            if ("REJECTED".equals(fillType)) {
                int remainingQty = ((Number) payload.get("remainingQuantity")).intValue();
                BigDecimal marketPrice = new BigDecimal((String) payload.get("marketPriceAtFill"));
                BigDecimal releaseAmount = BigDecimal.valueOf(remainingQty)
                        .multiply(marketPrice).setScale(2, RoundingMode.HALF_UP);
                client.setReservedCash(client.getReservedCash().subtract(releaseAmount)
                        .max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
                log.info("Reserved cash released on rejection: clientId={} released={}", clientId, releaseAmount);
            } else {
                client.setReservedCash(client.getReservedCash().subtract(fillValue)
                        .max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
                client.setCashBalance(client.getCashBalance().subtract(fillValue)
                        .setScale(2, RoundingMode.HALF_UP));
                upsertHolding(clientId, symbol, executedQty, executedPrice);
                log.info("BUY fill applied: clientId={} symbol={} qty={} fillType={}", clientId, symbol, executedQty, fillType);
            }
        } else if ("SELL".equals(side) && !"REJECTED".equals(fillType)) {
            client.setCashBalance(client.getCashBalance().add(fillValue).setScale(2, RoundingMode.HALF_UP));
            reduceHolding(clientId, symbol, executedQty);
            log.info("SELL fill applied: clientId={} symbol={} qty={}", clientId, symbol, executedQty);
        }

        clientRepository.save(client);
        evictPortfolioCache(clientId);
    }

    private void upsertHolding(UUID clientId, String symbol, int executedQty, BigDecimal executedPrice) {
        Optional<Holding> existing = holdingRepository.findByClientIdAndSymbol(clientId, symbol);
        if (existing.isPresent()) {
            Holding h = existing.get();
            int newQty = h.getQuantity() + executedQty;
            BigDecimal newAvg = h.getAvgPrice().multiply(BigDecimal.valueOf(h.getQuantity()))
                    .add(executedPrice.multiply(BigDecimal.valueOf(executedQty)))
                    .divide(BigDecimal.valueOf(newQty), 4, RoundingMode.HALF_UP);
            h.setQuantity(newQty);
            h.setAvgPrice(newAvg);
            holdingRepository.save(h);
        } else {
            holdingRepository.save(Holding.builder()
                    .clientId(clientId)
                    .symbol(symbol)
                    .quantity(executedQty)
                    .avgPrice(executedPrice.setScale(4, RoundingMode.HALF_UP))
                    .build());
        }
    }

    private void reduceHolding(UUID clientId, String symbol, int executedQty) {
        holdingRepository.findByClientIdAndSymbol(clientId, symbol).ifPresent(h -> {
            int newQty = Math.max(0, h.getQuantity() - executedQty);
            h.setQuantity(newQty);
            holdingRepository.save(h);
        });
    }

    private void evictPortfolioCache(UUID clientId) {
        redisTemplate.delete("client:portfolio:" + clientId);
        redisTemplate.delete("client:balance:" + clientId);
    }
}
