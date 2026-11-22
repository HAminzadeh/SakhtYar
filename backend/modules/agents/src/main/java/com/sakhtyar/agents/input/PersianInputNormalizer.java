package com.sakhtyar.agents.input;

import java.text.Normalizer;
import java.util.Locale;

public final class PersianInputNormalizer {

    public static final String VERSION = "1.0";

    private PersianInputNormalizer() {}

    public static String normalize(String input) {
        if (input == null || input.isBlank()) return "";

        String value = Normalizer.normalize(input, Normalizer.Form.NFKC);
        StringBuilder result = new StringBuilder(value.length());

        for (char c : value.toCharArray()) {
            if ((c >= '\u064B' && c <= '\u065F') || c == '\u0670') continue;

            result.append(switch (c) {
                case '\u064A', '\u0649' -> '\u06CC';
                case '\u0643' -> '\u06A9';
                case '\u06F0', '\u0660' -> '0';
                case '\u06F1', '\u0661' -> '1';
                case '\u06F2', '\u0662' -> '2';
                case '\u06F3', '\u0663' -> '3';
                case '\u06F4', '\u0664' -> '4';
                case '\u06F5', '\u0665' -> '5';
                case '\u06F6', '\u0666' -> '6';
                case '\u06F7', '\u0667' -> '7';
                case '\u06F8', '\u0668' -> '8';
                case '\u06F9', '\u0669' -> '9';
                case '\u066B' -> '.';
                case '\u066C' -> ',';
                case '\u200C', '\u200D' -> ' ';
                default -> c;
            });
        }

        return result.toString()
                .replaceAll("(?<=\\d),(?=\\d)","")
                .replaceAll("\\s+"," ")
                .trim()
                .toLowerCase(Locale.forLanguageTag("fa"));
    }
}