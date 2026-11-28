package com.sakhtyar.assembly.application;

import com.sakhtyar.material.application.MaterialPricePolicy;
import com.sakhtyar.material.domain.MaterialPriceAggregateEntity;
import com.sakhtyar.material.domain.MaterialPriceType;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Selects only a single compatible, still-current publication. No regional or FX fallback. */
public final class MaterialPriceResolver {
    private MaterialPriceResolver() {}
    public static MaterialPriceAggregateEntity resolve(List<MaterialPriceAggregateEntity> candidates,
            UUID item, UUID variant, MaterialPriceType type, String currency, String unit,
            String province, String city, Instant now) {
        var matches = candidates.stream()
                .filter(a -> Objects.equals(a.getMaterialItemId(), item))
                .filter(a -> Objects.equals(a.getVariantId(), variant) && a.getPriceType()==type)
                .filter(a -> MaterialPricePolicy.VERSION.equals(a.getAlgorithmVersion()))
                .filter(MaterialPriceAggregateEntity::isCurrentReference)
                .filter(a -> a.getExpiresAt()!=null && a.getExpiresAt().isAfter(now))
                .filter(a -> a.getPeriodEnd()!=null && !a.getPeriodEnd().isAfter(now))
                .filter(a -> a.getSampleCount()>0 && a.getEvidenceIds().size()==a.getSampleCount())
                .filter(a -> a.getCurrencyCode().equalsIgnoreCase(currency) && a.getUnitCode().equalsIgnoreCase(unit))
                .filter(a -> MaterialPricePolicy.region(a.getProvince()).equals(MaterialPricePolicy.region(province)))
                .filter(a -> MaterialPricePolicy.region(a.getCity()).equals(MaterialPricePolicy.region(city)))
                .toList();
        if(matches.size()!=1) throw new IllegalStateException("Expected exactly one eligible price reference for material " + item + "; found " + matches.size());
        return matches.getFirst();
    }
}
