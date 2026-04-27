package com.brokerdealer.marketdataservice.mapper;

import com.brokerdealer.marketdataservice.domain.MarketHoliday;
import com.brokerdealer.marketdataservice.dto.response.MarketHolidayResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MarketHolidayMapper {

    @Mapping(target = "holidayDate",
            expression = "java(holiday.getHolidayDate() != null ? holiday.getHolidayDate().toString() : null)")
    MarketHolidayResponse toResponse(MarketHoliday holiday);
}
