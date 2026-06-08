package com.brokerdealer.clientservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateDefaultRouteRequest {

    @NotBlank
    private String defaultRoute;
}
