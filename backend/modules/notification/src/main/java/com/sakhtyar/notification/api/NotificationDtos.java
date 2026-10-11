package com.sakhtyar.notification.api;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
public final class NotificationDtos {
  private NotificationDtos(){}
  public record EnqueueRequest(
      @NotBlank @Size(max=40) String channel,
      @NotBlank @Size(max=500) String target,
      @NotBlank @Size(max=300) String subject,
      @NotBlank @Size(max=10000) String body,
      Map<String,Object> metadata
  ){}
  public record NotificationResponse(
      UUID id,String channel,String target,String subject,String body,String status,
      int attemptCount,String lastError,Map<String,Object> metadata,Instant createdAt,Instant sentAt
  ){}
}
