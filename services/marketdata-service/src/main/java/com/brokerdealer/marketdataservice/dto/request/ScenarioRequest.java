package com.brokerdealer.marketdataservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ScenarioRequest {

    @NotBlank
    private String scenarioId;

    @NotBlank
    private String symbol;
}
