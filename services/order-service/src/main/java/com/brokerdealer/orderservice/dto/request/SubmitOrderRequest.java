package com.brokerdealer.orderservice.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class SubmitOrderRequest {

    @NotBlank(message = "clientOrderId is required")
    @Size(max = 100, message = "clientOrderId must not exceed 100 characters")
    private String clientOrderId;

    @NotNull(message = "clientId is required")
    private UUID clientId;

    @NotBlank(message = "symbol is required")
    @Size(max = 20, message = "symbol must not exceed 20 characters")
    @Pattern(regexp = "^[A-Z0-9]+$", message = "symbol must contain only uppercase letters and digits")
    private String symbol;

    @NotBlank(message = "side is required")
    private String side;

    @NotBlank(message = "orderType is required")
    private String orderType;

    @NotBlank(message = "timeInForce is required")
    private String timeInForce;

    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "quantity must be greater than 0")
    private Integer quantity;

    /** Required for LIMIT orders; must be null for MARKET orders. BigDecimal as String. */
    private String limitPrice;

    /** Optional for GTC orders. If provided, sets expiryApplied=TRADER; otherwise defaults to today+180. */
    private LocalDate expiryDate;
}
