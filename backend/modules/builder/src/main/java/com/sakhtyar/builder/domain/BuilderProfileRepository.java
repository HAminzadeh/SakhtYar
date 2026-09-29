package com.sakhtyar.builder.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BuilderProfileRepository extends JpaRepository<BuilderProfileEntity, UUID> {
}