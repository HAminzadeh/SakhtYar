package com.sakhtyar.builder.application;

import static com.sakhtyar.builder.api.BuilderDtos.*;
import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.builder.domain.*;
import com.sakhtyar.casefile.domain.CaseRepository;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BuilderService {
  private final BuilderProfileRepository builders;
  private final ProjectBuilderRepository links;
  private final CaseRepository cases;
  private final AuditService audit;

  public BuilderService(BuilderProfileRepository builders,ProjectBuilderRepository links,CaseRepository cases,AuditService audit){
    this.builders=builders;this.links=links;this.cases=cases;this.audit=audit;
  }

  @Transactional(readOnly=true)
  public List<BuilderResponse> list(){return builders.findAllByOrderByUpdatedAtDesc().stream().map(BuilderResponse::from).toList();}

  @Transactional(readOnly=true)
  public BuilderResponse get(UUID id){return BuilderResponse.from(require(id));}

  @Transactional
  public BuilderResponse create(UpsertBuilderRequest r){
    Instant now=Instant.now();
    var e=new BuilderProfileEntity(UUID.randomUUID(),r.legalName().trim(),clean(r.tradeName()),
      clean(r.nationalIdentifier()),clean(r.registrationNo()),clean(r.mobile()),clean(r.phone()),clean(r.email()),
      clean(r.website()),clean(r.province()),clean(r.city()),clean(r.address()),r.verified(),r.metadata(),now,now);
    builders.save(e);
    audit.record("BUILDER",e.getId(),"BUILDER_CREATED",Map.of("legalName",e.getLegalName(),"verified",e.isVerified()));
    return BuilderResponse.from(e);
  }

  @Transactional
  public BuilderResponse update(UUID id,UpsertBuilderRequest r){
    var e=require(id);
    e.update(r.legalName().trim(),clean(r.tradeName()),clean(r.nationalIdentifier()),clean(r.registrationNo()),
      clean(r.mobile()),clean(r.phone()),clean(r.email()),clean(r.website()),clean(r.province()),clean(r.city()),clean(r.address()),
      r.verified(),r.metadata());
    builders.save(e);
    audit.record("BUILDER",id,"BUILDER_UPDATED",Map.of("verified",e.isVerified()));
    return BuilderResponse.from(e);
  }

  @Transactional(readOnly=true)
  public List<ProjectBuilderResponse> byCase(UUID caseId){
    requireCase(caseId);
    return links.findByCaseIdOrderByCreatedAtAsc(caseId).stream().map(ProjectBuilderResponse::from).toList();
  }

  @Transactional
  public ProjectBuilderResponse link(UUID caseId,LinkBuilderRequest r){
    requireCase(caseId);require(r.builderId());
    if(r.primary()){
      for(var existing:links.findByCaseIdOrderByCreatedAtAsc(caseId)){
        if(existing.isPrimary()&&!existing.getBuilderId().equals(r.builderId()))
          throw new IllegalStateException("Case already has a primary builder.");
      }
    }
    var e=links.findByCaseIdAndBuilderId(caseId,r.builderId()).orElse(null);
    Instant now=Instant.now();
    if(e==null){
      e=new ProjectBuilderEntity(UUID.randomUUID(),caseId,r.builderId(),r.role().trim().toUpperCase(Locale.ROOT),
        r.status().trim().toUpperCase(Locale.ROOT),r.primary(),r.proposedSharePercent(),r.metadata(),now,now);
    }else{
      e.update(r.role().trim().toUpperCase(Locale.ROOT),r.status().trim().toUpperCase(Locale.ROOT),
        r.primary(),r.proposedSharePercent(),r.metadata());
    }
    links.save(e);
    audit.record("PROJECT_BUILDER",e.getId(),"PROJECT_BUILDER_LINKED",
      Map.of("caseId",caseId.toString(),"builderId",r.builderId().toString()));
    return ProjectBuilderResponse.from(e);
  }

  private BuilderProfileEntity require(UUID id){return builders.findById(id).orElseThrow(()->new IllegalArgumentException("Builder not found."));}
  private void requireCase(UUID id){if(!cases.existsById(id))throw new IllegalArgumentException("Case not found.");}
  private String clean(String s){if(s==null)return null;var v=s.trim();return v.isEmpty()?null:v;}
}
