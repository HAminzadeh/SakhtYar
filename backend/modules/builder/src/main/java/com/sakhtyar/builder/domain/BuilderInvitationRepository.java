package com.sakhtyar.builder.domain;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface BuilderInvitationRepository extends JpaRepository<BuilderInvitationEntity,UUID>{
    @Query("select i.caseId from BuilderInvitationEntity i where i.id = :id")
    Optional<UUID> findCaseIdById(@Param("id") UUID id);
    @Query("select i.builderId from BuilderInvitationEntity i where i.id = :id")
    Optional<UUID> findBuilderIdById(@Param("id") UUID id);
    List<BuilderInvitationEntity> findByCaseIdOrderByCreatedAtDesc(UUID caseId);
    List<BuilderInvitationEntity> findByBuilderIdOrderByCreatedAtDesc(UUID builderId);
    List<BuilderInvitationEntity> findByCaseIdAndBuilderIdOrderByCreatedAtDesc(UUID caseId,UUID builderId);
}
