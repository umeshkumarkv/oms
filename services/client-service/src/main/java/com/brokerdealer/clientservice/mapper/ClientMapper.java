package com.brokerdealer.clientservice.mapper;

import com.brokerdealer.clientservice.domain.Client;
import com.brokerdealer.clientservice.dto.response.ClientProfileResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ClientMapper {

    @Mapping(source = "id", target = "clientId")
    ClientProfileResponse toProfileResponse(Client client);
}
