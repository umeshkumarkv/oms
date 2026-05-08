package com.brokerdealer.orderservice.dto.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class OrderListData {
    List<OrderResponse> orders;
    int page;
    int pageSize;
    long totalCount;
    int totalPages;
}
