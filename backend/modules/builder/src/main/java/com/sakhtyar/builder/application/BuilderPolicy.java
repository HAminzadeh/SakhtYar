package com.sakhtyar.builder.application;

import com.sakhtyar.builder.api.BuilderDtos.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

/** Common validation and actor boundary. No untrusted field grants verification or identity. */
public final class BuilderPolicy {
    private BuilderPolicy() { }
    private static final Set<String> RESERVED=Set.of("verified","approved","signed","signature",
            "accepted","consent","verificationstatus","verificationevidence","legallybinding");
    public static Authentication actor() {
        Authentication a=SecurityContextHolder.getContext().getAuthentication();
        if(a==null || !a.isAuthenticated() || a.getName()==null || a.getName().isBlank()
                || a.getAuthorities()==null || has(a,"ROLE_TESTER"))
            throw deny();
        return a;
    }
    public static boolean has(Authentication a,String authority) {
        return a!=null && a.getAuthorities()!=null && a.getAuthorities().stream()
                .anyMatch(x->authority.equals(x.getAuthority()));
    }
    public static String username() {return actor().getName();}
    public static void authority(String permission) {if(!has(actor(),permission))throw deny();}
    public static boolean manager() {return has(actor(),"USER_MANAGE");}
    public static ResponseStatusException deny() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND,"Resource not found.");
    }
    public static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);
    }
    public static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT,message);
    }
    public static void profile(UpsertBuilderRequest r) {
        if(r==null || blank(r.legalName()) || r.legalName().trim().length()>250)throw invalid("Valid legalName is required.");
        if(r.metadata()!=null)metadata(r.metadata());
        if(r.nationalIdentifier()!=null && !r.nationalIdentifier().isBlank() &&
                !r.nationalIdentifier().trim().matches("[0-9A-Za-z-]{4,30}"))
            throw invalid("Invalid nationalIdentifier.");
    }
    public static void link(LinkBuilderRequest r) {
        if(r==null || r.builderId()==null)throw invalid("builderId is required.");
        if(!"CANDIDATE".equals(normal(r.role())) || !"ACTIVE".equals(normal(r.status())) || r.primary())
            throw conflict("Manual link is candidate-only; selection requires an accepted proposal.");
        share(r.proposedSharePercent());metadata(r.metadata());
    }
    public static void share(BigDecimal n) {
        if(n!=null && (n.signum()<0 || n.compareTo(new BigDecimal("100"))>0 || n.scale()>4))
            throw invalid("Share must be between 0 and 100 with up to 4 decimal places.");
    }
    public static void proposal(SubmitProposalRequest r) {
        if(r==null || r.proposedSharePercent()==null || r.durationMonths()==null ||
                r.durationMonths()<1 || r.durationMonths()>240)throw invalid("Invalid proposal duration or share.");
        share(r.proposedSharePercent());metadata(r.terms());
    }
    public static void metadata(Map<String,Object> m) {
        if(m==null)return;
        if(m.size()>40)throw invalid("Metadata limit exceeded.");
        int total=0;
        for(var e:m.entrySet()) {
            if(e.getKey()==null || !e.getKey().matches("[a-zA-Z0-9_\\-]{1,80}") ||
                    RESERVED.contains(e.getKey().replace("_","").toLowerCase(Locale.ROOT)))
                throw invalid("Reserved or invalid metadata key.");
            if(e.getValue() instanceof Map || e.getValue() instanceof Collection) {
                if(e.getValue().toString().length()>1500)throw invalid("Complex metadata too large.");
            }
            if(e.getValue()==null)throw invalid("Null metadata values are not allowed.");
            if(e.getKey().equals("available") && !(e.getValue() instanceof Boolean))
                throw invalid("available must be boolean.");
            if(Set.of("capitalCapacity","maxProjectAreaM2").contains(e.getKey())){
                try { BigDecimal n=new BigDecimal(e.getValue().toString());
                    if(n.signum()<0 || n.precision()>24 || n.scale()>4)throw invalid("Invalid capacity.");
                } catch(NumberFormatException x){throw invalid("Invalid capacity.");}
            }
            if(e.getKey().equals("experienceYears")){
                try {int years=Integer.parseInt(e.getValue().toString());if(years<0||years>100)throw invalid("Invalid experience years.");}
                catch(NumberFormatException x){throw invalid("Invalid experience years.");}
            }
            if(e.getKey().equals("districts") && (!(e.getValue() instanceof Collection<?> c) || c.size()>50 ||
                    c.stream().anyMatch(x->!(x instanceof String q)||q.isBlank()||q.length()>100)))
                throw invalid("Invalid districts list.");
            total+=e.getValue().toString().length()+e.getKey().length();
        }
        if(total>8000)throw invalid("Metadata size exceeded.");
    }
    public static String normal(String s){return s==null?"":s.trim().toUpperCase(Locale.ROOT);}
    public static boolean blank(String s){return s==null || s.isBlank();}
    public static String clean(String s){return blank(s)?null:s.trim();}
}
