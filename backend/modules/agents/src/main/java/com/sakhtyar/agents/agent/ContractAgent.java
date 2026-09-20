package com.sakhtyar.agents.agent;

import com.sakhtyar.agents.core.AgentExecutionContext;
import com.sakhtyar.agents.core.AgentRequest;
import com.sakhtyar.agents.core.AgentResult;
import com.sakhtyar.agents.core.AgentStatus;
import com.sakhtyar.agents.core.AgentType;
import com.sakhtyar.agents.core.SakhtyarAgent;
import com.sakhtyar.agents.support.AgentValues;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ContractAgent implements SakhtyarAgent {

    private static final Map<String, List<String>> REQUIRED_CLAUSES = Map.of(
            "PARTIES", List.of("طرفین", "مالک", "سازنده"),
            "PROPERTY", List.of("ملک", "پلاک ثبتی", "مشخصات ملک"),
            "SHARES", List.of("سهم", "درصد", "قدرالسهم"),
            "DURATION", List.of("مدت", "زمان اجرا", "مدت پروژه"),
            "OBLIGATIONS", List.of("تعهدات", "تعهد مالک", "تعهد سازنده"),
            "DELAY_PENALTY", List.of("وجه التزام", "خسارت تاخیر", "خسارت تأخیر"),
            "GUARANTEES", List.of("ضمانت", "تضمین"),
            "TERMINATION", List.of("فسخ", "شرایط فسخ"),
            "UNIT_ALLOCATION", List.of("تقسیم واحد", "تقسیم طبقات", "واحدهای سهم")
    );

    @Override
    public AgentType type() {
        return AgentType.CONTRACT;
    }

    @Override
    public String displayName() {
        return "عامل قرارداد";
    }

    @Override
    public AgentResult execute(AgentRequest request, AgentExecutionContext context) {
        Map<String, Object> structured = AgentValues.map(
                request.parameters().get("contract")
        );
        String text = AgentValues.text(request.parameters(), "contractText");

        if (structured.isEmpty() && (text == null || text.isBlank())) {
            return AgentResult.needsInput(
                    type(),
                    "برای بررسی قرارداد، متن قرارداد یا ساختار قرارداد لازم است.",
                    Map.of(),
                    List.of("contractText یا contract")
            );
        }

        ArrayList<String> present = new ArrayList<>();
        ArrayList<String> missing = new ArrayList<>();
        REQUIRED_CLAUSES.forEach((code, keywords) -> {
            if (present(structured, text, code, keywords)) {
                present.add(code);
            } else {
                missing.add(code);
            }
        });

        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("presentClauses", present);
        data.put("missingClauses", missing);
        data.put("structuredContract", !structured.isEmpty());

        return new AgentResult(
                type(),
                missing.isEmpty() ? AgentStatus.SUCCESS : AgentStatus.PARTIAL,
                missing.isEmpty()
                        ? "ساختار اصلی قرارداد کامل به نظر می‌رسد."
                        : "برخی بخش‌های کلیدی قرارداد پیدا نشد و باید بررسی شوند.",
                data,
                List.of(),
                missing,
                structured.isEmpty() ? 0.60d : 0.85d,
                Instant.now()
        );
    }

    private boolean present(
            Map<String, Object> structured,
            String text,
            String code,
            List<String> keywords
    ) {
        if (structured.containsKey(code)
                || structured.containsKey(code.toLowerCase())) {
            return true;
        }
        for (String key : structured.keySet()) {
            String normalizedKey = key.replace("_", " ").toLowerCase();
            for (String keyword : keywords) {
                if (normalizedKey.contains(keyword.toLowerCase())) {
                    return true;
                }
            }
        }
        if (text == null) {
            return false;
        }
        return keywords.stream().anyMatch(text::contains);
    }
}
