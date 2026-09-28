package com.sakhtyar.document.mapper;
import com.sakhtyar.document.api.DocumentDtos.DocumentResponse;
import com.sakhtyar.document.domain.DocumentEntity;
import org.mapstruct.Mapper;
@Mapper(componentModel = "spring")
public interface DocumentMapper { DocumentResponse toResponse(DocumentEntity entity); }
