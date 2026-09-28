package com.sakhtyar.owner.mapper;
import com.sakhtyar.owner.api.OwnerDtos.OwnerResponse;
import com.sakhtyar.owner.domain.OwnerEntity;
import org.mapstruct.Mapper;
@Mapper(componentModel = "spring")
public interface OwnerMapper { OwnerResponse toResponse(OwnerEntity entity); }
