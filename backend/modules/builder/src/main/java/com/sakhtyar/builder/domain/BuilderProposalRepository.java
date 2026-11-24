package com.sakhtyar.builder.domain;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface BuilderProposalRepository extends JpaRepository<BuilderProposalEntity,UUID>{
    @Query("select p.caseId from BuilderProposalEntity p where p.id = :id")
    Optional<UUID> findCaseIdById(@Param("id") UUID id);
    @Query("select p.builderId from BuilderProposalEntity p where p.id = :id")
    Optional<UUID> findBuilderIdById(@Param("id") UUID id);
    Optional<BuilderProposalEntity> findByInvitationId(UUID invitationId);
    List<BuilderProposalEntity> findByCaseIdOrderByUpdatedAtDesc(UUID caseId);
    boolean existsByCaseIdAndStatus(UUID caseId,String status);
}
