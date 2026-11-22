package com.sakhtyar.agents.api;

import static com.sakhtyar.agents.api.LearningDtos.*;

import com.sakhtyar.agents.learning.LearningLoopService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/agents/learning")
public class LearningController {

    private final LearningLoopService service;

    public LearningController(LearningLoopService service) {
        this.service=service;
    }

    @PostMapping("/feedback")
    public LearningEventResponse feedback(
            @Valid @RequestBody FeedbackRequest request,
            Authentication authentication
    ) {
        return service.submit(request,authentication);
    }

    @GetMapping("/events/{id}")
    public LearningEventResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/cases/{caseId}")
    public List<LearningEventResponse> byCase(@PathVariable UUID caseId) {
        return service.byCase(caseId);
    }

    @GetMapping("/input-requests/{inputRequestId}")
    public List<LearningEventResponse> byInputRequest(
            @PathVariable UUID inputRequestId
    ) {
        return service.byInputRequest(inputRequestId);
    }
}