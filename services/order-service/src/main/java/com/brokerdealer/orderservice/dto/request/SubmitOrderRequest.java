package com.brokerdealer.orderservice.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    @Size(min = 1, max = 6, message = "symbol must be 1–6 characters")
    @Pattern(regexp = "^[A-Z]{1,6}$", message = "symbol must contain only uppercase letters A–Z (1–6 characters)")
    private String symbol;

    @NotBlank(message = "side is required")
    private String side;

    @NotBlank(message = "orderType is required")
    private String orderType;

    @JsonProperty("tif")
    @NotBlank(message = "timeInForce is required")
    private String timeInForce;

    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "quantity must be greater than 0")
    private Integer quantity;

    /** Required for LIMIT orders; must be null for MARKET orders. BigDecimal as String. */
    private String limitPrice;

    /** Optional for GTD orders. If provided, sets expiryApplied=TRADER; otherwise defaults to today+180. */
    @JsonProperty("gtdDate")
    private LocalDate expiryDate;

    /** Optional execution venue. Defaults to "NYSE" if absent or blank. */
    private String routingDestination;
}
