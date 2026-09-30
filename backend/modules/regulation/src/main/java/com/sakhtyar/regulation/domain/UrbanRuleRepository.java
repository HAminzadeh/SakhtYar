package com.sakhtyar.regulation.domain;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface UrbanRuleRepository extends JpaRepository<UrbanRuleEntity,UUID>{
    Optional<UrbanRuleEntity> findByCodeIgnoreCase(String code);
    List<UrbanRuleEntity> findAllByOrderByPriorityAscCodeAsc();
}