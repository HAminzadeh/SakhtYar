package com.sakhtyar.contract.application;

import static com.sakhtyar.contract.api.ContractDtos.*;
import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.casefile.application.CaseAccessService;
import com.sakhtyar.casefile.application.CaseLifecycle;
import com.sakhtyar.casefile.domain.CaseEntity;
import com.sakhtyar.casefile.domain.CaseRepository;
import com.sakhtyar.contract.domain.*;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Draft-only contracts. Legal approval and electronic signatures are intentionally NOT implemented. */
@Service
public class ContractService {
    private static final Map<String,List<String>> REQUIRED;
    static {
        Map<String,List<String>> sections=new LinkedHashMap<>();
        sections.put("PARTIES",List.of("PARTIES","طرفین","مالک","سازنده"));
        sections.put("PROPERTY",List.of("PROPERTY","ملک","پلاک ثبتی"));
        sections.put("SHARES",List.of("SHARES","سهم","قدرالسهم"));
        sections.put("DURATION",List.of("DURATION","مدت","زمان اجرا"));
        sections.put("OBLIGATIONS",List.of("OBLIGATIONS","تعهدات"));
        sections.put("DELAY_PENALTY",List.of("DELAY_PENALTY","وجه التزام","خسارت تأخیر"));
        sections.put("GUARANTEES",List.of("GUARANTEES","ضمانت","تضمین"));
        sections.put("TERMINATION",List.of("TERMINATION","فسخ"));
        sections.put("UNIT_ALLOCATION",List.of("UNIT_ALLOCATION","تقسیم واحد","تقسیم طبقات"));
        REQUIRED=Collections.unmodifiableMap(sections);
    }
    private static final String DISCLAIMER="این کنترل صرفاً ساختاری است؛ جایگزین تأیید حقوقی، رضایت یا امضای معتبر نیست.";
    private final ContractRecordRepository repo;
    private final ContractRevisionRepository revisions;
    private final CaseRepository cases;
    private final CaseAccessService caseAccess;
    private final AuditService audit;

    public ContractService(ContractRecordRepository repo,ContractRevisionRepository revisions,
            CaseRepository cases,CaseAccessService caseAccess,AuditService audit) {
        this.repo=repo;this.revisions=revisions;this.cases=cases;this.caseAccess=caseAccess;this.audit=audit;
    }

    @Transactional(readOnly=true)
    public List<ContractResponse> byCase(UUID caseId) {
        requireCase(caseId,false);
        return repo.findByCaseIdOrderByUpdatedAtDesc(caseId).stream().map(ContractResponse::from).toList();
    }

    @Transactional(readOnly=true)
    public ContractResponse get(UUID id) {
        ContractRecordEntity c=contract(id);
        requireCase(c.getCaseId(),false);
        return ContractResponse.from(c);
    }

    @Transactional
    public ContractResponse create(UUID caseId,UpsertContractRequest r,Authentication ignored) {
        ContractInputPolicy.validateDraft(r);
        requireCase(caseId,true); // parent row lock is the first lock for every contract write
        String code=r.contractCode().trim().toUpperCase(Locale.ROOT);
        if (repo.findByCaseIdAndContractCodeIgnoreCase(caseId,code).isPresent())
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Contract code already exists for case.");
        Instant now=Instant.now();
        var c=new ContractRecordEntity(UUID.randomUUID(),caseId,code,
                r.contractType().trim().toUpperCase(Locale.ROOT),"DRAFT",r.title().trim(),
                null,r.effectiveFrom(),r.effectiveTo(),r.schemaVersion().trim(),
                r.terms(),r.metadata(),currentActor(),now,now);
        repo.saveAndFlush(c); // expose unique constraint failures before audit success
        recordRevision(c,1,"CREATED");
        audit.record("CONTRACT",c.getId(),"CONTRACT_CREATED",Map.of("status","DRAFT","revision",1));
        return ContractResponse.from(c);
    }

    /** Legacy PUT remains valid; clients may opt into If-Match: "<revisionNo>". */
    @Transactional
    public ContractResponse update(UUID id,UpsertContractRequest r) { return update(id,r,null); }

    @Transactional
    public ContractResponse update(UUID id,UpsertContractRequest r,String ifMatch) {
        ContractInputPolicy.validateDraft(r);
        // Scalar case-id projection must happen BEFORE the parent lock. Do not load the
        // entity into Hibernate's first-level cache before acquiring that lock.
        UUID parentId=repo.findCaseIdById(id).orElseThrow(()->
                new ResponseStatusException(HttpStatus.NOT_FOUND,"Contract not found."));
        requireCase(parentId,true);
        ContractRecordEntity c=contract(id);
        if (!parentId.equals(c.getCaseId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Contract not found.");
        if (!"DRAFT".equals(c.getStatus()) || c.getSignedAt()!=null)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Non-draft or legacy signed contracts are read-only.");
        if (!c.getContractCode().equalsIgnoreCase(r.contractCode().trim()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"contractCode is immutable.");
        var latest=revisions.findTopByContractIdOrderByRevisionNoDesc(id);
        int current=latest.map(ContractRevisionEntity::getRevisionNo).orElse(1);
        if (ifMatch!=null) {
            if (!ifMatch.matches("\"[1-9][0-9]{0,8}\""))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"If-Match must be a quoted revision number.");
            int expected=Integer.parseInt(ifMatch.substring(1,ifMatch.length()-1));
            if (expected!=current) throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED,
                    "Contract revision changed. Refresh before editing.");
        }
        if (current==Integer.MAX_VALUE) throw new ResponseStatusException(HttpStatus.CONFLICT,"Revision limit exceeded.");
        if (latest.isEmpty()) recordRevision(c,1,"BASELINE_CAPTURED"); // legacy record, not legal evidence
        c.update(r.contractType().trim().toUpperCase(Locale.ROOT),"DRAFT",r.title().trim(),null,
                r.effectiveFrom(),r.effectiveTo(),r.schemaVersion().trim(),r.terms(),r.metadata());
        repo.saveAndFlush(c);
        recordRevision(c,current+1,"UPDATED");
        audit.record("CONTRACT",c.getId(),"CONTRACT_UPDATED",Map.of("status","DRAFT","revision",current+1));
        return ContractResponse.from(c);
    }

    @Transactional(readOnly=true)
    public List<ContractRevisionResponse> versions(UUID id) {
        ContractRecordEntity c=contract(id);
        requireCase(c.getCaseId(),false);
        return revisions.findByContractIdOrderByRevisionNoAsc(id).stream().map(ContractRevisionResponse::from).toList();
    }

    @Transactional(readOnly=true)
    public ContractRevisionResponse version(UUID id,int number) {
        ContractRecordEntity c=contract(id);
        requireCase(c.getCaseId(),false);
        if(number<1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid revision number.");
        return ContractRevisionResponse.from(revisions.findByContractIdAndRevisionNo(id,number)
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Revision not found.")));
    }

    @Transactional(readOnly=true)
    public ContractReviewResponse review(UUID caseId) {
        requireCase(caseId,false);
        List<ContractRecordEntity> all=repo.findByCaseIdOrderByUpdatedAtDesc(caseId);
        if(all.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"No contract registered for case.");
        ContractRecordEntity c=all.get(0);
        Map<String,Object> terms=c.getTerms();
        Object nested=terms.get("clauses");
        Map<?,?> clauses=nested instanceof Map<?,?> m?m:Map.of();
        List<String> present=new ArrayList<>(),missing=new ArrayList<>();
        REQUIRED.forEach((name,aliases)->{
            boolean exists=aliases.stream().anyMatch(alias->hasNonemptyKey(terms,alias)||hasNonemptyKey(clauses,alias));
            if(exists) present.add(name); else missing.add(name);
        });
        return new ContractReviewResponse(c.getId(),c.getContractCode(),c.getStatus(),present,missing,
                (double)present.size()/REQUIRED.size(),DISCLAIMER);
    }

    private boolean hasNonemptyKey(Map<?,?> value,String alias) {
        for (var entry:value.entrySet()) {
            if (entry.getKey() instanceof String key && key.equalsIgnoreCase(alias) && meaningful(entry.getValue())) return true;
        }
        return false;
    }
    private boolean meaningful(Object value) {
        if(value==null) return false;
        if(value instanceof String s) return !s.isBlank();
        if(value instanceof Map<?,?> m) return !m.isEmpty();
        if(value instanceof Collection<?> c) return !c.isEmpty();
        if(value instanceof Boolean b) return b;
        return true;
    }

    private void recordRevision(ContractRecordEntity c,int number,String event) {
        Map<String,Object> snapshot=new LinkedHashMap<>();
        snapshot.put("contractCode",c.getContractCode());
        snapshot.put("contractType",c.getContractType());
        snapshot.put("status",c.getStatus());
        snapshot.put("title",c.getTitle());
        snapshot.put("schemaVersion",c.getSchemaVersion());
        snapshot.put("signedAt",c.getSignedAt()==null?null:c.getSignedAt().toString());
        snapshot.put("effectiveFrom",c.getEffectiveFrom()==null?null:c.getEffectiveFrom().toString());
        snapshot.put("effectiveTo",c.getEffectiveTo()==null?null:c.getEffectiveTo().toString());
        snapshot.put("terms",c.getTerms());
        snapshot.put("metadata",c.getMetadata());
        revisions.saveAndFlush(new ContractRevisionEntity(UUID.randomUUID(),c.getId(),number,event,
                snapshot,currentActor(),Instant.now()));
    }

    private ContractRecordEntity contract(UUID id) {
        if(id==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Contract not found.");
        return repo.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Contract not found."));
    }

    private CaseEntity requireCase(UUID caseId,boolean write) {
        requireAuthority(write?"CASE_WRITE":"CASE_READ");
        CaseEntity c=(write?cases.findByIdForUpdate(caseId):cases.findById(caseId))
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Case not found."));
        if (!"REAL".equals(c.getDataScope()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Case not found.");
        if (write) {
            caseAccess.requireWrite(c);
            CaseLifecycle.requireEditable(c.getStatus(),c.getStatus());
        } else caseAccess.requireRead(c);
        return c;
    }
    private void requireAuthority(String permission) {
        Authentication a=SecurityContextHolder.getContext().getAuthentication();
        if(a==null||!a.isAuthenticated()||a.getName()==null||a.getAuthorities()==null||
                a.getAuthorities().stream().anyMatch(x->"ROLE_TESTER".equals(x.getAuthority()))||
                a.getAuthorities().stream().noneMatch(x->permission.equals(x.getAuthority())))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Case not found.");
    }
    private String currentActor() {
        Authentication a=SecurityContextHolder.getContext().getAuthentication();
        if(a==null||!a.isAuthenticated()||a.getName()==null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Case not found.");
        return a.getName();
    }
}
