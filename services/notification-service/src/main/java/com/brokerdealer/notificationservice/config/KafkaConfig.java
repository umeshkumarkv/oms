package com.brokerdealer.notificationservice.config;

import com.brokerdealer.notificationservice.dto.event.ExecutionCompletedEvent;
import com.brokerdealer.notificationservice.dto.event.OrderStateChangedEvent;
import com.brokerdealer.notificationservice.dto.event.RiskEvaluationResultEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.brokerdealer.*");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            KafkaTemplate<String, Object> kafkaTemplate) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setCommonErrorHandler(buildErrorHandler(kafkaTemplate));
        return factory;
    }

    // ── Typed consumer factories (USE_TYPE_HEADERS=false) ─────────────────────
    // notification-service does not have producing services' event classes on the
    // classpath, so we must ignore __TypeId__ headers and deserialize to local DTOs.

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderStateChangedEvent>
            orderStateListenerContainerFactory(KafkaTemplate<String, Object> kafkaTemplate) {
        ConcurrentKafkaListenerContainerFactory<String, OrderStateChangedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(typedConsumerFactory(OrderStateChangedEvent.class));
        factory.setCommonErrorHandler(buildErrorHandler(kafkaTemplate));
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ExecutionCompletedEvent>
            executionListenerContainerFactory(KafkaTemplate<String, Object> kafkaTemplate) {
        ConcurrentKafkaListenerContainerFactory<String, ExecutionCompletedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(typedConsumerFactory(ExecutionCompletedEvent.class));
        factory.setCommonErrorHandler(buildErrorHandler(kafkaTemplate));
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, RiskEvaluationResultEvent>
            riskResultListenerContainerFactory(KafkaTemplate<String, Object> kafkaTemplate) {
        ConcurrentKafkaListenerContainerFactory<String, RiskEvaluationResultEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(typedConsumerFactory(RiskEvaluationResultEvent.class));
        factory.setCommonErrorHandler(buildErrorHandler(kafkaTemplate));
        return factory;
    }

    private <T> ConsumerFactory<String, T> typedConsumerFactory(Class<T> targetType) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.brokerdealer.*");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, targetType.getName());
        return new DefaultKafkaConsumerFactory<>(props);
    }

    private DefaultErrorHandler buildErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxAttempts(3);
        return new DefaultErrorHandler(new DeadLetterPublishingRecoverer(kafkaTemplate), backOff);
    }
}
