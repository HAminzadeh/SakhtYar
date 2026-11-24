package com.sakhtyar.builder.application;

import static com.sakhtyar.builder.api.BuilderDtos.*;
import com.sakhtyar.builder.domain.*;
import com.sakhtyar.casefile.application.CaseAccessService;
import com.sakhtyar.property.domain.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.*;
import org.springframework.transaction.annotation.Transactional;

/** V2 scoring: self-reported signals, not certified technical/financial qualifications. */
@Service
public class BuilderMatchingService {
    public static final String CRITERIA_VERSION="BUILDER_SCORE_V2";
    private final BuilderProfileRepository builders;
    private final PropertyRepository properties;
    private final CaseAccessService caseAccess;
    public BuilderMatchingService(BuilderProfileRepository builders,PropertyRepository properties,CaseAccessService caseAccess){
        this.builders=builders;this.properties=properties;this.caseAccess=caseAccess;
    }
    @Transactional(readOnly=true)
    public MatchResponse match(UUID caseId,BigDecimal requiredCapital,int limit){
        BuilderPolicy.authority("CASE_READ");caseAccess.requireRead(caseId);
        if(requiredCapital!=null && requiredCapital.signum()<0)throw BuilderPolicy.invalid("requiredCapital must be non-negative.");
        if(limit<1 || limit>100)throw BuilderPolicy.invalid("limit must be 1..100.");
        PropertyEntity property=properties.findByCaseId(caseId).orElseThrow(()->BuilderPolicy.invalid("Property not registered."));
        // Exact top-K over bounded pages. Never load all profiles into heap memory.
        Comparator<MatchItem> best=Comparator.comparingInt(MatchItem::score).reversed()
                .thenComparing(x->x.builderId().toString());
        PriorityQueue<MatchItem> top=new PriorityQueue<>(best.reversed());
        int candidateCount=0;
        for(int pageNumber=0;pageNumber<10000;pageNumber++){
            Page<BuilderProfileEntity> batch=builders.findByVerifiedTrue(PageRequest.of(pageNumber,250,
                Sort.by(Sort.Order.desc("updatedAt"),Sort.Order.asc("id"))));
            if(batch==null)throw new IllegalStateException("Verified builder page unavailable.");
            for(BuilderProfileEntity b:batch.getContent()){
                if(Boolean.FALSE.equals(booleanValue(b.getMetadata().get("available"))))continue;
                candidateCount++;
                top.add(score(b,property,requiredCapital));
                if(top.size()>limit)top.poll(); // removes worst under reversed comparator
            }
            if(!batch.hasNext()){
                List<MatchItem> result=new ArrayList<>(top);result.sort(best);
                return new MatchResponse(caseId,property.getDistrict(),property.getLandAreaM2(),requiredCapital,
                        candidateCount,result);
            }
        }
        throw new IllegalStateException("Matching capacity exceeded; refuse incomplete ranking.");
    }
    private MatchItem score(BuilderProfileEntity b,PropertyEntity p,BigDecimal required){
        int value=10;
        List<String> reasons=new ArrayList<>();reasons.add("پروفایل در فهرست بررسی‌شده مدیر قرار دارد (+10)");
        Map<String,Object> m=b.getMetadata();
        Boolean available=booleanValue(m.get("available"));
        if(Boolean.TRUE.equals(available)){value+=20;reasons.add("ظرفیت پذیرش اعلام شده (+20)");}
        if(eq(b.getCity(),p.getCity())){value+=10;reasons.add("شهر یکسان (+10)");}
        List<String> districts=strings(m.get("districts"));
        if(p.getDistrict()!=null && districts.stream().anyMatch(x->eq(x,p.getDistrict()))){value+=25;reasons.add("منطقه در فهرست فعالیت (+25)");}
        BigDecimal area=nonnegative(m.get("maxProjectAreaM2"));
        if(p.getLandAreaM2()!=null && p.getLandAreaM2().signum()>0 && area!=null && area.compareTo(p.getLandAreaM2())>=0){
            value+=20;reasons.add("ظرفیت متراژ خوداظهاری کافی (+20)");
        }
        BigDecimal capital=nonnegative(m.get("capitalCapacity"));
        if(required!=null && required.signum()>0 && capital!=null && capital.compareTo(required)>=0){value+=10;reasons.add("ظرفیت سرمایه خوداظهاری کافی (+10)");}
        Integer years=positiveYears(m.get("experienceYears"));
        if(years!=null){value+=Math.min(5,years);reasons.add("سابقه ثبت‌شده خوداظهاری (+"+Math.min(5,years)+")");}
        Map<String,Object> evidence=new LinkedHashMap<>();
        evidence.put("criteriaVersion",CRITERIA_VERSION);evidence.put("source","BUILDER_PROFILE_SELF_REPORTED");
        evidence.put("verifiedProfile",b.isVerified());evidence.put("available",available==null?"UNKNOWN":available);
        evidence.put("city",b.getCity());evidence.put("districts",districts);
        evidence.put("maxProjectAreaM2",area);evidence.put("capitalCapacity",capital);evidence.put("experienceYears",years);
        evidence.put("disclaimer","امتیاز غربال‌گری است و تأیید صلاحیت یا پیشنهاد الزام‌آور نیست.");
        return new MatchItem(b.getId(),b.getLegalName(),b.getTradeName(),value,List.copyOf(reasons),evidence);
    }
    private static boolean eq(String a,String b){return a!=null&&b!=null&&a.trim().equalsIgnoreCase(b.trim());}
    private static Boolean booleanValue(Object v){if(v instanceof Boolean b)return b; if(v instanceof String s){if(s.equalsIgnoreCase("true"))return true;if(s.equalsIgnoreCase("false"))return false;}return null;}
    private static BigDecimal nonnegative(Object v){if(v==null)return null;try{BigDecimal n=new BigDecimal(v.toString());return n.signum()>=0?n:null;}catch(NumberFormatException e){return null;}}
    private static Integer positiveYears(Object v){try{int n=Integer.parseInt(String.valueOf(v));return n>0&&n<=100?n:null;}catch(NumberFormatException e){return null;}}
    private static List<String> strings(Object v){
        if(v instanceof Collection<?> c)return c.stream().filter(Objects::nonNull).map(Object::toString).map(String::trim).filter(s->!s.isBlank()).limit(50).toList();
        if(v instanceof String s)return Arrays.stream(s.split(",")).map(String::trim).filter(x->!x.isBlank()).limit(50).toList();
        return List.of();
    }
}
