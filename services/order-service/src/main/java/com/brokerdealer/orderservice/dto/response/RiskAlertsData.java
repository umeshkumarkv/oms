package com.brokerdealer.orderservice.dto.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RiskAlertsData {
    Integer clientsNearLimit;
    Integer cashRejectCount;
    Integer largeOrdersAwaitingReview;
    Integer pendingVenueAck;
}
