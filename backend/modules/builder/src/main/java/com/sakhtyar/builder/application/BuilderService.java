package com.sakhtyar.builder.application;

import static com.sakhtyar.builder.api.BuilderDtos.*;
import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.builder.domain.*;
import com.sakhtyar.casefile.domain.*;
import com.sakhtyar.casefile.application.*;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Global builder directory is administratively maintained. No direct verification toggle. */
@Service
public class BuilderService {
    private final BuilderProfileRepository builders;
    private final ProjectBuilderRepository links;
    private final CaseRepository cases;
    private final CaseAccessService caseAccess;
    private final AuditService audit;
    private final BuilderAccountBindingRepository bindings;
    private final BuilderVerificationRepository reviews;
    private final JdbcTemplate jdbc;
    public BuilderService(BuilderProfileRepository builders,ProjectBuilderRepository links,
            CaseRepository cases,CaseAccessService caseAccess,AuditService audit,
            BuilderAccountBindingRepository bindings,BuilderVerificationRepository reviews,JdbcTemplate jdbc) {
        this.builders=builders;this.links=links;this.cases=cases;this.caseAccess=caseAccess;
        this.audit=audit;this.bindings=bindings;this.reviews=reviews;this.jdbc=jdbc;
    }
    @Transactional(readOnly=true)
    public List<BuilderResponse> list(){
        BuilderPolicy.authority("CASE_READ");boolean full=BuilderPolicy.manager();
        List<BuilderProfileEntity> items=full?builders.findAllByOrderByUpdatedAtDesc():builders.findByVerifiedTrueOrderByUpdatedAtDesc();
        return items.stream().limit(500).map(e->BuilderResponse.from(e,full)).toList();
    }
    @Transactional(readOnly=true)
    public BuilderPageResponse page(int page,int size,String city){
        BuilderPolicy.authority("CASE_READ");
        if(page<0 || page>100000 || size<1 || size>100)throw BuilderPolicy.invalid("Invalid page or size.");
        boolean full=BuilderPolicy.manager();
        Pageable paging=PageRequest.of(page,size,Sort.by(Sort.Order.desc("updatedAt"),Sort.Order.asc("id")));
        String filter=BuilderPolicy.clean(city);
        if(filter!=null && filter.length()>100)throw BuilderPolicy.invalid("Invalid city filter.");
        Page<BuilderProfileEntity> result=filter==null
            ? (full?builders.findAll(paging):builders.findByVerifiedTrue(paging))
            : (full?builders.findByCityIgnoreCase(filter,paging):builders.findByVerifiedTrueAndCityIgnoreCase(filter,paging));
        return new BuilderPageResponse(result.getContent().stream().map(e->BuilderResponse.from(e,full)).toList(),
                result.getTotalElements(),page,size);
    }
    @Transactional(readOnly=true)
    public BuilderResponse get(UUID id){
        BuilderPolicy.authority("CASE_READ");BuilderProfileEntity e=require(id);
        if(!BuilderPolicy.manager() && !e.isVerified())throw BuilderPolicy.deny();
        return BuilderResponse.from(e,BuilderPolicy.manager());
    }
    @Transactional
    public BuilderResponse create(UpsertBuilderRequest r){
        BuilderPolicy.authority("USER_MANAGE");BuilderPolicy.profile(r);
        if(r.verified())throw BuilderPolicy.invalid("Use explicit reviewed verification; no direct verified flag.");
        String national=BuilderPolicy.clean(r.nationalIdentifier());
        if(national!=null && builders.existsByNationalIdentifierIgnoreCase(national))throw BuilderPolicy.conflict("Duplicate identifier.");
        Instant now=Instant.now();
        var e=new BuilderProfileEntity(UUID.randomUUID(),r.legalName().trim(),BuilderPolicy.clean(r.tradeName()),
                national,BuilderPolicy.clean(r.registrationNo()),BuilderPolicy.clean(r.mobile()),
                BuilderPolicy.clean(r.phone()),BuilderPolicy.clean(r.email()),BuilderPolicy.clean(r.website()),
                BuilderPolicy.clean(r.province()),BuilderPolicy.clean(r.city()),BuilderPolicy.clean(r.address()),
                false,r.metadata(),now,now);
        builders.saveAndFlush(e);
        audit.record("BUILDER",e.getId(),"BUILDER_CREATED",Map.of("reviewRequired",true));
        return BuilderResponse.from(e,true);
    }
    @Transactional
    public BuilderResponse update(UUID id,UpsertBuilderRequest r){
        BuilderPolicy.authority("USER_MANAGE");BuilderPolicy.profile(r);
        var e=builders.findByIdForUpdate(id).orElseThrow(BuilderPolicy::deny);
        if(r.verified()!=e.isVerified())throw BuilderPolicy.invalid("Verification changes require a review action.");
        String national=BuilderPolicy.clean(r.nationalIdentifier());
        if(national!=null && builders.existsByNationalIdentifierIgnoreCaseAndIdNot(national,id))throw BuilderPolicy.conflict("Duplicate identifier.");
        e.update(r.legalName().trim(),BuilderPolicy.clean(r.tradeName()),national,BuilderPolicy.clean(r.registrationNo()),
                BuilderPolicy.clean(r.mobile()),BuilderPolicy.clean(r.phone()),BuilderPolicy.clean(r.email()),
                BuilderPolicy.clean(r.website()),BuilderPolicy.clean(r.province()),BuilderPolicy.clean(r.city()),
                BuilderPolicy.clean(r.address()),e.isVerified(),r.metadata());
        builders.saveAndFlush(e);
        audit.record("BUILDER",e.getId(),"BUILDER_UPDATED",Map.of("verified",e.isVerified()));
        return BuilderResponse.from(e,true);
    }
    /** Human admin-reviewed evidence reference; does not certify legal or financial capacity. */
    @Transactional
    public BuilderResponse review(VerifyBuilderRequest r){
        BuilderPolicy.authority("USER_MANAGE");
        if(r==null || r.builderId()==null || r.approved()==null || BuilderPolicy.blank(r.evidenceReference()))
            throw BuilderPolicy.invalid("Review requires decision and reference.");
        BuilderProfileEntity e=builders.findByIdForUpdate(r.builderId()).orElseThrow(BuilderPolicy::deny);
        e.update(e.getLegalName(),e.getTradeName(),e.getNationalIdentifier(),e.getRegistrationNo(),
                e.getMobile(),e.getPhone(),e.getEmail(),e.getWebsite(),e.getProvince(),e.getCity(),
                e.getAddress(),r.approved(),e.getMetadata());
        builders.saveAndFlush(e);
        reviews.saveAndFlush(new BuilderVerificationEntity(UUID.randomUUID(),e.getId(),
                r.approved()?"APPROVED":"REJECTED",r.evidenceReference().trim(),BuilderPolicy.username(),Instant.now()));
        audit.record("BUILDER",e.getId(),"BUILDER_REVIEWED",Map.of("approved",r.approved()));
        return BuilderResponse.from(e,true);
    }
    /** Binding is assigned by a privileged operator to an EXISTING active user. */
    @Transactional
    public void bind(AccountBindingRequest r){
        BuilderPolicy.authority("USER_MANAGE");
        if(r==null||r.builderId()==null||BuilderPolicy.blank(r.username())||BuilderPolicy.blank(r.reviewReference()))
            throw BuilderPolicy.invalid("Reviewed binding and existing user required.");
        builders.findByIdForUpdate(r.builderId()).orElseThrow(BuilderPolicy::deny);
        if(bindings.existsById(r.builderId())||bindings.findByUsernameIgnoreCase(r.username().trim()).isPresent())
            throw BuilderPolicy.conflict("Builder or user already bound.");
        List<String> usernames=jdbc.queryForList("select username from app_user where lower(username)=lower(?) and active=true",
                String.class,r.username().trim());
        if(usernames.size()!=1)throw BuilderPolicy.invalid("Active account not found or ambiguous.");
        bindings.saveAndFlush(new BuilderAccountBindingEntity(r.builderId(),usernames.get(0),r.reviewReference().trim(),
                BuilderPolicy.username(),Instant.now()));
        audit.record("BUILDER",r.builderId(),"BUILDER_ACCOUNT_BOUND",Map.of("identityReviewed",true));
    }
    @Transactional(readOnly=true)
    public List<ProjectBuilderResponse> byCase(UUID caseId){
        BuilderPolicy.authority("CASE_READ");caseAccess.requireRead(caseId);
        return links.findByCaseIdOrderByCreatedAtAsc(caseId).stream().map(ProjectBuilderResponse::from).toList();
    }
    /** Legacy manual link is limited to candidates; selection must originate in a proposal decision. */
    @Transactional
    public ProjectBuilderResponse link(UUID caseId,LinkBuilderRequest r){
        BuilderPolicy.authority("USER_MANAGE");BuilderPolicy.authority("CASE_WRITE");BuilderPolicy.link(r);
        CaseEntity parent=cases.findByIdForUpdate(caseId).orElseThrow(BuilderPolicy::deny);
        caseAccess.requireWrite(parent);CaseLifecycle.requireEditable(parent.getStatus(),parent.getStatus());
        require(r.builderId());
        ProjectBuilderEntity e=links.findByCaseIdAndBuilderId(caseId,r.builderId()).orElse(null);
        Instant now=Instant.now();
        if(e==null)e=new ProjectBuilderEntity(UUID.randomUUID(),caseId,r.builderId(),"CANDIDATE","ACTIVE",false,
                r.proposedSharePercent(),r.metadata(),now,now);
        else {
            if(e.isPrimary())throw BuilderPolicy.conflict("Selected builder cannot be overwritten by candidate link.");
            e.update("CANDIDATE","ACTIVE",false,r.proposedSharePercent(),r.metadata());
        }
        links.saveAndFlush(e);
        audit.record("PROJECT_BUILDER",e.getId(),"PROJECT_BUILDER_CANDIDATE_LINKED",Map.of("caseId",caseId.toString()));
        return ProjectBuilderResponse.from(e);
    }
    private BuilderProfileEntity require(UUID id){return builders.findById(id).orElseThrow(BuilderPolicy::deny);}
}
