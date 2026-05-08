package com.brokerdealer.orderservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class SchedulingConfig {

    @Bean
    Clock etClock() {
        return Clock.system(ZoneId.of("America/New_York"));
    }
}
