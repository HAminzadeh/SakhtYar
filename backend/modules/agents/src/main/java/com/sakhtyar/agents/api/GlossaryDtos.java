package com.sakhtyar.agents.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class GlossaryDtos {

    private GlossaryDtos() {
    }

    public record GlossaryDraftRequest(
            @NotBlank @Size(max = 200) String term,
            @NotBlank String meaning,
            List<@Size(max = 200) String> aliases
    ) {
    }
}
