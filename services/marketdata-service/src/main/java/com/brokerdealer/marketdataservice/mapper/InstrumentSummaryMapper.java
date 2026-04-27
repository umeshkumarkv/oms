package com.brokerdealer.marketdataservice.mapper;

import com.brokerdealer.marketdataservice.domain.Instrument;
import com.brokerdealer.marketdataservice.dto.response.InstrumentSummaryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface InstrumentSummaryMapper {

    @Mapping(target = "halted", source = "tradingHalted")
    InstrumentSummaryResponse toSummary(Instrument instrument);
}
