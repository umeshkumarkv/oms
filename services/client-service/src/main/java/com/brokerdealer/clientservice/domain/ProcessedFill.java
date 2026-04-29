package com.brokerdealer.clientservice.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "processed_fills", schema = "client_schema")
public class ProcessedFill {

    @Id
    @Column(name = "execution_id", updatable = false, nullable = false)
    private UUID executionId;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;
}
