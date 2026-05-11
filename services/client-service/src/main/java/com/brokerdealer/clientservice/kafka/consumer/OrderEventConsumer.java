package com.brokerdealer.clientservice.kafka.consumer;

import com.brokerdealer.clientservice.service.ClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final ClientService clientService;

    @KafkaListener(topics = "order.state.changed",
                   groupId = "client-service-order.state.changed-group")
    public void consumeOrderStateChanged(Map<String, Object> payload) {
        log.debug("order.state.changed received: orderId={} toState={}",
                payload.get("orderId"), payload.get("toState"));
    }

    @KafkaListener(topics = "execution.completed",
                   groupId = "client-service-execution.completed-group")
    public void consumeExecutionCompleted(Map<String, Object> payload) {
        String fillType = (String) payload.get("fillType");
        log.info("execution.completed received: orderId={} fillType={} side={}",
                payload.get("orderId"), fillType, payload.get("side"));
        clientService.processExecutionFill(payload);
    }
}
