package com.sakhtyar.builder.domain;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BuilderProposalRevisionRepository extends JpaRepository<BuilderProposalRevisionEntity,UUID>{
    List<BuilderProposalRevisionEntity> findByProposalIdOrderByRevisionNoAsc(UUID proposalId);
}
