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
        if (request == null || request.priceType() == null || request.origin() == null || request.sourceId() == null)
            throw new IllegalArgumentException("Price type, origin and source are required.");
        requireActiveSource(request.sourceId(),itemId);
        if(request.origin()==PriceObservationOrigin.SYSTEM) throw new IllegalArgumentException("SYSTEM origin cannot be asserted by a price submission.");
        if (request.variantId() != null) {
            MaterialVariantEntity variant = variantRepository.findById(request.variantId())
                    .orElseThrow(() -> new IllegalArgumentException("Material variant not found."));
            if (!variant.isActive() || !variant.getMaterialItemId().equals(itemId))
                throw new IllegalArgumentException("Invalid or inactive material variant.");
        }
        Instant now = Instant.now();
        String currency=MaterialFoundation.currencyCode(request.currencyCode());
        if (!observationRepository.activeCurrency(currency)) throw new IllegalArgumentException("Unknown or inactive currency.");
        String unit=MaterialFoundation.unitCode(request.unitCode());
        BigDecimal amount=MaterialFoundation.amount(request.amount()), quantity=MaterialFoundation.quantity(request.quantityBasis());
        MaterialPricePolicy.unitPrice(amount,quantity,unit,item.getUnitCode(),currency,currency);
        String province=MaterialPricePolicy.regionValue(request.province()), city=MaterialPricePolicy.regionValue(request.city());
        if(city!=null && province==null) throw new IllegalArgumentException("City requires province.");
        String url=MaterialPricePolicy.sourceUrl(request.sourceUrl(),request.origin()==PriceObservationOrigin.CRAWLER || request.origin()==PriceObservationOrigin.API);
        if(request.origin()==PriceObservationOrigin.IMPORT && url==null && (request.rawPayload()==null || request.rawPayload().isEmpty()))
            throw new IllegalArgumentException("Import requires a reference URL or raw evidence.");
        Instant observed=MaterialPricePolicy.observationTime(request.observedAt(),now);
        var entity = new MaterialPriceObservationEntity(UUID.randomUUID(), itemId, request.variantId(), request.priceType(),
                amount,currency,unit,quantity,province,city,request.sourceId(),url,observed,now,
                MaterialFoundation.confidence(request.confidence()),request.origin(),request.rawPayload(),reviewActor(authentication));
        String key=quoteKey(entity,item);
        // Parent pessimistic lock serializes all application writers for this material, including legacy deduplication.
        var matches=observationRepository.findByMaterialItemIdAndQuoteKey(itemId,key).stream().toList();
        if(matches.isEmpty()) matches=observationRepository.findLegacyCandidates(itemId,request.sourceId(),observed).stream()
                .filter(o -> key.equals(safeQuoteKey(o,item))).sorted(Comparator.comparing(MaterialPriceObservationEntity::getId)).toList();
        if(!matches.isEmpty()) {
            var previous=matches.getFirst();
            if(!Objects.equals(previous.getSourceUrl(),entity.getSourceUrl()) || previous.getOrigin()!=entity.getOrigin()
                    || !Objects.equals(previous.getRawPayload(),entity.getRawPayload()) || !Objects.equals(MaterialFoundation.confidence(previous.getConfidence()),entity.getConfidence()))
                throw new IllegalArgumentException("Duplicate quote has different evidence; original evidence retained.");
            return PriceObservationResponse.from(previous);
        }
        entity.assignQuoteKey(key);
        observationRepository.save(entity);
        auditService.record("MATERIAL_PRICE_OBSERVATION",entity.getId(),"PRICE_OBSERVATION_CREATED",Map.of("materialId",itemId.toString(),"status",entity.getReviewStatus().name(),"origin",entity.getOrigin().name()));
        return PriceObservationResponse.from(entity);
    }

    @Transactional(readOnly = true)
    public List<PriceObservationResponse> observations(UUID itemId) {
        requireItem(itemId);
        return observationRepository.findByMaterialItemIdOrderByObservedAtDesc(itemId).stream().map(PriceObservationResponse::from).toList();
    }

    @Transactional
    public PriceObservationResponse reviewObservation(UUID observationId,ReviewPriceObservationRequest request,Authentication authentication) {
        UUID itemId=observationRepository.findMaterialId(observationId).orElseThrow(() -> new IllegalArgumentException("Price observation not found."));
        MaterialItemEntity item=requireLockedItem(itemId);
        var entity=observationRepository.findById(observationId).orElseThrow(() -> new IllegalArgumentException("Price observation not found."));
        String reviewer=reviewActor(authentication);
        var target=request.status();
        if(target!=KnowledgeReviewStatus.APPROVED && target!=KnowledgeReviewStatus.REJECTED) throw new IllegalArgumentException("Invalid price review decision.");
        if(entity.getReviewStatus()==target) return PriceObservationResponse.from(entity);
        if(entity.getReviewStatus()!=KnowledgeReviewStatus.PENDING_REVIEW && !(entity.getReviewStatus()==KnowledgeReviewStatus.APPROVED && target==KnowledgeReviewStatus.REJECTED))
            throw new IllegalArgumentException("Price review transition is not allowed.");
        String note=MaterialFoundation.required(request.note(),4000);
        Instant now=Instant.now();
        if(target==KnowledgeReviewStatus.APPROVED) {
            requireActiveSource(entity.getSourceId(),itemId);
            if(!item.isActive() || !activeVariant(entity)) throw new IllegalArgumentException("Inactive material or variant.");
            MaterialPricePolicy.observationTime(entity.getObservedAt(),now);
            String groupCurrency=MaterialPricePolicy.currencyGroup(entity.getCurrencyCode());
            BigDecimal candidate=normalized(entity,item,groupCurrency);
            var history=eligible(item,entity.getObservedAt(),true).stream()
                    .filter(o -> !o.getId().equals(entity.getId()) && group(o,item).equals(group(entity,item)))
                    .map(o -> normalized(o,item,groupCurrency)).toList();
            anomalyDetector.detect(candidate,history).ifPresent(result -> {
                if(anomalyRepository.findByObservationId(entity.getId()).isEmpty())
                    anomalyRepository.save(new MaterialPriceAnomalyEntity(UUID.randomUUID(),entity.getId(),result.type(),result.severity(),
                            result.score().min(new BigDecimal("999999.9999")),result.reason(),now));
            });
        }
        entity.review(target,reviewer,note,now);
        aggregateRepository.retirePublished(itemId);
        auditService.record("MATERIAL_PRICE_OBSERVATION",entity.getId(),"PRICE_OBSERVATION_REVIEWED",Map.of("status",target.name(),"reviewedBy",reviewer,"note",note));
        return PriceObservationResponse.from(entity);
    }

    @Transactional
    public List<PriceAggregateResponse> rebuildAggregates(UUID itemId) {
        MaterialItemEntity item=requireLockedItem(itemId);
        Instant now=Instant.now();
        Map<GroupKey,List<MaterialPriceObservationEntity>> groups=new LinkedHashMap<>();
        if(item.isActive()) for(var o:screenForRebuild(item,now)) groups.computeIfAbsent(group(o,item),k->new ArrayList<>()).add(o);
        List<MaterialPriceAggregateEntity> desired=new ArrayList<>();
        for(var entry:groups.entrySet()) {
            GroupKey key=entry.getKey(); var rows=entry.getValue();
            List<String> currencies="IRR".equals(key.currencyCode())?List.of("IRR","TOMAN"):List.of(key.currencyCode());
            for(String currency:currencies) {
                if(!observationRepository.activeCurrency(currency)) continue;
                List<BigDecimal> values;
                try { values=rows.stream().map(o->normalized(o,item,currency)).toList(); }
                catch(IllegalArgumentException incompatibleRepresentation) { continue; } // no partial mirror or rounded zero reference
                var stats=PriceStatistics.calculate(values);
                MaterialFoundation.amount(stats.min());MaterialFoundation.amount(stats.max());MaterialFoundation.amount(stats.average());MaterialFoundation.amount(stats.median());
                Instant start=rows.stream().map(MaterialPriceObservationEntity::getObservedAt).min(Comparator.naturalOrder()).orElseThrow();
                Instant end=rows.stream().map(MaterialPriceObservationEntity::getObservedAt).max(Comparator.naturalOrder()).orElseThrow();
                List<String> ids=rows.stream().map(o->o.getId().toString()).sorted().toList();
                var aggregate=new MaterialPriceAggregateEntity(UUID.randomUUID(),itemId,key.variantId(),key.priceType(),currency,key.unitCode(),blankToNull(key.province()),blankToNull(key.city()),rows.size(),stats.min(),stats.max(),stats.average(),stats.median(),start,end,MaterialPricePolicy.VERSION,now);
                aggregate.publish(ids,start.plus(MaterialPricePolicy.MAX_AGE),MaterialPricePolicy.digest(MaterialPricePolicy.VERSION,currency,Objects.toString(key.variantId(),null),key.priceType().name(),key.unitCode(),key.province(),key.city(),String.join(",",ids)));
                desired.add(aggregate);
            }
        }
        List<MaterialPriceAggregateEntity> current=aggregateRepository.findByMaterialItemIdOrderByCalculatedAtDesc(itemId);
        if(current.size()==desired.size() && current.stream().map(MaterialPriceAggregateEntity::getPublicationKey).collect(java.util.stream.Collectors.toSet())
                .equals(desired.stream().map(MaterialPriceAggregateEntity::getPublicationKey).collect(java.util.stream.Collectors.toSet()))) {
            if(desired.isEmpty()) aggregateRepository.retirePublished(itemId); // retire hidden/expired rows too
            return current.stream().map(PriceAggregateResponse::from).toList();
        }
        aggregateRepository.retirePublished(itemId);
        List<MaterialPriceAggregateEntity> saved=aggregateRepository.saveAll(desired);
        auditService.record("MATERIAL_ITEM",itemId,"PRICE_AGGREGATES_PUBLISHED",Map.of("aggregateCount",saved.size(),"algorithmVersion",MaterialPricePolicy.VERSION));
        return saved.stream().map(PriceAggregateResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<PriceAggregateResponse> aggregates(UUID itemId) {
        requireItem(itemId);
        return aggregateRepository.findByMaterialItemIdOrderByCalculatedAtDesc(itemId).stream().map(PriceAggregateResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<PriceAnomalyResponse> anomalies(PriceAnomalyStatus status) {
        var rows=status==null?anomalyRepository.findAll():anomalyRepository.findByStatusOrderByCreatedAtAsc(status);
        return rows.stream().map(PriceAnomalyResponse::from).toList();
    }

    @Transactional
    public PriceAnomalyResponse reviewAnomaly(UUID anomalyId,ReviewAnomalyRequest request,Authentication authentication) {
        UUID itemId=anomalyRepository.findMaterialId(anomalyId).orElseThrow(()->new IllegalArgumentException("Price anomaly not found."));
        requireLockedItem(itemId);
        var entity=anomalyRepository.findById(anomalyId).orElseThrow(()->new IllegalArgumentException("Price anomaly not found."));
        String reviewer=reviewActor(authentication);
        if(request.status()!=PriceAnomalyStatus.ACCEPTED && request.status()!=PriceAnomalyStatus.DISMISSED) throw new IllegalArgumentException("Invalid anomaly resolution.");
        if(entity.getStatus()==request.status()) return PriceAnomalyResponse.from(entity);
        if(entity.getStatus()!=PriceAnomalyStatus.OPEN) throw new IllegalArgumentException("Resolved anomaly cannot be rewritten.");
        String note=MaterialFoundation.required(request.note(),4000);
        entity.review(request.status(),reviewer,note,Instant.now());
        aggregateRepository.retirePublished(itemId);
        auditService.record("MATERIAL_PRICE_ANOMALY",entity.getId(),"PRICE_ANOMALY_REVIEWED",Map.of("status",entity.getStatus().name(),"note",note,"reviewedBy",reviewer));
        return PriceAnomalyResponse.from(entity);
    }

    private void requireActiveSource(UUID sourceId,UUID itemId) {
        if(!observationRepository.sourceMatchesMaterial(sourceId,itemId))
            throw new IllegalArgumentException("Unknown or disabled price source.");
    }
    private boolean activeVariant(MaterialPriceObservationEntity o) {
        return o.getVariantId()==null || variantRepository.findById(o.getVariantId()).map(v->v.isActive() && v.getMaterialItemId().equals(o.getMaterialItemId())).orElse(false);
    }
    private List<MaterialPriceObservationEntity> eligible(MaterialItemEntity item,Instant asOf,boolean strictPast) {
        var rows=observationRepository.findWithin(item.getId(),KnowledgeReviewStatus.APPROVED,asOf.minus(MaterialPricePolicy.MAX_AGE),asOf);
        Set<String> seen=new HashSet<>(); List<MaterialPriceObservationEntity> result=new ArrayList<>();
        for(var o:rows.stream().sorted(Comparator.comparing(MaterialPriceObservationEntity::getObservedAt).thenComparing(MaterialPriceObservationEntity::getId)).toList()) {
            if(!MaterialPricePolicy.fresh(o.getObservedAt(),asOf) || (strictPast && !o.getObservedAt().isBefore(asOf)) || !MaterialPricePolicy.confident(o.getConfidence()) || !activeVariant(o)) continue;
            if(!observationRepository.sourceMatchesMaterial(o.getSourceId(),item.getId()) || !observationRepository.activeCurrency(o.getCurrencyCode())) continue;
            if(anomalyRepository.findByObservationId(o.getId()).map(a->a.getStatus()!=PriceAnomalyStatus.DISMISSED).orElse(false)) continue;
            try {
                normalized(o,item,MaterialPricePolicy.currencyGroup(o.getCurrencyCode()));group(o,item);
                MaterialPricePolicy.sourceUrl(o.getSourceUrl(),o.getOrigin()==PriceObservationOrigin.CRAWLER || o.getOrigin()==PriceObservationOrigin.API);
                if(!MaterialPricePolicy.region(o.getCity()).isEmpty() && MaterialPricePolicy.region(o.getProvince()).isEmpty()) continue;
                if(o.getOrigin()==PriceObservationOrigin.IMPORT && (o.getSourceUrl()==null || o.getSourceUrl().isBlank()) && o.getRawPayload().isEmpty()) continue;
                if(seen.add(quoteKey(o,item))) result.add(o);
            } catch(IllegalArgumentException invalidLegacy) { continue; } // historical evidence is kept but cannot become reference
        }
        return result;
    }
    /** Deterministic chronological screening also covers legacy/backdated approved quotes. */
    private List<MaterialPriceObservationEntity> screenForRebuild(MaterialItemEntity item,Instant now) {
        Map<GroupKey,List<MaterialPriceObservationEntity>> accepted=new HashMap<>();
        List<MaterialPriceObservationEntity> result=new ArrayList<>();
        for(var row:eligible(item,now,false)) {
            GroupKey key=group(row,item);
            var earlier=accepted.computeIfAbsent(key,k->new ArrayList<>());
            if(anomalyRepository.findByObservationId(row.getId()).isEmpty()) {
                List<BigDecimal> prior=earlier.stream().filter(o->o.getObservedAt().isBefore(row.getObservedAt()) && MaterialPricePolicy.fresh(o.getObservedAt(),row.getObservedAt()))
                        .map(o->normalized(o,item,key.currencyCode())).toList();
                var anomaly=anomalyDetector.detect(normalized(row,item,key.currencyCode()),prior);
                if(anomaly.isPresent()) {
                    var a=anomaly.get();
                    anomalyRepository.save(new MaterialPriceAnomalyEntity(UUID.randomUUID(),row.getId(),a.type(),a.severity(),a.score().min(new BigDecimal("999999.9999")),a.reason(),now));
                    auditService.record("MATERIAL_PRICE_OBSERVATION",row.getId(),"PRICE_ANOMALY_DETECTED_ON_REBUILD",Map.of("algorithmVersion",MaterialPricePolicy.VERSION));
                    continue;
                }
            }
            earlier.add(row);result.add(row);
        }
        return result;
    }
    private static String safeQuoteKey(MaterialPriceObservationEntity o,MaterialItemEntity item) {
        try { return quoteKey(o,item); } catch(IllegalArgumentException malformedLegacy) { return null; }
    }
    private static BigDecimal normalized(MaterialPriceObservationEntity o,MaterialItemEntity item,String targetCurrency) {
        return MaterialPricePolicy.unitPrice(o.getAmount(),o.getQuantityBasis(),o.getUnitCode(),item.getUnitCode(),o.getCurrencyCode(),targetCurrency);
    }
    private static GroupKey group(MaterialPriceObservationEntity o,MaterialItemEntity item) {
        return new GroupKey(o.getVariantId(),o.getPriceType(),MaterialPricePolicy.currencyGroup(o.getCurrencyCode()),item.getUnitCode(),MaterialPricePolicy.region(o.getProvince()),MaterialPricePolicy.region(o.getCity()));
    }
    private static String quoteKey(MaterialPriceObservationEntity o,MaterialItemEntity item) {
        String currency=MaterialPricePolicy.currencyGroup(o.getCurrencyCode());
        BigDecimal amount=MaterialPricePolicy.currencyAmount(MaterialFoundation.amount(o.getAmount()),o.getCurrencyCode(),currency);
        BigDecimal quantity=MaterialFoundation.convert(MaterialFoundation.quantity(o.getQuantityBasis()),o.getUnitCode(),item.getUnitCode());
        return MaterialPricePolicy.digest(o.getMaterialItemId().toString(),Objects.toString(o.getVariantId(),null),o.getPriceType().name(),
                currency,item.getUnitCode(),amount.stripTrailingZeros().toPlainString(),quantity.stripTrailingZeros().toPlainString(),
                MaterialPricePolicy.region(o.getProvince()),MaterialPricePolicy.region(o.getCity()),o.getSourceId().toString(),o.getObservedAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS).toString());
    }
    private static String reviewActor(Authentication authentication) {
        if(authentication==null || !authentication.isAuthenticated() || authentication.getName()==null || authentication.getName().isBlank() || authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken
                || authentication.getAuthorities().stream().noneMatch(a->"USER_MANAGE".equals(a.getAuthority())))
            throw new IllegalArgumentException("Authenticated material administrator required.");
        return authentication.getName();
    }

    @Transactional(readOnly = true)
    public List<PriceAggregateHistoryResponse> aggregateHistory(UUID itemId) {
        requireItem(itemId);
        Set<UUID> usable=aggregateRepository.findByMaterialItemIdOrderByCalculatedAtDesc(itemId).stream()
                .map(MaterialPriceAggregateEntity::getId).collect(java.util.stream.Collectors.toSet());
        return aggregateRepository.findHistory(itemId).stream().map(a -> new PriceAggregateHistoryResponse(
                PriceAggregateResponse.from(a),usable.contains(a.getId()),a.getExpiresAt(),a.getEvidenceIds())).toList();
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