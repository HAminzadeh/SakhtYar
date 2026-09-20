package com.sakhtyar.agents.agent;

import com.sakhtyar.agents.core.AgentExecutionContext;
import com.sakhtyar.agents.core.AgentRequest;
import com.sakhtyar.agents.core.AgentResult;
import com.sakhtyar.agents.core.AgentStatus;
import com.sakhtyar.agents.core.AgentType;
import com.sakhtyar.agents.core.SakhtyarAgent;
import com.sakhtyar.agents.support.AgentValues;
import com.sakhtyar.property.api.PropertyDtos.PropertyResponse;
import com.sakhtyar.property.application.PropertyService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PropertyAgent implements SakhtyarAgent {

    private final PropertyService propertyService;

    public PropertyAgent(PropertyService propertyService) {
        this.propertyService = propertyService;
    }

    @Override
    public AgentType type() {
        return AgentType.PROPERTY;
    }

    @Override
    public String displayName() {
        return "عامل ملک";
    }

    @Override
    public AgentResult execute(AgentRequest request, AgentExecutionContext context) {
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        boolean loadedFromDatabase = false;
        String loadWarning = null;

        if (request.caseId() != null) {
            try {
                PropertyResponse property = propertyService.get(request.caseId());
                putProperty(data, property);
                loadedFromDatabase = true;
            } catch (RuntimeException ex) {
                loadWarning = "پرونده ملک از دیتابیس قابل دریافت نبود: " + ex.getMessage();
            }
        }

        request.parameters().forEach((key, value) -> {
            if (value != null && isPropertyParameter(key)) {
                data.put(key, value);
            }
        });

        if (data.isEmpty()) {
            return new AgentResult(
                    type(),
                    AgentStatus.NEEDS_INPUT,
                    "برای تحلیل ملک، پرونده یا مشخصات اولیه ملک لازم است.",
                    data,
                    loadWarning == null ? List.of() : List.of(loadWarning),
                    List.of("caseId یا landAreaM2/location"),
                    0.0d,
                    Instant.now()
            );
        }

        boolean missingArea = AgentValues.decimal(data, "landAreaM2") == null;
        AgentStatus status = missingArea ? AgentStatus.PARTIAL : AgentStatus.SUCCESS;
        return new AgentResult(
                type(),
                status,
                loadedFromDatabase
                        ? "اطلاعات ملک از پرونده پروژه دریافت شد."
                        : "اطلاعات ملک از پارامترهای درخواست تشکیل شد.",
                data,
                loadWarning == null ? List.of() : List.of(loadWarning),
                missingArea ? List.of("landAreaM2") : List.of(),
                loadedFromDatabase ? 1.0d : 0.75d,
                Instant.now()
        );
    }

    private void putProperty(LinkedHashMap<String, Object> data, PropertyResponse property) {
        AgentValues.putIfPresent(data, "propertyId", property.id());
        AgentValues.putIfPresent(data, "caseId", property.caseId());
        AgentValues.putIfPresent(data, "province", property.province());
        AgentValues.putIfPresent(data, "city", property.city());
        AgentValues.putIfPresent(data, "district", property.district());
        AgentValues.putIfPresent(data, "neighborhood", property.neighborhood());
        AgentValues.putIfPresent(data, "address", property.address());
        AgentValues.putIfPresent(data, "landAreaM2", property.landAreaM2());
        AgentValues.putIfPresent(data, "registryMainNo", property.registryMainNo());
        AgentValues.putIfPresent(data, "registrySubNo", property.registrySubNo());
        AgentValues.putIfPresent(data, "registrySection", property.registrySection());
        AgentValues.putIfPresent(data, "postalCode", property.postalCode());
        AgentValues.putIfPresent(data, "latitude", property.latitude());
        AgentValues.putIfPresent(data, "longitude", property.longitude());
    }

    private boolean isPropertyParameter(String key) {
        return switch (key) {
            case "landAreaM2", "frontageM", "province", "city", "district",
                    "neighborhood", "address", "location", "latitude", "longitude",
                    "registryMainNo", "registrySubNo", "registrySection", "postalCode" -> true;
            default -> false;
        };
    }
}
