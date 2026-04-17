package com.brokerdealer.clientservice.service;

import com.brokerdealer.clientservice.repository.ClientRepository;
import com.brokerdealer.clientservice.repository.HoldingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClientService {

    private final ClientRepository clientRepository;
    private final HoldingRepository holdingRepository;

    public Optional<Object> getClient(UUID id) {
        // TODO: implement — JIRA story BD-XX
        return Optional.empty();
    }

    public Optional<Object> getPortfolio(UUID id) {
        // TODO: implement — JIRA story BD-XX
        return Optional.empty();
    }

    public Optional<Object> adjustReservedCash(UUID id, Object request) {
        // TODO: implement — JIRA story BD-XX
        return Optional.empty();
    }
}
