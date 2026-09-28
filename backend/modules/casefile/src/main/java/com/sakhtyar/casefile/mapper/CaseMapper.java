package com.sakhtyar.casefile.mapper;
import com.sakhtyar.casefile.api.CaseDtos.CaseResponse;
import com.sakhtyar.casefile.domain.CaseEntity;
import org.mapstruct.Mapper;
@Mapper(componentModel = "spring")
public interface CaseMapper { CaseResponse toResponse(CaseEntity entity); }
