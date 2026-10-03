package com.sakhtyar.agents.api;

import static com.sakhtyar.agents.api.InputGatewayDtos.*;

import com.sakhtyar.agents.input.PersianInputGateway;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/input-gateway")
public class InputGatewayController {

    private final PersianInputGateway gateway;

    public InputGatewayController(PersianInputGateway gateway) {
        this.gateway=gateway;
    }

    @PostMapping("/normalize")
    public GatewayResponse normalize(
            @Valid @RequestBody NormalizeRequest request
    ) {
        return gateway.process(
                request.caseId(),
                request.conversationId(),
                request.text(),
                request.parameters()
        );
    }

    @GetMapping("/requests/{id}")
    public GatewayResponse get(@PathVariable UUID id) {
        return gateway.get(id);
    }

    @GetMapping("/cases/{caseId}")
    public List<GatewayResponse> byCase(@PathVariable UUID caseId) {
        return gateway.byCase(caseId);
    }
}