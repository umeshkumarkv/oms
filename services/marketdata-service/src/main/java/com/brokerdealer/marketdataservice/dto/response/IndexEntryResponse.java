package com.brokerdealer.marketdataservice.dto.response;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonDeserialize(builder = IndexEntryResponse.IndexEntryResponseBuilder.class)
public class IndexEntryResponse {

    String name;
    String currentValue;
    String percentChange;

    @JsonPOJOBuilder(withPrefix = "")
    public static class IndexEntryResponseBuilder {
    }
}
