package com.brokerdealer.orderservice.dto.market;

import lombok.Data;

/**
 * Envelope DTO for GET /api/v1/market/{symbol} response from marketdata-service.
 */
@Data
public class InstrumentResponse {
    private InstrumentData data;

    @Data
    public static class InstrumentData {
        private String symbol;
        private String lastPrice;
        private Boolean tradable;
        private Boolean halted;
        private Integer minQuantity;
        private Integer maxQuantity;
    }
}
