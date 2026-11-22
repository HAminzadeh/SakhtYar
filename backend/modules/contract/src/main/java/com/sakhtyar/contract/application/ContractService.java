package com.sakhtyar.contract.application;

import static com.sakhtyar.contract.api.ContractDtos.*;
import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.casefile.domain.CaseRepository;
import com.sakhtyar.contract.domain.*;
import java.time.Instant;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContractService {
  private static final Map<String,List<String>> REQUIRED=Map.of(
    "PARTIES",List.of("PARTIES","طرفین","مالک","سازنده"),
    "PROPERTY",List.of("PROPERTY","ملک","پلاک ثبتی"),
    "SHARES",List.of("SHARES","سهم","قدرالسهم"),
    "DURATION",List.of("DURATION","مدت","زمان اجرا"),
    "OBLIGATIONS",List.of("OBLIGATIONS","تعهدات"),
    "DELAY_PENALTY",List.of("DELAY_PENALTY","وجه التزام","خسارت تأخیر"),
    "GUARANTEES",List.of("GUARANTEES","ضمانت","تضمین"),
    "TERMINATION",List.of("TERMINATION","فسخ"),
    "UNIT_ALLOCATION",List.of("UNIT_ALLOCATION","تقسیم واحد","تقسیم طبقات")
  );

  private final ContractRecordRepository repo;
  private final CaseRepository cases;
  private final AuditService audit;

  public ContractService(ContractRecordRepository repo,CaseRepository cases,AuditService audit){
    this.repo=repo;this.cases=cases;this.audit=audit;
  }

  @Transactional(readOnly=true)
  public List<ContractResponse> byCase(UUID caseId){
    requireCase(caseId);
    return repo.findByCaseIdOrderByUpdatedAtDesc(caseId).stream().map(ContractResponse::from).toList();
  }

  @Transactional(readOnly=true)
  public ContractResponse get(UUID id){return ContractResponse.from(require(id));}

  @Transactional
  public ContractResponse create(UUID caseId,UpsertContractRequest r,Authentication auth){
    requireCase(caseId);validate(r);
    if(repo.findByCaseIdAndContractCodeIgnoreCase(caseId,r.contractCode().trim()).isPresent())
      throw new IllegalArgumentException("Contract code already exists for case.");

    Instant now=Instant.now();
    var e=new ContractRecordEntity(
      UUID.randomUUID(),caseId,r.contractCode().trim().toUpperCase(Locale.ROOT),
      r.contractType().trim().toUpperCase(Locale.ROOT),r.status().trim().toUpperCase(Locale.ROOT),
      r.title().trim(),r.signedAt(),r.effectiveFrom(),r.effectiveTo(),r.schemaVersion().trim(),
      r.terms(),r.metadata(),actor(auth),now,now
    );
    repo.save(e);
    audit.record("CONTRACT",e.getId(),"CONTRACT_CREATED",Map.of("caseId",caseId.toString(),"code",e.getContractCode()));
    return ContractResponse.from(e);
  }

  @Transactional
  public ContractResponse update(UUID id,UpsertContractRequest r){
    validate(r);var e=require(id);
    if(!e.getContractCode().equalsIgnoreCase(r.contractCode().trim()))
      throw new IllegalArgumentException("contractCode is immutable.");
    e.update(r.contractType().trim().toUpperCase(Locale.ROOT),r.status().trim().toUpperCase(Locale.ROOT),
      r.title().trim(),r.signedAt(),r.effectiveFrom(),r.effectiveTo(),r.schemaVersion().trim(),r.terms(),r.metadata());
    repo.save(e);
    audit.record("CONTRACT",e.getId(),"CONTRACT_UPDATED",Map.of("status",e.getStatus()));
    return ContractResponse.from(e);
  }

  @Transactional(readOnly=true)
  public ContractReviewResponse review(UUID caseId){
    List<ContractRecordEntity> all=repo.findByCaseIdOrderByUpdatedAtDesc(caseId);
    if(all.isEmpty())throw new IllegalArgumentException("No contract registered for case.");
    ContractRecordEntity e=all.get(0);
    Map<String,Object> terms=e.getTerms();
    String flattened=terms.toString();
    List<String> present=new ArrayList<>(),missing=new ArrayList<>();

    REQUIRED.forEach((code,keys)->{
      boolean ok=terms.containsKey(code)||terms.containsKey(code.toLowerCase(Locale.ROOT))
        ||keys.stream().anyMatch(flattened::contains);
      if(ok)present.add(code);else missing.add(code);
    });

    double completeness=REQUIRED.isEmpty()?1.0:((double)present.size()/REQUIRED.size());
    return new ContractReviewResponse(
      e.getId(),e.getContractCode(),e.getStatus(),present,missing,completeness,
      "این کنترل ساختاری است و جایگزین بررسی وکیل یا کارشناس حقوقی نیست."
    );
  }

  private void validate(UpsertContractRequest r){
    if(r.effectiveFrom()!=null&&r.effectiveTo()!=null&&r.effectiveTo().isBefore(r.effectiveFrom()))
      throw new IllegalArgumentException("effectiveTo cannot be before effectiveFrom.");
  }
  private ContractRecordEntity require(UUID id){return repo.findById(id).orElseThrow(()->new IllegalArgumentException("Contract not found."));}
  private void requireCase(UUID id){if(!cases.existsById(id))throw new IllegalArgumentException("Case not found.");}
  private String actor(Authentication a){return a==null||a.getName()==null?"system":a.getName();}
}
