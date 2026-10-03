package com.sakhtyar.agents.api;

import com.sakhtyar.agents.provider.AiGateway;
import com.sakhtyar.agents.provider.AiModelProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/ai/playground")
public class AiPlaygroundController {

    private final AiGateway gateway;

    public AiPlaygroundController(AiGateway gateway) {
        this.gateway = gateway;
    }

    @PostMapping("/run")
    public Map<String, Object> run(
            @Valid @RequestBody PlaygroundRequest request
    ) {
        String agentCode = request.agentCode().trim().toUpperCase();
        String systemPrompt = request.systemPrompt() == null
                || request.systemPrompt().isBlank()
                ? """
                  You are a SakhtYar AI playground assistant.
                  Respond concisely and use structured JSON when the user asks
                  for structured data. Never invent project-specific facts.
                  """
                : request.systemPrompt().trim();

        var execution = gateway.generate(
                agentCode,
                new AiModelProvider.AiModelRequest(
                        systemPrompt,
                        request.input().trim(),
                        Map.of(
                                "source", "admin-playground",
                                "agentCode", agentCode
                        )
                )
        ).orElseThrow(() ->
                new IllegalStateException(
                        "No healthy executable AI provider is available for this route."
                )
        );

        var response = execution.response();

        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("agentCode", agentCode);
        result.put("provider", execution.provider());
        result.put("model", response.model());
        result.put("fallbackUsed", execution.fallbackUsed());
        result.put("latencyMs", execution.latencyMs());
        result.put("inputTokens", response.inputTokens());
        result.put("outputTokens", response.outputTokens());
        result.put("rawText", response.text());
        result.put("structuredData", response.structuredData());
        return result;
    }

    public record PlaygroundRequest(
            @NotBlank String agentCode,
            String systemPrompt,
            @NotBlank String input
    ) {
    }
}