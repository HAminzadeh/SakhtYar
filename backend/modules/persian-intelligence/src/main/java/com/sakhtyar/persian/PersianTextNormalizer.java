package com.sakhtyar.persian;

import org.springframework.stereotype.Component;
import java.text.Normalizer;

@Component
public final class PersianTextNormalizer {
    public String normalize(String input) {
        if (input == null || input.isBlank()) return input;
        return Normalizer.normalize(input, Normalizer.Form.NFKC)
                .replace('ي','ی').replace('ى','ی').replace('ك','ک')
                .replace("\u200E","").replace("\u200F","").replace("\uFEFF","")
                .replaceAll("[\\t\\u00A0 ]+", " ").trim();
    }
}