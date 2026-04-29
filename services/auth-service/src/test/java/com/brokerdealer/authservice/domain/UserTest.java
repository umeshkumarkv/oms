package com.brokerdealer.authservice.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    void shouldSetCreatedAtOnPrePersistWhenNull() {
        User user = new User();
        user.onCreate();
        assertThat(user.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldNotOverrideCreatedAtOnPrePersistWhenAlreadySet() {
        Instant existing = Instant.parse("2026-01-01T00:00:00Z");
        User user = User.builder().createdAt(existing).build();
        user.onCreate();
        assertThat(user.getCreatedAt()).isEqualTo(existing);
    }
}
