package com.sakhtyar.builder.domain;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BuilderAccountBindingRepository extends JpaRepository<BuilderAccountBindingEntity,UUID> {
    Optional<BuilderAccountBindingEntity> findByUsernameIgnoreCase(String username);
}
