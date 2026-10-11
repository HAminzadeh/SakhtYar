package com.sakhtyar.builder.domain;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProjectBuilderRepository extends JpaRepository<ProjectBuilderEntity,UUID>{
  List<ProjectBuilderEntity> findByCaseIdOrderByCreatedAtAsc(UUID caseId);
  Optional<ProjectBuilderEntity> findByCaseIdAndBuilderId(UUID caseId,UUID builderId);
}
