package com.brokerdealer.marketdataservice.mapper;

import com.brokerdealer.marketdataservice.domain.Instrument;
import com.brokerdealer.marketdataservice.dto.response.InstrumentSummaryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface InstrumentSummaryMapper {

    @Mapping(target = "halted",    source = "tradingHalted")
    @Mapping(target = "bidPrice",  expression = "java(instrument.getBidPrice()  != null ? instrument.getBidPrice().toPlainString()  : null)")
    @Mapping(target = "askPrice",  expression = "java(instrument.getAskPrice()  != null ? instrument.getAskPrice().toPlainString()  : null)")
    @Mapping(target = "lastPrice", expression = "java(instrument.getLastPrice() != null ? instrument.getLastPrice().toPlainString() : null)")
    InstrumentSummaryResponse toSummary(Instrument instrument);
}
