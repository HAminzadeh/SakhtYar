package com.sakhtyar.assembly.application;

import static com.sakhtyar.assembly.api.AssemblyDtos.*;

import com.sakhtyar.assembly.domain.*;
import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.material.domain.*;
import com.sakhtyar.material.application.MaterialFoundation;
import com.sakhtyar.material.application.MaterialPricePolicy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssemblyService {

    private static final String ALGORITHM_VERSION = "2.0";

    private final CostAssemblyRepository assemblyRepository;
    private final CostAssemblyComponentRepository componentRepository;
    private final CostEstimateRepository estimateRepository;
    private final CostEstimateLineRepository lineRepository;
    private final MaterialItemRepository materialRepository;
    private final MaterialVariantRepository variantRepository;
    private final MaterialPriceAggregateRepository aggregateRepository;
    private final AuditService auditService;

    public AssemblyService(
            CostAssemblyRepository assemblyRepository,
            CostAssemblyComponentRepository componentRepository,
            CostEstimateRepository estimateRepository,
            CostEstimateLineRepository lineRepository,
            MaterialItemRepository materialRepository,
            MaterialVariantRepository variantRepository,
            MaterialPriceAggregateRepository aggregateRepository,
            AuditService auditService
    ) {
        this.assemblyRepository = assemblyRepository;
        this.componentRepository = componentRepository;
        this.estimateRepository = estimateRepository;
        this.lineRepository = lineRepository;
        this.materialRepository = materialRepository;
        this.variantRepository = variantRepository;
        this.aggregateRepository = aggregateRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<AssemblyResponse> listAssemblies(String query, String category) {
        String q = MaterialFoundation.search(query);
        return assemblyRepository.findAllByOrderByUpdatedAtDesc().stream()
                .filter(a -> category == null || category.isBlank() || a.getCategory().equalsIgnoreCase(category))
                .filter(a -> q.isBlank()
                        || MaterialFoundation.search(a.getCode()).contains(q)
                        || MaterialFoundation.search(a.getNameFa()).contains(q)
                        || MaterialFoundation.search(a.getNameEn()).contains(q))
                .map(AssemblyResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AssemblyResponse getAssembly(UUID id) {
        return AssemblyResponse.from(requireAssembly(id));
    }

    @Transactional
    public AssemblyResponse createAssembly(
            UpsertAssemblyRequest request, Authentication authentication
    ) {
        String code = MaterialFoundation.code(request.code());
        assemblyRepository.findByCodeIgnoreCase(code).ifPresent(existing -> {
            throw new IllegalArgumentException("Assembly code already exists: " + code);
        });

        Instant now = Instant.now();
        CostAssemblyEntity entity = new CostAssemblyEntity(
                UUID.randomUUID(), code, MaterialFoundation.required(request.nameFa(), 400), MaterialFoundation.optional(request.nameEn(), 400),
                MaterialFoundation.required(request.category(), 160), MaterialFoundation.unitCode(request.outputUnitCode()),
                AssemblyDefinitionPolicy.optionalText(request.description(), 10000), request.active(), AssemblyDefinitionPolicy.metadata(request.metadata()),
                actor(authentication), now
        );
        assemblyRepository.save(entity);

        auditService.record("COST_ASSEMBLY", entity.getId(), "ASSEMBLY_CREATED",
                Map.of("code", entity.getCode(), "category", entity.getCategory()));

        return AssemblyResponse.from(entity);
    }

    @Transactional
    public AssemblyResponse updateAssembly(UUID id, UpsertAssemblyRequest request) {
        CostAssemblyEntity entity = requireLockedAssembly(id);
        if (!entity.getCode().equalsIgnoreCase(request.code().trim())) {
            throw new IllegalArgumentException("Assembly code is immutable after creation.");
        }

        entity.update(
                MaterialFoundation.required(request.nameFa(), 400), MaterialFoundation.optional(request.nameEn(), 400), MaterialFoundation.required(request.category(), 160),
                AssemblyDefinitionPolicy.unchangedOutputUnit(entity.getOutputUnitCode(), request.outputUnitCode()),
                AssemblyDefinitionPolicy.optionalText(request.description(), 10000), request.active(), AssemblyDefinitionPolicy.metadata(request.metadata()), Instant.now()
        );

        auditService.record("COST_ASSEMBLY", entity.getId(), "ASSEMBLY_UPDATED",
                Map.of("code", entity.getCode(), "active", entity.isActive()));

        return AssemblyResponse.from(entity);
    }

    @Transactional(readOnly = true)
    public List<ComponentResponse> components(UUID assemblyId) {
        requireAssembly(assemblyId);
        return componentRepository.findByAssemblyIdOrderBySortOrderAscIdAsc(assemblyId).stream()
                .map(ComponentResponse::from)
                .toList();
    }

    @Transactional
    public ComponentResponse addComponent(UUID assemblyId, CreateComponentRequest request) {
        if(!requireLockedAssembly(assemblyId).isActive()) throw new IllegalStateException("Assembly inactive.");
        MaterialItemEntity material = validateComponent(request);

        CostAssemblyComponentEntity entity = new CostAssemblyComponentEntity(
                UUID.randomUUID(), assemblyId, material.getId(), request.variantId(),
                MaterialFoundation.quantity(request.quantity()), AssemblyDefinitionPolicy.waste(request.wasteFactor()),
                MaterialFoundation.unitCode(request.unitCode()),
                request.priceBasis(), request.sortOrder(), AssemblyDefinitionPolicy.optionalText(request.note(), 4000), Instant.now()
        );
        componentRepository.save(entity);

        auditService.record("COST_ASSEMBLY", assemblyId, "ASSEMBLY_COMPONENT_ADDED",
                Map.of(
                        "componentId", entity.getId().toString(),
                        "materialId", material.getId().toString()
                ));

        return ComponentResponse.from(entity);
    }

    private MaterialItemEntity validateComponent(CreateComponentRequest request) {
        if (request == null || request.materialItemId() == null || request.priceBasis() == null || request.sortOrder() < 0)
            throw new IllegalArgumentException("Invalid assembly component.");
        MaterialItemEntity material = materialRepository.findLockedById(request.materialItemId())
                .orElseThrow(() -> new IllegalArgumentException("Material not found."));
        if (!material.isActive()) throw new IllegalArgumentException("Inactive material.");
        MaterialFoundation.quantity(request.quantity());
        AssemblyDefinitionPolicy.waste(request.wasteFactor());
        if (!material.getUnitCode().equalsIgnoreCase(MaterialFoundation.unitCode(request.unitCode())))
            throw new IllegalArgumentException("Assembly component unit must match material base unit.");
        AssemblyDefinitionPolicy.optionalText(request.note(), 4000);
        if (request.variantId() != null) {
            MaterialVariantEntity variant = variantRepository.findById(request.variantId())
                    .orElseThrow(() -> new IllegalArgumentException("Material variant not found."));
            if (!variant.isActive() || !variant.getMaterialItemId().equals(material.getId()))
                throw new IllegalArgumentException("Variant does not belong to selected material.");
        }
        return material;
    }

    @Transactional
    public ComponentResponse updateComponent(UUID assemblyId, UUID componentId, CreateComponentRequest request) {
        if (!requireLockedAssembly(assemblyId).isActive()) throw new IllegalStateException("Assembly inactive.");
        CostAssemblyComponentEntity component = requireComponent(assemblyId, componentId);
        MaterialItemEntity material = validateComponent(request);
        component.revise(material.getId(), request.variantId(), MaterialFoundation.quantity(request.quantity()),
                AssemblyDefinitionPolicy.waste(request.wasteFactor()), MaterialFoundation.unitCode(request.unitCode()),
                request.priceBasis(), request.sortOrder(), AssemblyDefinitionPolicy.optionalText(request.note(), 4000));
        auditService.record("COST_ASSEMBLY", assemblyId, "ASSEMBLY_COMPONENT_UPDATED",
                Map.of("componentId", componentId.toString(), "materialId", material.getId().toString()));
        return ComponentResponse.from(component);
    }

    private CostAssemblyComponentEntity requireComponent(UUID assemblyId, UUID componentId) {
        CostAssemblyComponentEntity component = componentRepository.findById(componentId)
                .orElseThrow(() -> new IllegalArgumentException("Assembly component not found."));
        if (!component.getAssemblyId().equals(assemblyId))
            throw new IllegalArgumentException("Component does not belong to selected assembly.");
        return component;
    }

    @Transactional
    public void removeComponent(UUID assemblyId, UUID componentId) {
        requireLockedAssembly(assemblyId);
        CostAssemblyComponentEntity component = requireComponent(assemblyId, componentId);
        if (lineRepository.existsByAssemblyComponentId(componentId))
            throw new IllegalStateException("Component has historical estimates; revise the definition or create a new assembly.");
        componentRepository.delete(component);

        auditService.record("COST_ASSEMBLY", assemblyId, "ASSEMBLY_COMPONENT_REMOVED",
                Map.of("componentId", componentId.toString()));
    }

    @Transactional
    public EstimateResponse calculate(
            UUID assemblyId, CalculateCostRequest request, Authentication authentication
    ) {
        CostAssemblyEntity assembly = requireLockedAssembly(assemblyId);
        MaterialFoundation.quantity(request.quantity());
        if (!assembly.isActive()) {
            throw new IllegalStateException("Assembly is inactive.");
        }

        List<CostAssemblyComponentEntity> components =
                componentRepository.findByAssemblyIdOrderBySortOrderAscIdAsc(assemblyId);

        if (components.isEmpty()) {
            throw new IllegalStateException("Assembly has no components.");
        }

        if (request.priceType() == null) throw new IllegalArgumentException("Price type required.");
        String currency = MaterialFoundation.currencyCode(request.currencyCode());
        String province = normalizeRegion(request.province());
        String city = normalizeRegion(request.city());
        if (!city.isBlank() && province.isBlank()) throw new IllegalArgumentException("City requires province.");
        // Lock in deterministic UUID order within this assembly; a deadlock aborts the whole transaction.
        components.stream().map(CostAssemblyComponentEntity::getMaterialItemId).distinct().sorted().forEach(id -> {
            var item=materialRepository.findLockedById(id).orElseThrow(() -> new IllegalStateException("Missing material."));
            if(!item.isActive()) throw new IllegalStateException("Inactive material.");
        });

        List<PreparedLine> prepared = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (CostAssemblyComponentEntity component : components) {
            MaterialPriceAggregateEntity aggregate = selectAggregate(
                    component, request.priceType(), currency, province, city
            );

            BigDecimal unitPrice = AssemblyCostCalculator.choosePrice(
                    component.getPriceBasis(),
                    aggregate.getMinAmount(),
                    aggregate.getMaxAmount(),
                    aggregate.getAverageAmount(),
                    aggregate.getMedianAmount()
            );

            BigDecimal effectiveQuantity = AssemblyCostCalculator.effectiveQuantity(
                    component.getQuantity(), component.getWasteFactor(), request.quantity()
            );
            MaterialFoundation.quantity(effectiveQuantity);
            BigDecimal lineTotal = MaterialFoundation.amount(AssemblyCostCalculator.lineTotal(effectiveQuantity, unitPrice));
            total = total.add(lineTotal);

            prepared.add(new PreparedLine(component, aggregate, unitPrice, effectiveQuantity, lineTotal));
        }

        BigDecimal finalTotal = MaterialFoundation.amount(total.setScale(2, RoundingMode.HALF_UP));
        BigDecimal unitCost = MaterialFoundation.amount(finalTotal.divide(request.quantity(), 2, RoundingMode.HALF_UP));

        Instant now = Instant.now();
        CostEstimateEntity estimate = new CostEstimateEntity(
                UUID.randomUUID(), assemblyId, request.quantity(), assembly.getOutputUnitCode(),
                request.priceType(), currency, blankToNull(province), blankToNull(city),
                unitCost, finalTotal, ALGORITHM_VERSION, actor(authentication), now
        );
        estimateRepository.save(estimate);

        List<CostEstimateLineEntity> lines = new ArrayList<>();
        for (PreparedLine p : prepared) {
            CostEstimateLineEntity line = new CostEstimateLineEntity(
                    UUID.randomUUID(), estimate.getId(), p.component().getId(),
                    p.component().getMaterialItemId(), p.component().getVariantId(),
                    p.aggregate().getId(), p.component().getQuantity(),
                    p.component().getWasteFactor(), p.effectiveQuantity(),
                    p.component().getUnitCode(), p.unitPrice(), p.lineTotal(),
                    p.component().getPriceBasis(), p.aggregate().getSampleCount(),
                    p.aggregate().getPeriodStart(), p.aggregate().getPeriodEnd()
            );
            lines.add(lineRepository.save(line));
        }

        auditService.record("COST_ESTIMATE", estimate.getId(), "ASSEMBLY_COST_CALCULATED",
                Map.of(
                        "assemblyId", assemblyId.toString(),
                        "quantity", request.quantity().toPlainString(),
                        "currency", currency,
                        "totalCost", finalTotal.toPlainString(),
                        "algorithmVersion", ALGORITHM_VERSION
                ));

        return toEstimateResponse(estimate, lines);
    }

    @Transactional(readOnly = true)
    public List<EstimateResponse> estimates(UUID assemblyId) {
        requireAssembly(assemblyId);
        return estimateRepository.findByAssemblyIdOrderByCalculatedAtDesc(assemblyId).stream()
                .map(e -> toEstimateResponse(
                        e, lineRepository.findByEstimateIdOrderByIdAsc(e.getId())
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public EstimateResponse estimate(UUID estimateId) {
        CostEstimateEntity estimate = estimateRepository.findById(estimateId)
                .orElseThrow(() -> new IllegalArgumentException("Cost estimate not found."));
        return toEstimateResponse(
                estimate, lineRepository.findByEstimateIdOrderByIdAsc(estimateId)
        );
    }

    private MaterialPriceAggregateEntity selectAggregate(
            CostAssemblyComponentEntity component,
            MaterialPriceType priceType,
            String currency,
            String province,
            String city
    ) {
        return MaterialPriceResolver.resolve(aggregateRepository.findByMaterialItemIdOrderByCalculatedAtDesc(component.getMaterialItemId()),
                component.getMaterialItemId(), component.getVariantId(), priceType, currency, component.getUnitCode(), province, city, Instant.now());
    }

    private EstimateResponse toEstimateResponse(
            CostEstimateEntity estimate,
            List<CostEstimateLineEntity> lines
    ) {
        return new EstimateResponse(
                estimate.getId(), estimate.getAssemblyId(), estimate.getRequestedQuantity(),
                estimate.getOutputUnitCode(), estimate.getPriceType(),
                estimate.getCurrencyCode(), estimate.getProvince(), estimate.getCity(),
                estimate.getUnitCost(), estimate.getTotalCost(), estimate.getAlgorithmVersion(),
                estimate.getStatus(), estimate.getCalculatedBy(), estimate.getCalculatedAt(),
                lines.stream().map(EstimateLineResponse::from).toList()
        );
    }

    private CostAssemblyEntity requireLockedAssembly(UUID id) {
        return assemblyRepository.findLockedById(id).orElseThrow(() -> new IllegalArgumentException("Assembly not found."));
    }

    private CostAssemblyEntity requireAssembly(UUID id) {
        return assemblyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Assembly not found."));
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
        return com.sakhtyar.material.application.MaterialPricePolicy.region(value);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record PreparedLine(
            CostAssemblyComponentEntity component,
            MaterialPriceAggregateEntity aggregate,
            BigDecimal unitPrice,
            BigDecimal effectiveQuantity,
            BigDecimal lineTotal
    ) {}
}