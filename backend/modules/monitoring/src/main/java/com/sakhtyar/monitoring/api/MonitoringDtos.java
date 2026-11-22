package com.sakhtyar.monitoring.api;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
public final class MonitoringDtos {
  private MonitoringDtos(){}
  public record UpsertRuleRequest(
      @NotBlank @Size(max=120) String code,
      @NotBlank @Pattern(regexp="MARKET_MEDIAN_PRICE|MARKET_PRICE_CHANGE|MARKET_SAMPLE_COUNT") String metricCode,
      @NotBlank @Pattern(regexp="GT|GTE|LT|LTE|EQ") String operator,
      @NotNull BigDecimal threshold,
      @NotBlank @Size(max=120) String city,
      @Size(max=120) String district,
      @Min(1) @Max(3650) int periodDays,
      @NotBlank @Size(max=40) String notificationChannel,
      @NotBlank @Size(max=500) String notificationTarget,
      @Min(1) @Max(10080) int cooldownMinutes,
      boolean active
  ){}
  public record RuleResponse(
      UUID id,String code,String metricCode,String operator,BigDecimal threshold,String city,String district,
      int periodDays,String notificationChannel,String notificationTarget,int cooldownMinutes,
      boolean active,Instant createdAt,Instant updatedAt
  ){}
  public record EventResponse(
      UUID id,UUID ruleId,String status,BigDecimal observedValue,BigDecimal threshold,
      String message,Instant observedAt
  ){}
}
