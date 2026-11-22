package com.sakhtyar.intelligence.application;
import com.sakhtyar.intelligence.domain.IntelligenceComponent;
import org.springframework.stereotype.Component;
import java.util.*;
@Component
public class ComponentRegistry {
    private final Map<String, IntelligenceComponent> components = new LinkedHashMap<>();
    public ComponentRegistry(List<IntelligenceComponent> discovered) { discovered.forEach(c -> components.put(c.descriptor().id(), c)); }
    public Optional<IntelligenceComponent> find(String id) { return Optional.ofNullable(components.get(id)); }
    public Collection<IntelligenceComponent.ComponentDescriptor> descriptors() { return components.values().stream().map(IntelligenceComponent::descriptor).toList(); }
}