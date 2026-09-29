package com.sakhtyar.material.application;

import com.sakhtyar.material.domain.PriceAnomalySeverity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class PriceAnomalyDetector {

    private static final BigDecimal MEDIUM_THRESHOLD = new BigDecimal("0.50");
    private static final BigDecimal HIGH_THRESHOLD = new BigDecimal("1.00");

    public Optional<Result> detect(BigDecimal candidate, List<BigDecimal> historicalApprovedPrices) {
        if (candidate == null || historicalApprovedPrices == null || historicalApprovedPrices.size() < 3) {
            return Optional.empty();
        }

        BigDecimal median = PriceStatistics.calculate(historicalApprovedPrices).median();
        if (median.signum() == 0) {
            return Optional.empty();
        }

        BigDecimal deviation = candidate.subtract(median).abs()
                .divide(median, 4, RoundingMode.HALF_UP);

        if (deviation.compareTo(MEDIUM_THRESHOLD) < 0) {
            return Optional.empty();
        }

        PriceAnomalySeverity severity = deviation.compareTo(HIGH_THRESHOLD) >= 0
                ? PriceAnomalySeverity.HIGH
                : PriceAnomalySeverity.MEDIUM;

        return Optional.of(new Result(
                "MEDIAN_DEVIATION",
                severity,
                deviation,
                "Observed price deviates materially from the median of previously approved observations."
        ));
    }

    public record Result(
            String type,
            PriceAnomalySeverity severity,
            BigDecimal score,
            String reason
    ) {}
}