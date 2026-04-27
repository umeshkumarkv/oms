package com.brokerdealer.clientservice.service;

import com.brokerdealer.clientservice.client.MarketDataClient;
import com.brokerdealer.clientservice.domain.Client;
import com.brokerdealer.clientservice.domain.Holding;
import com.brokerdealer.clientservice.dto.response.ClientProfileResponse;
import com.brokerdealer.clientservice.dto.response.HoldingDetailResponse;
import com.brokerdealer.clientservice.dto.response.PortfolioResponse;
import com.brokerdealer.clientservice.exception.ClientNotFoundException;
import com.brokerdealer.clientservice.mapper.ClientMapper;
import com.brokerdealer.clientservice.repository.ClientRepository;
import com.brokerdealer.clientservice.repository.HoldingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClientService {

    private final ClientRepository clientRepository;
    private final HoldingRepository holdingRepository;
    private final ClientMapper clientMapper;
    private final MarketDataClient marketDataClient;

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

    public Object adjustReservedCash(UUID id, Object request) {
        // TODO: implement — JIRA story SCRUM-32
        return null;
    }
}
