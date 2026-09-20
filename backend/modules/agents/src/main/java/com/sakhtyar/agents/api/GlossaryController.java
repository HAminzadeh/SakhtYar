package com.sakhtyar.agents.api;

import com.sakhtyar.agents.api.GlossaryDtos.GlossaryDraftRequest;
import com.sakhtyar.agents.glossary.PersianGlossaryService;
import com.sakhtyar.agents.glossary.GlossaryEntryView;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agents/glossary")
public class GlossaryController {

    private final PersianGlossaryService glossary;

    public GlossaryController(PersianGlossaryService glossary) {
        this.glossary = glossary;
    }

    @GetMapping
    public List<GlossaryEntryView> entries() {
        return glossary.entries();
    }

    @GetMapping("/approved")
    public Map<String, String> approvedTerms() {
        return glossary.all();
    }

    @PostMapping("/drafts")
    public GlossaryEntryView createDraft(
            @Valid @RequestBody GlossaryDraftRequest request
    ) {
        return glossary.createDraft(
                request.term(),
                request.meaning(),
                request.aliases()
        );
    }

    @PostMapping("/{id}/approve")
    public GlossaryEntryView approve(@PathVariable UUID id) {
        return glossary.approve(id);
    }

    @PostMapping("/{id}/reject")
    public GlossaryEntryView reject(@PathVariable UUID id) {
        return glossary.reject(id);
    }
}
