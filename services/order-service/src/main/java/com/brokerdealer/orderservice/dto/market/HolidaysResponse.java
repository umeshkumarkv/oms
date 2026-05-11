package com.brokerdealer.orderservice.dto.market;

import lombok.Data;

import java.util.List;

@Data
public class HolidaysResponse {
    private List<HolidayItem> data;

    @Data
    public static class HolidayItem {
        private String holidayDate;
        private String holidayName;
    }
}