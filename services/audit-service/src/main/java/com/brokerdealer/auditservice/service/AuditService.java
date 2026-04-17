package com.brokerdealer.auditservice.service;

import com.brokerdealer.auditservice.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public Optional<Object> getOrderAuditTrail(UUID orderId) {
        // TODO: implement — JIRA story BD-XX
        return Optional.empty();
    }

    public Object getAuditTrail(UUID clientId, String from, String to, String entityType, int page, int size) {
        // TODO: implement — JIRA story BD-XX
        return null;
    }
}
