package com.sakhtyar.agents.input;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.*;
import org.springframework.stereotype.Component;

@Component
public class PersianInputCanonicalizer {

    private static final Pattern AREA = Pattern.compile(
            "(?:(?:\\u0632\\u0645\\u06CC\\u0646|\\u0645\\u0644\\u06A9|\\u0645\\u0633\\u0627\\u062D\\u062A)\\s*[:=]?\\s*)"
            + "(\\d+(?:\\.\\d+)?)\\s*(?:\\u0645\\u062A\\u0631\\s*\\u0645\\u0631\\u0628\\u0639|\\u0645\\u062A\\u0631\\u0645\\u0631\\u0628\\u0639|\\u0645\\u062A\\u0631\\u06CC)"
    );

    private static final Pattern FRONT = Pattern.compile(
            "(?:(?:\\u0628\\u0631(?:\\s*\\u0645\\u0644\\u06A9)?|\\u0628\\u0631\\u0634|\\u0639\\u0631\\u0636(?:\\s*\\u0645\\u0644\\u06A9)?)\\s*[:=]?\\s*)"
            + "(\\d+(?:\\.\\d+)?)(?:\\s*\\u0645\\u062A\\u0631)?"
    );

    private static final Pattern PASSAGE = Pattern.compile(
            "(?:(?:\\u06AF\\u0630\\u0631|\\u0645\\u0639\\u0628\\u0631)\\s*[:=]?\\s*)"
            + "(\\d+(?:\\.\\d+)?)(?:\\s*(?:\\u0645\\u062A\\u0631\\u06CC|\\u0645\\u062A\\u0631))?"
    );

    public Canonicalization canonicalize(
            String normalizedText,
            Map<String,Object> explicitParameters
    ) {
        LinkedHashMap<String,Object> parameters=new LinkedHashMap<>();
        if (explicitParameters!=null) {
            explicitParameters.forEach((k,v)->{
                if (k!=null && v!=null) parameters.put(k,v);
            });
        }

        putDecimal(parameters,"landAreaM2",AREA,normalizedText);
        putDecimal(parameters,"frontageM",FRONT,normalizedText);
        putDecimal(parameters,"passageWidthM",PASSAGE,normalizedText);

        ArrayList<InputClarification> clarifications=new ArrayList<>();

        if (!parameters.containsKey("currencyUnit")) {
            boolean toman=normalizedText.contains("\u062A\u0648\u0645\u0627\u0646");
            boolean rial=normalizedText.contains("\u0631\u06CC\u0627\u0644");

            if (toman && rial) {
                clarifications.add(new InputClarification(
                        "currencyUnit",
                        "\u0648\u0627\u062D\u062F \u067E\u0648\u0644 \u0631\u0627 \u0645\u0634\u062E\u0635 \u06A9\u0646\u06CC\u062F\u061B \u062A\u0648\u0645\u0627\u0646 \u06CC\u0627 \u0631\u06CC\u0627\u0644."
                ));
            } else if (toman) {
                parameters.put("currencyUnit","TOMAN");
            } else if (rial) {
                parameters.put("currencyUnit","RIAL");
            } else if (hasMoneyContext(normalizedText) && containsDigit(normalizedText)) {
                clarifications.add(new InputClarification(
                        "currencyUnit",
                        "\u0648\u0627\u062D\u062F \u067E\u0648\u0644 \u0631\u0627 \u0645\u0634\u062E\u0635 \u06A9\u0646\u06CC\u062F\u061B \u062A\u0648\u0645\u0627\u0646 \u06CC\u0627 \u0631\u06CC\u0627\u0644."
                ));
            }
        }

        return new Canonicalization(Map.copyOf(parameters),List.copyOf(clarifications));
    }

    private void putDecimal(Map<String,Object> target,String key,Pattern pattern,String text) {
        if (target.containsKey(key)) return;
        Matcher m=pattern.matcher(text);
        if (m.find()) {
            try { target.put(key,new BigDecimal(m.group(1))); }
            catch (NumberFormatException ignored) {}
        }
    }

    private boolean hasMoneyContext(String text) {
        return text.contains("\u0642\u06CC\u0645\u062A")
                || text.contains("\u0647\u0632\u06CC\u0646\u0647")
                || text.contains("\u0627\u0631\u0632\u0634")
                || text.contains("\u0628\u0644\u0627\u0639\u0648\u0636");
    }

    private boolean containsDigit(String text) {
        for (int i=0;i<text.length();i++) {
            if (Character.isDigit(text.charAt(i))) return true;
        }
        return false;
    }

    public record Canonicalization(
            Map<String,Object> parameters,
            List<InputClarification> clarifications
    ) {}
}