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
public class LegalAgent implements SakhtyarAgent {

    @Override
    public AgentType type() {
        return AgentType.LEGAL;
    }

    @Override
    public String displayName() {
        return "عامل تحلیل حقوقی";
    }

    @Override
    public AgentResult execute(AgentRequest request, AgentExecutionContext context) {
        AgentResult contract = context.resultOf(AgentType.CONTRACT)
                .orElseGet(() -> context.call(AgentType.CONTRACT, request));

        if (contract.status() == AgentStatus.NEEDS_INPUT) {
            return AgentResult.needsInput(
                    type(),
                    "تحلیل حقوقی بدون قرارداد یا اطلاعات حقوقی پایه قابل انجام نیست.",
                    Map.of(),
                    contract.missingFields()
            );
        }

        List<String> missingClauses = AgentValues.stringList(
                contract.data().get("missingClauses")
        );
        ArrayList<Map<String, Object>> findings = new ArrayList<>();

        for (String clause : missingClauses) {
            findings.add(finding(
                    severityFor(clause),
                    "MISSING_" + clause,
                    messageFor(clause)
            ));
        }

        String contractText = AgentValues.text(request.parameters(), "contractText");
        if (contractText != null && contractText.contains("وکالت بلاعزل")) {
            findings.add(finding(
                    "REVIEW",
                    "IRREVOCABLE_POA_REVIEW",
                    "وجود عبارت «وکالت بلاعزل» شناسایی شد؛ حدود اختیار، مدت و شرایط آن باید توسط متخصص حقوقی بررسی شود."
            ));
        }

        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("findings", findings);
        data.put("findingCount", findings.size());
        data.put("disclaimer", "این تحلیل کمک‌یار است و جایگزین وکیل یا کارشناس حقوقی رسمی نیست.");

        return new AgentResult(
                type(),
                findings.isEmpty() ? AgentStatus.SUCCESS : AgentStatus.PARTIAL,
                findings.isEmpty()
                        ? "در کنترل ساختاری فعلی، هشدار حقوقی پایه‌ای شناسایی نشد."
                        : "مواردی برای بررسی حقوقی بیشتر شناسایی شد.",
                data,
                List.of("برای تصمیم حقوقی نهایی، بررسی متخصص انسانی ضروری است."),
                List.of(),
                0.70d,
                Instant.now()
        );
    }

    private Map<String, Object> finding(String severity, String code, String message) {
        LinkedHashMap<String, Object> finding = new LinkedHashMap<>();
        finding.put("severity", severity);
        finding.put("code", code);
        finding.put("message", message);
        return finding;
    }

    private String severityFor(String clause) {
        return switch (clause) {
            case "DELAY_PENALTY", "GUARANTEES", "TERMINATION" -> "HIGH_REVIEW";
            default -> "REVIEW";
        };
    }

    private String messageFor(String clause) {
        return switch (clause) {
            case "PARTIES" -> "مشخصات و هویت کامل طرفین قرارداد باید روشن باشد.";
            case "PROPERTY" -> "مشخصات دقیق ملک و اطلاعات ثبتی باید در قرارداد مشخص باشد.";
            case "SHARES" -> "سهم مالک و سازنده به‌صورت دقیق و قابل اندازه‌گیری درج نشده است.";
            case "DURATION" -> "مدت و نقاط زمانی اصلی پروژه باید مشخص شوند.";
            case "OBLIGATIONS" -> "تعهدات مالک و سازنده باید جداگانه و روشن ثبت شوند.";
            case "DELAY_PENALTY" -> "سازوکار جبران تأخیر یا وجه التزام باید بررسی و تکمیل شود.";
            case "GUARANTEES" -> "نوع و حدود تضمین‌های اجرای تعهدات باید روشن باشد.";
            case "TERMINATION" -> "شرایط فسخ و آثار آن باید به‌صورت صریح تعیین شود.";
            case "UNIT_ALLOCATION" -> "نحوه تقسیم واحدها یا طبقات باید دقیق مشخص شود.";
            default -> "این بند قراردادی نیازمند بررسی و تکمیل است.";
        };
    }
}
