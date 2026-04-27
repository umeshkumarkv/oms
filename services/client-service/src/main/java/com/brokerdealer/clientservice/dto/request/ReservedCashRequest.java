package com.brokerdealer.clientservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReservedCashRequest {

    @NotBlank(message = "adjustment is required")
    private String adjustment;

    @NotNull(message = "reason is required")
    private String reason;
}
