package com.brokerdealer.marketdataservice.mapper;

import com.brokerdealer.marketdataservice.domain.Instrument;
import com.brokerdealer.marketdataservice.dto.response.InstrumentResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface InstrumentMapper {

    @Mapping(target = "halted", source = "tradingHalted")
    @Mapping(target = "bidPrice",
            expression = "java(instrument.getBidPrice() != null ? instrument.getBidPrice().toPlainString() : null)")
    @Mapping(target = "askPrice",
            expression = "java(instrument.getAskPrice() != null ? instrument.getAskPrice().toPlainString() : null)")
    @Mapping(target = "lastPrice",
            expression = "java(instrument.getLastPrice() != null ? instrument.getLastPrice().toPlainString() : null)")
    @Mapping(target = "dayHigh",
            expression = "java(instrument.getDayHigh() != null ? instrument.getDayHigh().toPlainString() : null)")
    @Mapping(target = "dayLow",
            expression = "java(instrument.getDayLow() != null ? instrument.getDayLow().toPlainString() : null)")
    @Mapping(target = "previousClose",
            expression = "java(instrument.getPreviousClose() != null ? instrument.getPreviousClose().toPlainString() : null)")
    @Mapping(target = "dataTimestamp",
            expression = "java(instrument.getDataTimestamp() != null ? instrument.getDataTimestamp().toString() : null)")
    InstrumentResponse toResponse(Instrument instrument);
}
