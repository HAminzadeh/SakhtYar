package com.sakhtyar.material.application;

import static com.sakhtyar.material.api.MaterialDtos.*;

import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.knowledge.domain.*;
import com.sakhtyar.material.domain.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaterialService {

    private static final String AGGREGATE_ALGORITHM_VERSION = "1.0";

    private final MaterialItemRepository itemRepository;
    private final MaterialVariantRepository variantRepository;
    private final MaterialPriceObservationRepository observationRepository;
    private final MaterialPriceAggregateRepository aggregateRepository;
    private final MaterialPriceAnomalyRepository anomalyRepository;
    private final KnowledgeSourceRepository knowledgeSourceRepository;
    private final KnowledgeTermRepository knowledgeTermRepository;
    private final PriceAnomalyDetector anomalyDetector;
    private final AuditService auditService;

    public MaterialService(
            MaterialItemRepository itemRepository,
            MaterialVariantRepository variantRepository,
            MaterialPriceObservationRepository observationRepository,
            MaterialPriceAggregateRepository aggregateRepository,
            MaterialPriceAnomalyRepository anomalyRepository,
            KnowledgeSourceRepository knowledgeSourceRepository,
            KnowledgeTermRepository knowledgeTermRepository,
            PriceAnomalyDetector anomalyDetector,
            AuditService auditService
    ) {
        this.itemRepository = itemRepository;
        this.variantRepository = variantRepository;
        this.observationRepository = observationRepository;
        this.aggregateRepository = aggregateRepository;
        this.anomalyRepository = anomalyRepository;
        this.knowledgeSourceRepository = knowledgeSourceRepository;
        this.knowledgeTermRepository = knowledgeTermRepository;
        this.anomalyDetector = anomalyDetector;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<MaterialResponse> listMaterials(String query, String category) {
        String q = MaterialFoundation.search(query);
        return itemRepository.findAllByOrderByUpdatedAtDesc().stream()
                .filter(i -> category == null || category.isBlank() || MaterialFoundation.search(i.getCategory()).equals(MaterialFoundation.search(category)))
                .filter(i -> q.isBlank()
                        || MaterialFoundation.search(i.getCode()).contains(q)
                        || MaterialFoundation.search(i.getNameFa()).contains(q)
                        || (i.getNameEn() != null && MaterialFoundation.search(i.getNameEn()).contains(q)))
                .map(MaterialResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public MaterialResponse getMaterial(UUID id) {
        return MaterialResponse.from(requireItem(id));
    }

    @Transactional
    public MaterialResponse createMaterial(UpsertMaterialRequest request, Authentication authentication) {
        String code = MaterialFoundation.code(request.code());
        itemRepository.findByCodeIgnoreCase(code).ifPresent(existing -> {
            throw new IllegalArgumentException("Material code already exists: " + code);
        });
        validateKnowledgeTerm(request.knowledgeTermId());

        Instant now = Instant.now();
        MaterialItemEntity entity = new MaterialItemEntity(
                UUID.randomUUID(), code, MaterialFoundation.required(request.nameFa(), 400), MaterialFoundation.optional(request.nameEn(), 400),
                MaterialFoundation.required(request.category(), 160), MaterialFoundation.unitCode(request.unitCode()),
                request.knowledgeTermId(), request.active(), request.metadata(),
                actor(authentication), now
        );
        itemRepository.save(entity);
        auditService.record("MATERIAL_ITEM", entity.getId(), "MATERIAL_CREATED",
                Map.of("code", entity.getCode(), "category", entity.getCategory()));
        return MaterialResponse.from(entity);
    }

    @Transactional
    public MaterialResponse updateMaterial(UUID id, UpsertMaterialRequest request) {
        MaterialItemEntity entity = requireLockedItem(id);
        if (!entity.getCode().equalsIgnoreCase(request.code().trim())) {
            throw new IllegalArgumentException("Material code is immutable after creation.");
        }
        if (!Objects.equals(entity.getKnowledgeTermId(), request.knowledgeTermId())) validateKnowledgeTerm(request.knowledgeTermId());
        entity.update(
                MaterialFoundation.required(request.nameFa(), 400), MaterialFoundation.optional(request.nameEn(), 400), MaterialFoundation.required(request.category(), 160),
                MaterialFoundation.unchangedUnit(entity.getUnitCode(), request.unitCode()), request.knowledgeTermId(),
                request.active(), request.metadata(), Instant.now()
        );
        auditService.record("MATERIAL_ITEM", entity.getId(), "MATERIAL_UPDATED",
                Map.of("code", entity.getCode(), "active", entity.isActive()));
        return MaterialResponse.from(entity);
    }

    @Transactional(readOnly = true)
    public List<VariantResponse> listVariants(UUID itemId) {
        requireItem(itemId);
        return variantRepository.findByMaterialItemIdOrderByCreatedAtDesc(itemId).stream()
                .map(VariantResponse::from)
                .toList();
    }

    @Transactional
    public VariantResponse createVariant(
            UUID itemId, CreateVariantRequest request, Authentication authentication
    ) {
        MaterialItemEntity item = requireLockedItem(itemId);
        if (!item.isActive()) throw new IllegalArgumentException("Inactive material.");
        Instant now = Instant.now();
        MaterialVariantEntity entity = new MaterialVariantEntity(
                UUID.randomUUID(), itemId, MaterialFoundation.optional(request.brand(), 250), MaterialFoundation.optional(request.model(), 250),
                MaterialFoundation.optional(request.grade(), 160), MaterialFoundation.optional(request.manufacturer(), 250),
                MaterialFoundation.country(request.countryCode()),
                request.attributes(), request.active(), actor(authentication), now
        );
        variantRepository.save(entity);
        auditService.record("MATERIAL_ITEM", itemId, "MATERIAL_VARIANT_CREATED",
                Map.of("variantId", entity.getId().toString()));
        return VariantResponse.from(entity);
    }

    @Transactional
    public VariantResponse updateVariant(UUID itemId, UUID variantId, CreateVariantRequest request) {
        MaterialItemEntity item = requireLockedItem(itemId);
        MaterialVariantEntity variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new IllegalArgumentException("Material variant not found."));
        if (!variant.getMaterialItemId().equals(itemId)) throw new IllegalArgumentException("Variant does not belong to the selected material.");
        if (request.active() && !item.isActive()) throw new IllegalArgumentException("Inactive material.");
        String brand=MaterialFoundation.optional(request.brand(),250), model=MaterialFoundation.optional(request.model(),250);
        String grade=MaterialFoundation.optional(request.grade(),160), manufacturer=MaterialFoundation.optional(request.manufacturer(),250);
        String country=MaterialFoundation.country(request.countryCode());
        Map<String,Object> attributes=request.attributes()==null?Map.of():Map.copyOf(request.attributes());
        boolean changed=!Objects.equals(brand,variant.getBrand()) || !Objects.equals(model,variant.getModel())
                || !Objects.equals(grade,variant.getGrade()) || !Objects.equals(manufacturer,variant.getManufacturer())
                || !Objects.equals(country,variant.getCountryCode()) || !Objects.equals(attributes,variant.getAttributes());
        if(changed && observationRepository.findByMaterialItemIdOrderByObservedAtDesc(itemId).stream()
                .anyMatch(o -> variantId.equals(o.getVariantId())))
            throw new IllegalArgumentException("Referenced variant identity is immutable; create a new variant.");
        // V69 also guards assembly references and direct database writes.
        variant.update(brand,model,grade,manufacturer,country,attributes,request.active(),Instant.now());
        auditService.record("MATERIAL_ITEM",itemId,"MATERIAL_VARIANT_UPDATED",Map.of("variantId",variantId.toString(),"active",variant.isActive()));
        return VariantResponse.from(variant);
    }

    @Transactional
    public PriceObservationResponse createObservation(
            UUID itemId, CreatePriceObservationRequest request, Authentication authentication
    ) {
        MaterialItemEntity item = requireLockedItem(itemId);
        if (!item.isActive()) throw new IllegalArgumentException("Inactive material.");
        if (!knowledgeSourceRepository.existsById(request.sourceId())) {
            throw new IllegalArgumentException("Knowledge source not found.");
        }
        if (request.variantId() != null) {
            MaterialVariantEntity variant = variantRepository.findById(request.variantId())
                    .orElseThrow(() -> new IllegalArgumentException("Material variant not found."));
            if (!variant.isActive()) throw new IllegalArgumentException("Inactive variant.");
            if (!variant.getMaterialItemId().equals(itemId)) {
                throw new IllegalArgumentException("Variant does not belong to the selected material.");
            }
        }

        if (!item.getUnitCode().equalsIgnoreCase(request.unitCode().trim())) {
            throw new IllegalArgumentException(
                    "Observation unit must match material base unit until normalized aggregation is released."
            );
        }

        Instant now = Instant.now();
        MaterialPriceObservationEntity entity = new MaterialPriceObservationEntity(
                UUID.randomUUID(), itemId, request.variantId(), request.priceType(),
                MaterialFoundation.amount(request.amount()), MaterialFoundation.currencyCode(request.currencyCode()),
                item.getUnitCode(), MaterialFoundation.quantity(request.quantityBasis()),
                trim(request.province()), trim(request.city()), request.sourceId(),
                trim(request.sourceUrl()), request.observedAt(), now, MaterialFoundation.confidence(request.confidence()),
                request.origin(), request.rawPayload(), actor(authentication)
        );
        observationRepository.save(entity);

        auditService.record("MATERIAL_PRICE_OBSERVATION", entity.getId(), "PRICE_OBSERVATION_CREATED",
                Map.of(
                        "materialId", itemId.toString(),
                        "amount", entity.getAmount().toPlainString(),
                        "currency", entity.getCurrencyCode(),
                        "status", entity.getReviewStatus().name()
                ));

        return PriceObservationResponse.from(entity);
    }

    @Transactional(readOnly = true)
    public List<PriceObservationResponse> observations(UUID itemId) {
        requireItem(itemId);
        return observationRepository.findByMaterialItemIdOrderByObservedAtDesc(itemId).stream()
                .map(PriceObservationResponse::from)
                .toList();
    }

    @Transactional
    public PriceObservationResponse reviewObservation(
            UUID observationId, ReviewPriceObservationRequest request, Authentication authentication
    ) {
        MaterialPriceObservationEntity entity = observationRepository.findById(observationId)
                .orElseThrow(() -> new IllegalArgumentException("Price observation not found."));

        List<BigDecimal> priorApproved = observationRepository
                .findByMaterialItemIdAndReviewStatusOrderByObservedAtAsc(
                        entity.getMaterialItemId(), KnowledgeReviewStatus.APPROVED
                ).stream()
                .filter(o -> !o.getId().equals(entity.getId()))
                .filter(o -> Objects.equals(o.getVariantId(), entity.getVariantId()))
                .filter(o -> o.getPriceType() == entity.getPriceType())
                .filter(o -> o.getCurrencyCode().equalsIgnoreCase(entity.getCurrencyCode()))
                .filter(o -> o.getUnitCode().equalsIgnoreCase(entity.getUnitCode()))
                .filter(o -> Objects.equals(normalizeRegion(o.getProvince()), normalizeRegion(entity.getProvince())))
                .filter(o -> Objects.equals(normalizeRegion(o.getCity()), normalizeRegion(entity.getCity())))
                .map(MaterialPriceObservationEntity::getAmount)
                .toList();

        String reviewer = actor(authentication);
        entity.review(request.status(), reviewer, trim(request.note()), Instant.now());

        if (request.status() == KnowledgeReviewStatus.APPROVED) {
            anomalyDetector.detect(entity.getAmount(), priorApproved).ifPresent(result -> {
                anomalyRepository.findByObservationId(entity.getId()).orElseGet(() ->
                        anomalyRepository.save(new MaterialPriceAnomalyEntity(
                                UUID.randomUUID(), entity.getId(), result.type(), result.severity(),
                                result.score(), result.reason(), Instant.now()
                        ))
                );
            });
        }

        auditService.record("MATERIAL_PRICE_OBSERVATION", entity.getId(), "PRICE_OBSERVATION_REVIEWED",
                Map.of("status", entity.getReviewStatus().name(), "reviewedBy", reviewer));

        return PriceObservationResponse.from(entity);
    }

    @Transactional
    public List<PriceAggregateResponse> rebuildAggregates(UUID itemId) {
        requireItem(itemId);

        List<MaterialPriceObservationEntity> approved = observationRepository
                .findByMaterialItemIdAndReviewStatusOrderByObservedAtAsc(
                        itemId, KnowledgeReviewStatus.APPROVED
                );

        aggregateRepository.deleteByMaterialItemId(itemId);

        Map<GroupKey, List<MaterialPriceObservationEntity>> groups = new LinkedHashMap<>();
        for (MaterialPriceObservationEntity observation : approved) {
            GroupKey key = new GroupKey(
                    observation.getVariantId(),
                    observation.getPriceType(),
                    observation.getCurrencyCode().toUpperCase(Locale.ROOT),
                    observation.getUnitCode().toUpperCase(Locale.ROOT),
                    normalizeRegion(observation.getProvince()),
                    normalizeRegion(observation.getCity())
            );
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(observation);
        }

        Instant calculatedAt = Instant.now();
        List<MaterialPriceAggregateEntity> saved = new ArrayList<>();

        for (Map.Entry<GroupKey, List<MaterialPriceObservationEntity>> entry : groups.entrySet()) {
            List<MaterialPriceObservationEntity> observations = entry.getValue();
            List<BigDecimal> values = observations.stream()
                    .map(MaterialPriceObservationEntity::getAmount)
                    .toList();

            PriceStatistics.Stats stats = PriceStatistics.calculate(values);
            Instant periodStart = observations.stream()
                    .map(MaterialPriceObservationEntity::getObservedAt)
                    .min(Comparator.naturalOrder())
                    .orElseThrow();
            Instant periodEnd = observations.stream()
                    .map(MaterialPriceObservationEntity::getObservedAt)
                    .max(Comparator.naturalOrder())
                    .orElseThrow();

            GroupKey key = entry.getKey();
            MaterialPriceAggregateEntity aggregate = new MaterialPriceAggregateEntity(
                    UUID.randomUUID(), itemId, key.variantId(), key.priceType(),
                    key.currencyCode(), key.unitCode(), blankToNull(key.province()),
                    blankToNull(key.city()), observations.size(), stats.min(), stats.max(),
                    stats.average(), stats.median(), periodStart, periodEnd,
                    AGGREGATE_ALGORITHM_VERSION, calculatedAt
            );
            saved.add(aggregateRepository.save(aggregate));
        }

        auditService.record("MATERIAL_ITEM", itemId, "PRICE_AGGREGATES_REBUILT",
                Map.of("aggregateCount", saved.size(), "algorithmVersion", AGGREGATE_ALGORITHM_VERSION));

        return saved.stream().map(PriceAggregateResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<PriceAggregateResponse> aggregates(UUID itemId) {
        requireItem(itemId);
        return aggregateRepository.findByMaterialItemIdOrderByCalculatedAtDesc(itemId).stream()
                .map(PriceAggregateResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PriceAnomalyResponse> anomalies(PriceAnomalyStatus status) {
        List<MaterialPriceAnomalyEntity> items = status == null
                ? anomalyRepository.findAll()
                : anomalyRepository.findByStatusOrderByCreatedAtAsc(status);
        return items.stream().map(PriceAnomalyResponse::from).toList();
    }

    @Transactional
    public PriceAnomalyResponse reviewAnomaly(
            UUID anomalyId, ReviewAnomalyRequest request, Authentication authentication
    ) {
        MaterialPriceAnomalyEntity entity = anomalyRepository.findById(anomalyId)
                .orElseThrow(() -> new IllegalArgumentException("Price anomaly not found."));
        entity.review(request.status(), actor(authentication), trim(request.note()), Instant.now());
        auditService.record("MATERIAL_PRICE_ANOMALY", entity.getId(), "PRICE_ANOMALY_REVIEWED",
                Map.of("status", entity.getStatus().name()));
        return PriceAnomalyResponse.from(entity);
    }

    private MaterialItemEntity requireItem(UUID id) {
        return itemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Material not found."));
    }

    private MaterialItemEntity requireLockedItem(UUID id) {
        return itemRepository.findLockedById(id).orElseThrow(() -> new IllegalArgumentException("Material not found."));
    }

    private void validateKnowledgeTerm(UUID termId) {
        if (termId != null) {
            var term=knowledgeTermRepository.findById(termId).orElseThrow(() -> new IllegalArgumentException("Knowledge term not found."));
            if (term.getStatus()!=KnowledgeReviewStatus.APPROVED) throw new IllegalArgumentException("Knowledge term must be approved.");
        }
    }

    private static String actor(Authentication authentication) {
        return authentication == null || authentication.getName() == null
                ? "system"
                : authentication.getName();
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String normalizeRegion(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record GroupKey(
            UUID variantId,
            MaterialPriceType priceType,
            String currencyCode,
            String unitCode,
            String province,
            String city
    ) {}
}