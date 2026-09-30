package com.sakhtyar.scenario.application;

import static com.sakhtyar.scenario.api.ScenarioDtos.*;

import com.sakhtyar.assembly.api.AssemblyDtos;
import com.sakhtyar.assembly.application.AssemblyService;
import com.sakhtyar.assembly.domain.CostAssemblyRepository;
import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.scenario.domain.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScenarioService {
    private static final String COST_ALGORITHM_VERSION = "1.0";
    private final QualityPackageRepository packageRepo;
    private final QualityPackageSelectionRepository selectionRepo;
    private final ConstructionScenarioRepository scenarioRepo;
    private final ConstructionScenarioItemRepository itemRepo;
    private final ScenarioCostSnapshotRepository snapshotRepo;
    private final ScenarioCostSnapshotLineRepository snapshotLineRepo;
    private final CostAssemblyRepository assemblyRepo;
    private final AssemblyService assemblyService;
    private final AuditService auditService;

    public ScenarioService(QualityPackageRepository p, QualityPackageSelectionRepository s, ConstructionScenarioRepository c, ConstructionScenarioItemRepository i, ScenarioCostSnapshotRepository sr, ScenarioCostSnapshotLineRepository sl, CostAssemblyRepository ar, AssemblyService as, AuditService au) {
        packageRepo = p;
        selectionRepo = s;
        scenarioRepo = c;
        itemRepo = i;
        snapshotRepo = sr;
        snapshotLineRepo = sl;
        assemblyRepo = ar;
        assemblyService = as;
        auditService = au;
    }

    @Transactional(readOnly = true)
    public List<PackageResponse> packages() {
        return packageRepo.findAllByOrderByUpdatedAtDesc().stream().map(PackageResponse::from).toList();
    }

    @Transactional
    public PackageResponse createPackage(UpsertPackageRequest r, Authentication a) {
        String code = r.code().trim().toUpperCase(Locale.ROOT);
        packageRepo.findByCodeIgnoreCase(code).ifPresent(x -> {
            throw new IllegalArgumentException("Quality package code already exists.");
        });
        Instant now = Instant.now();
        var e = new QualityPackageEntity(UUID.randomUUID(), code, r.nameFa().trim(), trim(r.nameEn()), r.qualityLevel(), trim(r.description()), r.active(), r.metadata(), actor(a), now);
        packageRepo.save(e);
        auditService.record("QUALITY_PACKAGE", e.getId(), "QUALITY_PACKAGE_CREATED", Map.of("code", code));
        return PackageResponse.from(e);
    }

    @Transactional
    public PackageResponse updatePackage(UUID id, UpsertPackageRequest r) {
        var e = reqPkg(id);
        if (!e.getCode().equalsIgnoreCase(r.code().trim()))
            throw new IllegalArgumentException("Quality package code is immutable.");
        e.update(r.nameFa().trim(), trim(r.nameEn()), r.qualityLevel(), trim(r.description()), r.active(), r.metadata(), Instant.now());
        auditService.record("QUALITY_PACKAGE", id, "QUALITY_PACKAGE_UPDATED", Map.of("code", e.getCode(), "active", e.isActive()));
        return PackageResponse.from(e);
    }

    @Transactional(readOnly = true)
    public List<SelectionResponse> selections(UUID id) {
        reqPkg(id);
        return selectionRepo.findByPackageIdOrderBySortOrderAscIdAsc(id).stream().map(SelectionResponse::from).toList();
    }

    @Transactional
    public SelectionResponse addSelection(UUID id, CreateSelectionRequest r) {
        reqPkg(id);
        if (!assemblyRepo.existsById(r.assemblyId())) throw new IllegalArgumentException("Assembly not found.");
        selectionRepo.findByPackageIdAndSlotCodeIgnoreCase(id, r.slotCode().trim()).ifPresent(x -> {
            throw new IllegalArgumentException("Slot code already exists in package.");
        });
        var e = new QualityPackageSelectionEntity(UUID.randomUUID(), id, r.slotCode().trim().toUpperCase(Locale.ROOT), r.slotNameFa().trim(), r.assemblyId(), r.required(), r.quantityMultiplier(), r.sortOrder(), trim(r.note()), Instant.now());
        selectionRepo.save(e);
        auditService.record("QUALITY_PACKAGE", id, "QUALITY_PACKAGE_SELECTION_ADDED", Map.of("selectionId", e.getId().toString()));
        return SelectionResponse.from(e);
    }

    @Transactional
    public void removeSelection(UUID id, UUID selectionId) {
        reqPkg(id);
        var e = selectionRepo.findById(selectionId).orElseThrow(() -> new IllegalArgumentException("Selection not found."));
        if (!e.getPackageId().equals(id)) throw new IllegalArgumentException("Selection does not belong to package.");
        selectionRepo.delete(e);
        auditService.record("QUALITY_PACKAGE", id, "QUALITY_PACKAGE_SELECTION_REMOVED", Map.of("selectionId", selectionId.toString()));
    }

    @Transactional(readOnly = true)
    public List<ScenarioResponse> scenarios() {
        return scenarioRepo.findAllByOrderByUpdatedAtDesc().stream().map(ScenarioResponse::from).toList();
    }

    @Transactional
    public ScenarioResponse createScenario(UpsertScenarioRequest r, Authentication a) {
        String code = r.code().trim().toUpperCase(Locale.ROOT);
        scenarioRepo.findByCodeIgnoreCase(code).ifPresent(x -> {
            throw new IllegalArgumentException("Scenario code already exists.");
        });
        validatePkg(r.qualityPackageId());
        Instant now = Instant.now();
        var e = new ConstructionScenarioEntity(UUID.randomUUID(), code, r.nameFa().trim(), trim(r.nameEn()), r.qualityPackageId(), trim(r.structuralSystem()), trim(r.description()), r.assumptions(), r.active(), actor(a), now);
        scenarioRepo.save(e);
        auditService.record("CONSTRUCTION_SCENARIO", e.getId(), "SCENARIO_CREATED", Map.of("code", code));
        return ScenarioResponse.from(e);
    }

    @Transactional
    public ScenarioResponse updateScenario(UUID id, UpsertScenarioRequest r) {
        var e = reqScenario(id);
        if (!e.getCode().equalsIgnoreCase(r.code().trim()))
            throw new IllegalArgumentException("Scenario code is immutable.");
        validatePkg(r.qualityPackageId());
        e.update(r.nameFa().trim(), trim(r.nameEn()), r.qualityPackageId(), trim(r.structuralSystem()), trim(r.description()), r.assumptions(), r.active(), Instant.now());
        auditService.record("CONSTRUCTION_SCENARIO", id, "SCENARIO_UPDATED", Map.of("code", e.getCode(), "active", e.isActive()));
        return ScenarioResponse.from(e);
    }

    @Transactional
    public ScenarioResponse changeStatus(UUID id, ChangeStatusRequest r) {
        var e = reqScenario(id);
        if (r.status() == ScenarioStatus.READY) {
            if (!e.isActive()) throw new IllegalStateException("Inactive scenario cannot be READY.");
            if (itemRepo.findByScenarioIdOrderBySortOrderAscIdAsc(id).stream().noneMatch(ConstructionScenarioItemEntity::isEnabled))
                throw new IllegalStateException("Scenario must contain at least one enabled item before READY.");
        }
        e.setStatus(r.status(), Instant.now());
        auditService.record("CONSTRUCTION_SCENARIO", id, "SCENARIO_STATUS_CHANGED", Map.of("status", r.status().name()));
        return ScenarioResponse.from(e);
    }

    @Transactional(readOnly = true)
    public List<ScenarioItemResponse> items(UUID id) {
        reqScenario(id);
        return itemRepo.findByScenarioIdOrderBySortOrderAscIdAsc(id).stream().map(ScenarioItemResponse::from).toList();
    }

    @Transactional
    public ScenarioItemResponse addItem(UUID id, CreateScenarioItemRequest r) {
        var sc = reqScenario(id);
        if (!assemblyRepo.existsById(r.assemblyId())) throw new IllegalArgumentException("Assembly not found.");
        if (r.slotCode() != null && sc.getQualityPackageId() != null) {
            var sel = selectionRepo.findByPackageIdAndSlotCodeIgnoreCase(sc.getQualityPackageId(), r.slotCode().trim()).orElseThrow(() -> new IllegalArgumentException("Slot is not defined in selected quality package."));
            if (r.source() == ScenarioItemSource.PACKAGE && !sel.getAssemblyId().equals(r.assemblyId()))
                throw new IllegalArgumentException("PACKAGE item assembly must match package selection.");
        }
        var e = new ConstructionScenarioItemEntity(UUID.randomUUID(), id, r.slotCode() == null ? null : r.slotCode().trim().toUpperCase(Locale.ROOT), r.assemblyId(), r.quantity(), r.source(), r.enabled(), r.sortOrder(), trim(r.note()), Instant.now());
        itemRepo.save(e);
        auditService.record("CONSTRUCTION_SCENARIO", id, "SCENARIO_ITEM_ADDED", Map.of("itemId", e.getId().toString()));
        return ScenarioItemResponse.from(e);
    }

    @Transactional
    public void removeItem(UUID id, UUID itemId) {
        reqScenario(id);
        var e = itemRepo.findById(itemId).orElseThrow(() -> new IllegalArgumentException("Scenario item not found."));
        if (!e.getScenarioId().equals(id)) throw new IllegalArgumentException("Item does not belong to scenario.");
        itemRepo.delete(e);
        auditService.record("CONSTRUCTION_SCENARIO", id, "SCENARIO_ITEM_REMOVED", Map.of("itemId", itemId.toString()));
    }

    @Transactional
    public SnapshotResponse calculate(UUID id, CalculateScenarioRequest r, Authentication a) {
        var sc = reqScenario(id);
        if (!sc.isActive()) throw new IllegalStateException("Scenario is inactive.");
        if (sc.getStatus() == ScenarioStatus.ARCHIVED)
            throw new IllegalStateException("Archived scenario cannot be recalculated.");
        var items = itemRepo.findByScenarioIdOrderBySortOrderAscIdAsc(id).stream().filter(ConstructionScenarioItemEntity::isEnabled).toList();
        if (items.isEmpty()) throw new IllegalStateException("Scenario has no enabled items.");
        String currency = r.currencyCode().trim().toUpperCase(Locale.ROOT);
        BigDecimal total = BigDecimal.ZERO;
        List<AssemblyResult> results = new ArrayList<>();
        for (var item : items) {
            var estimate = assemblyService.calculate(item.getAssemblyId(), new AssemblyDtos.CalculateCostRequest(item.getQuantity(), r.priceType(), currency, trim(r.province()), trim(r.city())), a);
            total = total.add(estimate.totalCost());
            results.add(new AssemblyResult(item, estimate));
        }
        Instant now = Instant.now();
        var snap = new ScenarioCostSnapshotEntity(UUID.randomUUID(), id, r.priceType(), currency, trim(r.province()), trim(r.city()), total, COST_ALGORITHM_VERSION, actor(a), now);
        snapshotRepo.save(snap);
        List<ScenarioCostSnapshotLineEntity> lines = new ArrayList<>();
        for (var result : results) {
            var line = new ScenarioCostSnapshotLineEntity(UUID.randomUUID(), snap.getId(), result.item().getId(), result.item().getAssemblyId(), result.estimate().id(), result.item().getQuantity(), result.estimate().unitCost(), result.estimate().totalCost());
            lines.add(snapshotLineRepo.save(line));
        }
        auditService.record("SCENARIO_COST_SNAPSHOT", snap.getId(), "SCENARIO_COST_CALCULATED", Map.of("scenarioId", id.toString(), "totalCost", total.toPlainString(), "currency", currency));
        return toSnapshot(snap, lines);
    }

    @Transactional(readOnly = true)
    public List<SnapshotResponse> snapshots(UUID id) {
        reqScenario(id);
        return snapshotRepo.findByScenarioIdOrderByCalculatedAtDesc(id).stream().map(s -> toSnapshot(s, snapshotLineRepo.findBySnapshotIdOrderByIdAsc(s.getId()))).toList();
    }

    private SnapshotResponse toSnapshot(ScenarioCostSnapshotEntity s, List<ScenarioCostSnapshotLineEntity> lines) {
        return new SnapshotResponse(s.getId(), s.getScenarioId(), s.getPriceType(), s.getCurrencyCode(), s.getProvince(), s.getCity(), s.getTotalCost(), s.getAlgorithmVersion(), s.getCalculatedBy(), s.getCalculatedAt(), lines.stream().map(SnapshotLineResponse::from).toList());
    }

    private QualityPackageEntity reqPkg(UUID id) {
        return packageRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("Quality package not found."));
    }

    private ConstructionScenarioEntity reqScenario(UUID id) {
        return scenarioRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("Scenario not found."));
    }

    private void validatePkg(UUID id) {
        if (id != null && !packageRepo.existsById(id)) throw new IllegalArgumentException("Quality package not found.");
    }

    private static String actor(Authentication a) {
        return a == null || a.getName() == null ? "system" : a.getName();
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    private record AssemblyResult(ConstructionScenarioItemEntity item, AssemblyDtos.EstimateResponse estimate) {
    }
}
