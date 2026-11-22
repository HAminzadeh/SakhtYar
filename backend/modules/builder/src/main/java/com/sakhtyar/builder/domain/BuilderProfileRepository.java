package com.sakhtyar.builder.domain;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BuilderProfileRepository extends JpaRepository<BuilderProfileEntity,UUID>{
  List<BuilderProfileEntity> findAllByOrderByUpdatedAtDesc();
  List<BuilderProfileEntity> findByVerifiedTrueOrderByUpdatedAtDesc();
}
