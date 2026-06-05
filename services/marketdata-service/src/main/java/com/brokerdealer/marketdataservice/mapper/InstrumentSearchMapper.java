package com.brokerdealer.marketdataservice.mapper;

import com.brokerdealer.marketdataservice.domain.Instrument;
import com.brokerdealer.marketdataservice.dto.response.InstrumentSearchResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface InstrumentSearchMapper {

    @Mapping(target = "lastPrice",
            expression = "java(instrument.getLastPrice() != null ? instrument.getLastPrice().toPlainString() : null)")
    InstrumentSearchResponse toSearchResponse(Instrument instrument);
}
