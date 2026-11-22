package com.sakhtyar.agents.api;

import com.sakhtyar.agents.input.InputClarification;
import com.sakhtyar.agents.input.InputGatewayStatus;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import java.util.UUID;

public final class InputGatewayDtos {

    private InputGatewayDtos(){}

    public record NormalizeRequest(
            UUID caseId,
            UUID conversationId,
            @NotBlank @Size(max=20000) String text,
            Map<String,Object> parameters
    ) {}

    public record GatewayResponse(
            UUID id,
            UUID caseId,
            UUID conversationId,
            String rawText,
            String normalizedText,
            String locale,
            InputGatewayStatus status,
            String schemaVersion,
            String normalizerVersion,
            Map<String,Object> canonicalParameters,
            Map<String,Object> recognizedTerms,
            List<InputClarification> clarifications,
            String createdBy,
            Instant createdAt
    ) {}
}