package com.brokerdealer.clientservice.service;

import com.brokerdealer.clientservice.domain.Client;
import com.brokerdealer.clientservice.domain.Holding;
import com.brokerdealer.clientservice.domain.ProcessedFill;
import com.brokerdealer.clientservice.kafka.event.ExecutionCompletedEvent;
import com.brokerdealer.clientservice.kafka.event.OrderStateChangedEvent;
import com.brokerdealer.clientservice.repository.ClientRepository;
import com.brokerdealer.clientservice.repository.HoldingRepository;
import com.brokerdealer.clientservice.repository.ProcessedFillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioUpdateService {

    private static final Set<String> RELEASE_STATES = Set.of("FILLED", "RISK_REJECTED", "REJECTED", "EXPIRED", "CANCELLED");

    private final ClientRepository clientRepository;
    private final HoldingRepository holdingRepository;
    private final ProcessedFillRepository processedFillRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Handles order.state.changed events.
     * Releases reserved_cash for BUY orders that reach a terminal state.
     */
    @Transactional
    public void handleOrderStateChanged(OrderStateChangedEvent event) {
        if (event.getClientId() == null || event.getNewState() == null) {
            log.warn("Ignoring malformed order.state.changed event: orderId={}", event.getOrderId());
            return;
        }

        if (!"BUY".equalsIgnoreCase(event.getSide())) {
            log.debug("Skipping order.state.changed for SELL order: orderId={}", event.getOrderId());
            return;
        }

        if (!RELEASE_STATES.contains(event.getNewState())) {
            log.debug("No reserved cash action for state: orderId={} state={}", event.getOrderId(), event.getNewState());
            return;
        }

        if (event.getOrderValue() == null) {
            log.warn("Missing orderValue in state.changed event: orderId={} — cannot release reserved cash", event.getOrderId());
            return;
        }

        UUID clientId = event.getClientId();
        Client client = clientRepository.findById(clientId).orElse(null);
        if (client == null) {
            log.warn("Client not found for order.state.changed: clientId={}", clientId);
            return;
        }

        BigDecimal releaseAmount = new BigDecimal(event.getOrderValue());
        BigDecimal newReservedCash = client.getReservedCash().subtract(releaseAmount).setScale(2, RoundingMode.HALF_UP);

        if (newReservedCash.compareTo(BigDecimal.ZERO) < 0) {
            log.warn("Reserved cash already released (idempotent skip): orderId={} clientId={}", event.getOrderId(), clientId);
            return;
        }

        client.setReservedCash(newReservedCash);
        clientRepository.save(client);

        invalidateCache(clientId);
        log.info("Reserved cash released: clientId={} orderId={} state={} released={}",
                clientId, event.getOrderId(), event.getNewState(), releaseAmount.toPlainString());
    }

    /**
     * Handles execution.completed events.
     * Updates cash_balance and holdings. Idempotent via processed_fills table.
     */
    @Transactional
    public void handleExecutionCompleted(ExecutionCompletedEvent event) {
        if (event.getExecutionId() == null || event.getClientId() == null) {
            log.warn("Ignoring malformed execution.completed event: orderId={}", event.getOrderId());
            return;
        }

        if ("REJECTED".equalsIgnoreCase(event.getFillType())) {
            log.debug("Skipping REJECTED fill event: executionId={}", event.getExecutionId());
            return;
        }

        if (processedFillRepository.existsByExecutionId(event.getExecutionId())) {
            log.info("Duplicate execution.completed — skipping (idempotent): executionId={}", event.getExecutionId());
            return;
        }

        UUID clientId = event.getClientId();
        Client client = clientRepository.findById(clientId).orElse(null);
        if (client == null) {
            log.warn("Client not found for execution.completed: clientId={}", clientId);
            return;
        }

        BigDecimal fillPrice = new BigDecimal(event.getFillPrice());
        BigDecimal fillQty = BigDecimal.valueOf(event.getFillQuantity());
        BigDecimal fillValue = fillQty.multiply(fillPrice).setScale(2, RoundingMode.HALF_UP);

        if ("BUY".equalsIgnoreCase(event.getSide())) {
            processBuyFill(client, event.getSymbol(), event.getFillQuantity(), fillPrice, fillValue);
        } else {
            processSellFill(client, event.getSymbol(), event.getFillQuantity(), fillValue);
        }

        clientRepository.save(client);

        processedFillRepository.save(ProcessedFill.builder()
                .executionId(event.getExecutionId())
                .processedAt(Instant.now())
                .build());

        invalidateCache(clientId);
        log.info("Execution processed: clientId={} executionId={} side={} symbol={} qty={} fillType={}",
                clientId, event.getExecutionId(), event.getSide(), event.getSymbol(),
                event.getFillQuantity(), event.getFillType());
    }

    private void processBuyFill(Client client, String symbol, Integer fillQty,
                                 BigDecimal fillPrice, BigDecimal fillValue) {
        client.setCashBalance(client.getCashBalance().subtract(fillValue).setScale(2, RoundingMode.HALF_UP));

        Optional<Holding> existing = holdingRepository.findByClientIdAndSymbol(client.getId(), symbol);
        if (existing.isPresent()) {
            Holding holding = existing.get();
            BigDecimal oldQty = BigDecimal.valueOf(holding.getQuantity());
            BigDecimal newQty = oldQty.add(BigDecimal.valueOf(fillQty));
            BigDecimal newAvgPrice = oldQty.multiply(holding.getAvgPrice())
                    .add(BigDecimal.valueOf(fillQty).multiply(fillPrice))
                    .divide(newQty, 4, RoundingMode.HALF_UP);
            holding.setQuantity(holding.getQuantity() + fillQty);
            holding.setAvgPrice(newAvgPrice);
            holdingRepository.save(holding);
        } else {
            holdingRepository.save(Holding.builder()
                    .id(UUID.randomUUID())
                    .clientId(client.getId())
                    .symbol(symbol)
                    .quantity(fillQty)
                    .avgPrice(fillPrice.setScale(4, RoundingMode.HALF_UP))
                    .build());
        }
    }

    private void processSellFill(Client client, String symbol, Integer fillQty, BigDecimal fillValue) {
        client.setCashBalance(client.getCashBalance().add(fillValue).setScale(2, RoundingMode.HALF_UP));

        holdingRepository.findByClientIdAndSymbol(client.getId(), symbol).ifPresent(holding -> {
            int newQty = holding.getQuantity() - fillQty;
            if (newQty <= 0) {
                holdingRepository.delete(holding);
                log.info("Holding deleted (sold to zero): clientId={} symbol={}", client.getId(), symbol);
            } else {
                holding.setQuantity(newQty);
                holdingRepository.save(holding);
            }
        });
    }

    private void invalidateCache(UUID clientId) {
        redisTemplate.delete("client:portfolio:" + clientId);
        redisTemplate.delete("client:balance:" + clientId);
        log.debug("Cache invalidated: clientId={}", clientId);
    }
}
