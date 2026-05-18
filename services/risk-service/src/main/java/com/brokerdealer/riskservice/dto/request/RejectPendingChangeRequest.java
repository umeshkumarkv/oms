package com.brokerdealer.riskservice.dto.request;

import lombok.Data;

@Data
public class RejectPendingChangeRequest {

    private String rejectionReason;
}
