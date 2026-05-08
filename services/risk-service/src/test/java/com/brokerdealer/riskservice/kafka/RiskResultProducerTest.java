package com.brokerdealer.riskservice.kafka;

import com.brokerdealer.riskservice.dto.RiskEvaluationResult;
import com.brokerdealer.riskservice.kafka.producer.RiskResultProducer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RiskResultProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private RiskResultProducer riskResultProducer;

    @Test
    void shouldSendMessageToRiskEvaluationResultTopic() {
        UUID orderId = UUID.randomUUID();
        RiskEvaluationResult result = RiskEvaluationResult.builder()
                .orderId(orderId)
                .result("PASSED")
                .ruleCode(null)
                .reason(null)
                .timestamp(Instant.now())
                .build();

        riskResultProducer.publishRiskResult(result);

        verify(kafkaTemplate).send("risk.evaluation.result", orderId.toString(), result);
    }

    @Test
    void shouldUseOrderIdAsMessageKey() {
        UUID orderId = UUID.randomUUID();
        RiskEvaluationResult result = RiskEvaluationResult.builder()
                .orderId(orderId)
                .result("REJECTED")
                .ruleCode("SINGLE_ORDER_QTY_LIMIT")
                .reason("Qty exceeds limit")
                .timestamp(Instant.now())
                .build();

        riskResultProducer.publishRiskResult(result);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), valueCaptor.capture());

        assertThat(keyCaptor.getValue()).isEqualTo(orderId.toString());
        assertThat(topicCaptor.getValue()).isEqualTo("risk.evaluation.result");
    }
}
