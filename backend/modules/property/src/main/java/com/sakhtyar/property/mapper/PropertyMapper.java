package com.sakhtyar.property.mapper;
import com.sakhtyar.property.api.PropertyDtos.PropertyResponse;
import com.sakhtyar.property.domain.PropertyEntity;
import org.mapstruct.Mapper;
@Mapper(componentModel = "spring")
public interface PropertyMapper { PropertyResponse toResponse(PropertyEntity entity); }
