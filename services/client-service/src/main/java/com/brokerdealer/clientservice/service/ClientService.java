package com.brokerdealer.clientservice.service;

import com.brokerdealer.clientservice.dto.response.ClientProfileResponse;
import com.brokerdealer.clientservice.exception.ClientNotFoundException;
import com.brokerdealer.clientservice.mapper.ClientMapper;
import com.brokerdealer.clientservice.repository.ClientRepository;
import com.brokerdealer.clientservice.repository.HoldingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClientService {

    private final ClientRepository clientRepository;
    private final HoldingRepository holdingRepository;
    private final ClientMapper clientMapper;

    public ClientProfileResponse getClient(UUID id) {
        log.debug("Fetching client profile: clientId={}", id);
        return clientRepository.findById(id)
                .map(clientMapper::toProfileResponse)
                .orElseThrow(() -> new ClientNotFoundException("CLIENT_NOT_FOUND",
                        "Client not found: " + id));
    }

    public Object getPortfolio(UUID id) {
        // TODO: implement — JIRA story SCRUM-34
        return null;
    }

    public Object adjustReservedCash(UUID id, Object request) {
        // TODO: implement — JIRA story SCRUM-34
        return null;
    }
}
