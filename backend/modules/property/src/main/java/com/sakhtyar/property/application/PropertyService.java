package com.sakhtyar.property.application;

import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.casefile.domain.CaseEntity;
import com.sakhtyar.casefile.domain.CaseRepository;
import com.sakhtyar.property.api.PropertyDtos.MergePropertyFactsRequest;
import com.sakhtyar.property.api.PropertyDtos.PropertyResponse;
import com.sakhtyar.property.api.PropertyDtos.UpsertPropertyRequest;
import com.sakhtyar.property.domain.PropertyEntity;
import com.sakhtyar.property.domain.PropertyRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PropertyService {

    private static final int MAX_ATTRIBUTE_KEYS = 150;
    private static final int MAX_JSON_DEPTH = 5;
    private static final int MAX_COLLECTION_SIZE = 100;
    private static final int MAX_STRING_LENGTH = 4000;

    private static final Set<String> CANONICAL_FACT_KEYS = Set.of(
            "province",
            "city",
            "district",
            "neighborhood",
            "address",
            "landAreaM2",
            "frontageM",
            "passageWidthM",
            "buildingAreaM2",
            "constructionYear",
            "existingFloors",
            "existingUnits",
            "orientation",
            "propertyType",
            "buildingCondition",
            "registryMainNo",
            "registrySubNo",
            "registrySection",
            "postalCode",
            "latitude",
            "longitude"
    );

    private static final Set<String> ORIENTATIONS = Set.of(
            "NORTH",
            "SOUTH",
            "EAST",
            "WEST",
            "NORTH_EAST",
            "NORTH_WEST",
            "SOUTH_EAST",
            "SOUTH_WEST"
    );

    private final PropertyRepository repository;
    private final CaseRepository caseRepository;
    private final AuditService auditService;

    public PropertyService(
            PropertyRepository repository,
            CaseRepository caseRepository,
            AuditService auditService
    ) {
        this.repository = repository;
        this.caseRepository = caseRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public PropertyResponse get(UUID caseId) {
        requireCase(caseId);

        PropertyEntity entity = repository.findByCaseId(caseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Property not found for case."
                ));

        return PropertyResponse.from(entity);
    }

    @Transactional
    public PropertyResponse upsert(
            UUID caseId,
            UpsertPropertyRequest request
    ) {
        CaseEntity caseEntity = requireCase(caseId);

        PropertyEntity entity =
                repository.findByCaseId(caseId).orElse(null);
        boolean created = entity == null;

        Map<String, Object> attributes = created
                ? mergeAttributes(Map.of(), request.attributes())
                : mergeAttributes(
                        entity.getAttributes(),
                        request.attributes()
                );

        if (created) {
            Instant now = Instant.now();
            entity = new PropertyEntity(
                    UUID.randomUUID(),
                    caseId,
                    clean(request.province()),
                    clean(request.city()),
                    clean(request.district()),
                    clean(request.neighborhood()),
                    clean(request.address()),
                    request.landAreaM2(),
                    request.frontageM(),
                    request.passageWidthM(),
                    request.buildingAreaM2(),
                    request.constructionYear(),
                    request.existingFloors(),
                    request.existingUnits(),
                    normalizeOrientation(request.orientation()),
                    clean(request.propertyType()),
                    clean(request.buildingCondition()),
                    clean(request.registryMainNo()),
                    clean(request.registrySubNo()),
                    clean(request.registrySection()),
                    clean(request.postalCode()),
                    request.latitude(),
                    request.longitude(),
                    attributes,
                    now,
                    now
            );
        } else {
            entity.update(
                    clean(request.province()),
                    clean(request.city()),
                    clean(request.district()),
                    clean(request.neighborhood()),
                    clean(request.address()),
                    request.landAreaM2(),
                    request.frontageM(),
                    request.passageWidthM(),
                    request.buildingAreaM2(),
                    request.constructionYear(),
                    request.existingFloors(),
                    request.existingUnits(),
                    normalizeOrientation(request.orientation()),
                    clean(request.propertyType()),
                    clean(request.buildingCondition()),
                    clean(request.registryMainNo()),
                    clean(request.registrySubNo()),
                    clean(request.registrySection()),
                    clean(request.postalCode()),
                    request.latitude(),
                    request.longitude(),
                    attributes
            );
        }

        repository.save(entity);
        syncCaseSnapshot(caseEntity, entity);

        auditService.record(
                "PROPERTY",
                entity.getId(),
                created ? "PROPERTY_CREATED" : "PROPERTY_UPDATED",
                Map.of(
                        "caseId", caseId.toString(),
                        "propertyId", entity.getId().toString(),
                        "attributeKeys",
                        sortedKeys(entity.getAttributes())
                )
        );

        return PropertyResponse.from(entity);
    }

    @Transactional
    public PropertyResponse mergeFacts(
            UUID caseId,
            MergePropertyFactsRequest request
    ) {
        CaseEntity caseEntity = requireCase(caseId);
        Map<String, Object> facts = request == null
                || request.facts() == null
                ? Map.of()
                : request.facts();

        if (facts.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "At least one property fact is required."
            );
        }
        if (facts.size() > MAX_ATTRIBUTE_KEYS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Too many property facts in one request."
            );
        }

        PropertyEntity entity =
                repository.findByCaseId(caseId).orElse(null);
        boolean created = entity == null;

        if (created) {
            Instant now = Instant.now();
            entity = new PropertyEntity(
                    UUID.randomUUID(),
                    caseId,
                    null, null, null, null, null,
                    null, null, null, null,
                    null, null, null,
                    null, null, null,
                    null, null, null, null,
                    null, null,
                    Map.of(),
                    now,
                    now
            );
        }

        LinkedHashMap<String, Object> extraAttributes =
                new LinkedHashMap<>(entity.getAttributes());

        for (Map.Entry<String, Object> entry : facts.entrySet()) {
            String key = validateAttributeKey(entry.getKey());

            if (CANONICAL_FACT_KEYS.contains(key)) {
                continue;
            }

            Object raw = entry.getValue();
            if (raw == null) {
                extraAttributes.remove(key);
            } else {
                extraAttributes.put(
                        key,
                        sanitizeJsonValue(raw, 0)
                );
            }
        }

        entity.update(
                stringFact(
                        facts, "province", entity.getProvince(), 100
                ),
                stringFact(
                        facts, "city", entity.getCity(), 100
                ),
                stringFact(
                        facts, "district", entity.getDistrict(), 100
                ),
                stringFact(
                        facts,
                        "neighborhood",
                        entity.getNeighborhood(),
                        150
                ),
                stringFact(
                        facts, "address", entity.getAddress(), 4000
                ),
                decimalFact(
                        facts, "landAreaM2", entity.getLandAreaM2()
                ),
                decimalFact(
                        facts, "frontageM", entity.getFrontageM()
                ),
                decimalFact(
                        facts,
                        "passageWidthM",
                        entity.getPassageWidthM()
                ),
                decimalFact(
                        facts,
                        "buildingAreaM2",
                        entity.getBuildingAreaM2()
                ),
                integerFact(
                        facts,
                        "constructionYear",
                        entity.getConstructionYear(),
                        1000,
                        2500
                ),
                integerFact(
                        facts,
                        "existingFloors",
                        entity.getExistingFloors(),
                        0,
                        200
                ),
                integerFact(
                        facts,
                        "existingUnits",
                        entity.getExistingUnits(),
                        0,
                        10000
                ),
                orientationFact(
                        facts,
                        "orientation",
                        entity.getOrientation()
                ),
                stringFact(
                        facts,
                        "propertyType",
                        entity.getPropertyType(),
                        80
                ),
                stringFact(
                        facts,
                        "buildingCondition",
                        entity.getBuildingCondition(),
                        80
                ),
                stringFact(
                        facts,
                        "registryMainNo",
                        entity.getRegistryMainNo(),
                        100
                ),
                stringFact(
                        facts,
                        "registrySubNo",
                        entity.getRegistrySubNo(),
                        100
                ),
                stringFact(
                        facts,
                        "registrySection",
                        entity.getRegistrySection(),
                        100
                ),
                stringFact(
                        facts,
                        "postalCode",
                        entity.getPostalCode(),
                        20
                ),
                rangedDecimalFact(
                        facts,
                        "latitude",
                        entity.getLatitude(),
                        BigDecimal.valueOf(-90),
                        BigDecimal.valueOf(90)
                ),
                rangedDecimalFact(
                        facts,
                        "longitude",
                        entity.getLongitude(),
                        BigDecimal.valueOf(-180),
                        BigDecimal.valueOf(180)
                ),
                extraAttributes
        );

        repository.save(entity);
        syncCaseSnapshot(caseEntity, entity);

        auditService.record(
                "PROPERTY",
                entity.getId(),
                "PROPERTY_FACTS_MERGED",
                Map.of(
                        "caseId", caseId.toString(),
                        "propertyId", entity.getId().toString(),
                        "factKeys", sortedKeys(facts),
                        "extraAttributeKeys",
                        sortedKeys(extraAttributes)
                )
        );

        return PropertyResponse.from(entity);
    }

    private void syncCaseSnapshot(
            CaseEntity caseEntity,
            PropertyEntity entity
    ) {
        caseEntity.syncPropertySnapshot(
                entity.getCity(),
                entity.getDistrict(),
                entity.getAddress(),
                entity.getLandAreaM2()
        );
    }

    private Map<String, Object> mergeAttributes(
            Map<String, Object> current,
            Map<String, Object> patch
    ) {
        LinkedHashMap<String, Object> result =
                new LinkedHashMap<>(
                        current == null ? Map.of() : current
                );

        if (patch == null) {
            return result;
        }

        if (patch.size() > MAX_ATTRIBUTE_KEYS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Too many property attributes."
            );
        }

        for (Map.Entry<String, Object> entry : patch.entrySet()) {
            String key = validateAttributeKey(entry.getKey());
            Object value = entry.getValue();

            if (value == null) {
                result.remove(key);
            } else {
                result.put(
                        key,
                        sanitizeJsonValue(value, 0)
                );
            }
        }

        return result;
    }

    private Object sanitizeJsonValue(Object value, int depth) {
        if (depth > MAX_JSON_DEPTH) {
            throw badAttribute("JSON nesting is too deep.");
        }

        if (value == null
                || value instanceof Boolean
                || value instanceof Number) {
            return value;
        }

        if (value instanceof String text) {
            if (text.length() > MAX_STRING_LENGTH) {
                throw badAttribute("Attribute string is too long.");
            }
            return text;
        }

        if (value instanceof Map<?, ?> rawMap) {
            if (rawMap.size() > MAX_ATTRIBUTE_KEYS) {
                throw badAttribute("Nested object has too many keys.");
            }

            LinkedHashMap<String, Object> result =
                    new LinkedHashMap<>();

            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                String key = validateAttributeKey(
                        String.valueOf(entry.getKey())
                );
                Object nested = entry.getValue();

                if (nested != null) {
                    result.put(
                            key,
                            sanitizeJsonValue(nested, depth + 1)
                    );
                }
            }
            return result;
        }

        if (value instanceof Collection<?> collection) {
            if (collection.size() > MAX_COLLECTION_SIZE) {
                throw badAttribute("Attribute list is too large.");
            }

            ArrayList<Object> result = new ArrayList<>();
            for (Object item : collection) {
                result.add(
                        sanitizeJsonValue(item, depth + 1)
                );
            }
            return result;
        }

        throw badAttribute(
                "Unsupported JSON attribute value: "
                        + value.getClass().getSimpleName()
        );
    }

    private String validateAttributeKey(String key) {
        if (key == null) {
            throw badAttribute("Attribute key is required.");
        }

        String value = key.trim();
        if (value.isEmpty()
                || value.length() > 100
                || value.startsWith("_")) {
            throw badAttribute("Invalid property attribute key.");
        }
        return value;
    }

    private String stringFact(
            Map<String, Object> facts,
            String key,
            String current,
            int maxLength
    ) {
        if (!facts.containsKey(key) || facts.get(key) == null) {
            return current;
        }

        String value = String.valueOf(facts.get(key)).trim();
        if (value.isEmpty()) {
            return current;
        }
        if (value.length() > maxLength) {
            throw badFact(key, "text is too long");
        }
        return value;
    }

    private BigDecimal decimalFact(
            Map<String, Object> facts,
            String key,
            BigDecimal current
    ) {
        if (!facts.containsKey(key) || facts.get(key) == null) {
            return current;
        }

        BigDecimal value = toDecimal(facts.get(key), key);
        if (value.signum() <= 0) {
            throw badFact(key, "must be greater than zero");
        }
        return value;
    }

    private BigDecimal rangedDecimalFact(
            Map<String, Object> facts,
            String key,
            BigDecimal current,
            BigDecimal min,
            BigDecimal max
    ) {
        if (!facts.containsKey(key) || facts.get(key) == null) {
            return current;
        }

        BigDecimal value = toDecimal(facts.get(key), key);
        if (value.compareTo(min) < 0
                || value.compareTo(max) > 0) {
            throw badFact(key, "is out of range");
        }
        return value;
    }

    private Integer integerFact(
            Map<String, Object> facts,
            String key,
            Integer current,
            int min,
            int max
    ) {
        if (!facts.containsKey(key) || facts.get(key) == null) {
            return current;
        }

        Object raw = facts.get(key);
        int value;

        try {
            if (raw instanceof Number number) {
                value = new BigDecimal(
                        number.toString()
                ).intValueExact();
            } else {
                value = new BigDecimal(
                        String.valueOf(raw).trim()
                ).intValueExact();
            }
        } catch (RuntimeException ex) {
            throw badFact(key, "must be an integer");
        }

        if (value < min || value > max) {
            throw badFact(key, "is out of range");
        }
        return value;
    }

    private String orientationFact(
            Map<String, Object> facts,
            String key,
            String current
    ) {
        if (!facts.containsKey(key) || facts.get(key) == null) {
            return current;
        }

        String value = normalizeOrientation(
                String.valueOf(facts.get(key))
        );

        if (value == null) {
            throw badFact(key, "has an unsupported value");
        }
        return value;
    }

    private BigDecimal toDecimal(Object raw, String key) {
        try {
            if (raw instanceof BigDecimal decimal) {
                return decimal;
            }
            return new BigDecimal(
                    String.valueOf(raw).trim()
            );
        } catch (NumberFormatException ex) {
            throw badFact(key, "must be numeric");
        }
    }

    private String normalizeOrientation(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase();

        return ORIENTATIONS.contains(normalized)
                ? normalized
                : null;
    }

    private ResponseStatusException badFact(
            String key,
            String message
    ) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid property fact '" + key + "': " + message
        );
    }

    private ResponseStatusException badAttribute(String message) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message
        );
    }

    private CaseEntity requireCase(UUID caseId) {
        return caseRepository.findById(caseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Case not found."
                ));
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<String> sortedKeys(Map<String, ?> values) {
        return values.keySet().stream()
                .sorted(Comparator.naturalOrder())
                .toList();
    }
}
