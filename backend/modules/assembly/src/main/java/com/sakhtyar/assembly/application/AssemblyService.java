package com.sakhtyar.assembly.application;

import static com.sakhtyar.assembly.api.AssemblyDtos.*;

import com.sakhtyar.assembly.domain.*;
import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.material.domain.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssemblyService {

    private static final String ALGORITHM_VERSION = "1.0";

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
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return assemblyRepository.findAllByOrderByUpdatedAtDesc().stream()
                .filter(a -> category == null || category.isBlank() || a.getCategory().equalsIgnoreCase(category))
                .filter(a -> q.isBlank()
                        || a.getCode().toLowerCase(Locale.ROOT).contains(q)
                        || a.getNameFa().toLowerCase(Locale.ROOT).contains(q)
                        || (a.getNameEn() != null && a.getNameEn().toLowerCase(Locale.ROOT).contains(q)))
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
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        assemblyRepository.findByCodeIgnoreCase(code).ifPresent(existing -> {
            throw new IllegalArgumentException("Assembly code already exists: " + code);
        });

        Instant now = Instant.now();
        CostAssemblyEntity entity = new CostAssemblyEntity(
                UUID.randomUUID(), code, request.nameFa().trim(), trim(request.nameEn()),
                request.category().trim(), request.outputUnitCode().trim().toUpperCase(Locale.ROOT),
                trim(request.description()), request.active(), request.metadata(),
                actor(authentication), now
        );
        assemblyRepository.save(entity);

        auditService.record("COST_ASSEMBLY", entity.getId(), "ASSEMBLY_CREATED",
                Map.of("code", entity.getCode(), "category", entity.getCategory()));

        return AssemblyResponse.from(entity);
    }

    @Transactional
    public AssemblyResponse updateAssembly(UUID id, UpsertAssemblyRequest request) {
        CostAssemblyEntity entity = requireAssembly(id);
        if (!entity.getCode().equalsIgnoreCase(request.code().trim())) {
            throw new IllegalArgumentException("Assembly code is immutable after creation.");
        }

        entity.update(
                request.nameFa().trim(), trim(request.nameEn()), request.category().trim(),
                request.outputUnitCode().trim().toUpperCase(Locale.ROOT),
                trim(request.description()), request.active(), request.metadata(), Instant.now()
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
        requireAssembly(assemblyId);
        MaterialItemEntity material = materialRepository.findById(request.materialItemId())
                .orElseThrow(() -> new IllegalArgumentException("Material not found."));

        if (!material.getUnitCode().equalsIgnoreCase(request.unitCode().trim())) {
            throw new IllegalArgumentException(
                    "Assembly component unit must match the material base unit in Phase 5.5."
            );
        }

        if (request.variantId() != null) {
            MaterialVariantEntity variant = variantRepository.findById(request.variantId())
                    .orElseThrow(() -> new IllegalArgumentException("Material variant not found."));
            if (!variant.getMaterialItemId().equals(material.getId())) {
                throw new IllegalArgumentException("Variant does not belong to selected material.");
            }
        }

        CostAssemblyComponentEntity entity = new CostAssemblyComponentEntity(
                UUID.randomUUID(), assemblyId, material.getId(), request.variantId(),
                request.quantity(), request.wasteFactor(),
                request.unitCode().trim().toUpperCase(Locale.ROOT),
                request.priceBasis(), request.sortOrder(), trim(request.note()), Instant.now()
        );
        componentRepository.save(entity);

        auditService.record("COST_ASSEMBLY", assemblyId, "ASSEMBLY_COMPONENT_ADDED",
                Map.of(
                        "componentId", entity.getId().toString(),
                        "materialId", material.getId().toString()
                ));

        return ComponentResponse.from(entity);
    }

    @Transactional
    public void removeComponent(UUID assemblyId, UUID componentId) {
        requireAssembly(assemblyId);
        CostAssemblyComponentEntity component = componentRepository.findById(componentId)
                .orElseThrow(() -> new IllegalArgumentException("Assembly component not found."));
        if (!component.getAssemblyId().equals(assemblyId)) {
            throw new IllegalArgumentException("Component does not belong to selected assembly.");
        }
        componentRepository.delete(component);

        auditService.record("COST_ASSEMBLY", assemblyId, "ASSEMBLY_COMPONENT_REMOVED",
                Map.of("componentId", componentId.toString()));
    }

    @Transactional
    public EstimateResponse calculate(
            UUID assemblyId, CalculateCostRequest request, Authentication authentication
    ) {
        CostAssemblyEntity assembly = requireAssembly(assemblyId);
        if (!assembly.isActive()) {
            throw new IllegalStateException("Assembly is inactive.");
        }

        List<CostAssemblyComponentEntity> components =
                componentRepository.findByAssemblyIdOrderBySortOrderAscIdAsc(assemblyId);

        if (components.isEmpty()) {
            throw new IllegalStateException("Assembly has no components.");
        }

        String currency = request.currencyCode().trim().toUpperCase(Locale.ROOT);
        String province = normalizeRegion(request.province());
        String city = normalizeRegion(request.city());

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
            BigDecimal lineTotal = AssemblyCostCalculator.lineTotal(effectiveQuantity, unitPrice);
            total = total.add(lineTotal);

            prepared.add(new PreparedLine(component, aggregate, unitPrice, effectiveQuantity, lineTotal));
        }

        BigDecimal finalTotal = total.setScale(2, RoundingMode.HALF_UP);
        BigDecimal unitCost = finalTotal.divide(request.quantity(), 2, RoundingMode.HALF_UP);

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
        return aggregateRepository
                .findByMaterialItemIdOrderByCalculatedAtDesc(component.getMaterialItemId())
                .stream()
                .filter(a -> Objects.equals(a.getVariantId(), component.getVariantId()))
                .filter(a -> a.getPriceType() == priceType)
                .filter(a -> a.getCurrencyCode().equalsIgnoreCase(currency))
                .filter(a -> a.getUnitCode().equalsIgnoreCase(component.getUnitCode()))
                .filter(a -> Objects.equals(normalizeRegion(a.getProvince()), province))
                .filter(a -> Objects.equals(normalizeRegion(a.getCity()), city))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No approved price aggregate is available for material "
                                + component.getMaterialItemId()
                                + " with the requested variant/price type/currency/unit/region."
                ));
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
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
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