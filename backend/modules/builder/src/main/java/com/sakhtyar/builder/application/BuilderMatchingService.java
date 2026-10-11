package com.sakhtyar.builder.application;

import static com.sakhtyar.builder.api.BuilderDtos.*;
import com.sakhtyar.builder.domain.*;
import com.sakhtyar.property.domain.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BuilderMatchingService {
  private final BuilderProfileRepository builders;
  private final PropertyRepository properties;

  public BuilderMatchingService(BuilderProfileRepository builders,PropertyRepository properties){
    this.builders=builders;this.properties=properties;
  }

  @Transactional(readOnly=true)
  public MatchResponse match(UUID caseId,BigDecimal requiredCapital,int limit){
    PropertyEntity p=properties.findByCaseId(caseId)
      .orElseThrow(()->new IllegalArgumentException("Property for case not found."));
    int safe=Math.max(1,Math.min(limit,100));
    List<MatchItem> items=new ArrayList<>();
    for(var b:builders.findByVerifiedTrueOrderByUpdatedAtDesc())items.add(score(b,p,requiredCapital));
    items.sort(Comparator.comparingInt(MatchItem::score).reversed());
    return new MatchResponse(caseId,p.getDistrict(),p.getLandAreaM2(),requiredCapital,items.size(),
      items.stream().limit(safe).toList());
  }

  private MatchItem score(BuilderProfileEntity b,PropertyEntity p,BigDecimal requiredCapital){
    int score=10; List<String> reasons=new ArrayList<>(); reasons.add("پروفایل سازنده تأیید شده است");
    Map<String,Object> m=b.getMetadata();

    if(bool(m.get("available"),true)){score+=20;reasons.add("ظرفیت پذیرش پروژه");}
    if(eq(b.getCity(),p.getCity())){score+=10;reasons.add("فعالیت در شهر ملک");}

    List<String> districts=strings(m.get("districts"));
    if(p.getDistrict()!=null&&districts.stream().anyMatch(x->x.equalsIgnoreCase(p.getDistrict()))){
      score+=25;reasons.add("سابقه/تمرکز در منطقه هدف");
    }

    BigDecimal maxArea=decimal(m.get("maxProjectAreaM2"));
    if(p.getLandAreaM2()!=null&&maxArea!=null&&maxArea.compareTo(p.getLandAreaM2())>=0){
      score+=20;reasons.add("ظرفیت متراژی مناسب");
    }

    BigDecimal capital=decimal(m.get("capitalCapacity"));
    if(requiredCapital!=null&&capital!=null&&capital.compareTo(requiredCapital)>=0){
      score+=10;reasons.add("ظرفیت سرمایه اعلام‌شده کافی");
    }

    Integer exp=integer(m.get("experienceYears"));
    if(exp!=null&&exp>0){score+=Math.min(5,exp);reasons.add("سابقه اجرایی ثبت‌شده");}

    Map<String,Object> evidence=new LinkedHashMap<>();
    evidence.put("verified",b.isVerified());evidence.put("city",b.getCity());evidence.put("districts",districts);
    if(maxArea!=null)evidence.put("maxProjectAreaM2",maxArea);
    if(capital!=null)evidence.put("capitalCapacity",capital);
    if(exp!=null)evidence.put("experienceYears",exp);

    return new MatchItem(b.getId(),b.getLegalName(),b.getTradeName(),Math.min(100,score),reasons,evidence);
  }

  private boolean eq(String a,String b){return a!=null&&b!=null&&a.equalsIgnoreCase(b);}
  private boolean bool(Object v,boolean d){return v==null?d:Boolean.parseBoolean(v.toString());}
  private BigDecimal decimal(Object v){if(v==null)return null;try{return new BigDecimal(v.toString());}catch(Exception e){return null;}}
  private Integer integer(Object v){if(v==null)return null;try{return Integer.valueOf(v.toString());}catch(Exception e){return null;}}
  private List<String> strings(Object v){
    if(v instanceof Collection<?> c)return c.stream().map(String::valueOf).filter(x->!x.isBlank()).toList();
    if(v==null)return List.of();
    return Arrays.stream(v.toString().split(",")).map(String::trim).filter(x->!x.isBlank()).toList();
  }
}
