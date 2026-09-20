package com.sakhtyar.agents.provider;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class PersianAgentPromptService {

    private static final String PROMPT_PATH = "prompts/persian-agent-system.txt";
    private final String systemPrompt;

    public PersianAgentPromptService() {
        this.systemPrompt = loadPrompt();
    }

    public String systemPrompt() {
        return systemPrompt;
    }

    private String loadPrompt() {
        ClassPathResource resource = new ClassPathResource(PROMPT_PATH);
        try (InputStream input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Could not load Persian Agent prompt: " + PROMPT_PATH,
                    ex
            );
        }
    }
}
