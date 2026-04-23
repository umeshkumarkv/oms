package com.brokerdealer.orderservice.service;

import com.brokerdealer.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;

    public Object submitOrder(Object request) {
        // TODO: implement — JIRA story BD-XX
        return null;
    }

    public Object getOrders(String status, UUID clientId, int page, int size) {
        // TODO: implement — JIRA story BD-XX
        return null;
    }

    public Optional<Object> getOrderById(UUID id) {
        // TODO: implement — JIRA story BD-XX
        return Optional.empty();
    }
}
