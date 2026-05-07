package com.brokerdealer.executionservice.mapper;

import com.brokerdealer.executionservice.domain.Execution;
import com.brokerdealer.executionservice.dto.response.ExecutionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ExecutionMapper {

    @Mapping(source = "id", target = "executionId")
    @Mapping(target = "orderId", expression = "java(execution.getOrderId().toString())")
    @Mapping(target = "side", expression = "java(execution.getSide().name())")
    @Mapping(target = "executedPrice", expression = "java(execution.getExecutedPrice().toPlainString())")
    @Mapping(target = "fillType", expression = "java(execution.getFillType().name())")
    @Mapping(target = "fillValue", expression = "java(execution.getFillValue().toPlainString())")
    @Mapping(target = "marketPriceAtFill", expression = "java(execution.getMarketPriceAtFill().toPlainString())")
    @Mapping(target = "executedAt", expression = "java(execution.getExecutedAt().toString())")
    ExecutionResponse toResponse(Execution execution);
}
