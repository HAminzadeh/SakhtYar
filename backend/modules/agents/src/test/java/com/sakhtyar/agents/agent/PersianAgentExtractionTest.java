package com.sakhtyar.agents.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sakhtyar.agents.core.AgentExecutionContext;
import com.sakhtyar.agents.core.AgentRequest;
import com.sakhtyar.agents.core.AgentResult;
import com.sakhtyar.agents.glossary.PersianGlossaryService;
import com.sakhtyar.agents.provider.AiModelRegistry;
import com.sakhtyar.agents.provider.PersianAgentPromptService;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PersianAgentExtractionTest {

    @Test
    void extractsExplicitPropertyFactsEvenWithoutAiProvider() {
        PersianGlossaryService glossary = mock(PersianGlossaryService.class);
        AiModelRegistry modelRegistry = mock(AiModelRegistry.class);
        PersianAgentPromptService promptService =
                mock(PersianAgentPromptService.class);

        when(modelRegistry.activeProvider()).thenReturn(Optional.empty());
        when(glossary.recognizedTerms(anyString())).thenReturn(Map.of());

        PersianAgent agent = new PersianAgent(
                glossary,
                modelRegistry,
                promptService
        );

        AgentRequest request = new AgentRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "یه ملک ۵۰۰ متری تو پیروزی دارم که برای سال ۱۳۵۰ هست دو طبقه و جنوبی هست",
                Map.of()
        );

        AgentExecutionContext context =
                mock(AgentExecutionContext.class);
        AgentResult result = agent.execute(request, context);

        @SuppressWarnings("unchecked")
        Map<String, Object> parameters =
                (Map<String, Object>) result.data()
                        .get("normalizedParameters");

        assertThat(parameters.get("landAreaM2"))
                .isEqualTo(new BigDecimal("500"));
        assertThat(parameters.get("location"))
                .isEqualTo("پیروزی");
        assertThat(parameters.get("constructionYear"))
                .isEqualTo(new BigDecimal("1350"));
        assertThat(parameters.get("existingFloors"))
                .isEqualTo(2);
        assertThat(parameters.get("orientation"))
                .isEqualTo("SOUTH");
    }

    @Test
    void doesNotTreatAllowedFloorsAsExistingFloors() {
        PersianGlossaryService glossary = mock(PersianGlossaryService.class);
        AiModelRegistry modelRegistry = mock(AiModelRegistry.class);
        PersianAgentPromptService promptService =
                mock(PersianAgentPromptService.class);

        when(modelRegistry.activeProvider()).thenReturn(Optional.empty());
        when(glossary.recognizedTerms(anyString())).thenReturn(Map.of());

        PersianAgent agent = new PersianAgent(
                glossary,
                modelRegistry,
                promptService
        );

        AgentRequest request = new AgentRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "این ملک ۵ طبقه مجاز دارد",
                Map.of()
        );

        AgentResult result = agent.execute(
                request,
                mock(AgentExecutionContext.class)
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> parameters =
                (Map<String, Object>) result.data()
                        .get("normalizedParameters");

        assertThat(parameters)
                .doesNotContainKey("existingFloors");
    }
}
