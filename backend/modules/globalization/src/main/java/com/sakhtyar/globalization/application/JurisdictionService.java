package com.sakhtyar.globalization.application;

import static com.sakhtyar.globalization.domain.GlobalDtos.JurisdictionContext;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JurisdictionService {
    private final JdbcTemplate jdbc;
    public JurisdictionService(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @Transactional(readOnly=true)
    public JurisdictionContext forCase(UUID caseId){
        List<JurisdictionContext> rows=jdbc.query("""
          select p.id property_id,p.case_id,p.country_id,p.administrative_division_id,p.city_id,
                 c.iso_alpha2 country_code,a.code division_code,ci.name city_name
          from property p
          left join global_country c on c.id=p.country_id
          left join global_administrative_division a on a.id=p.administrative_division_id
          left join global_city ci on ci.id=p.city_id
          where p.case_id=?
          """,(rs,n)->new JurisdictionContext(
            rs.getObject("case_id",UUID.class),rs.getObject("property_id",UUID.class),
            rs.getObject("country_id",UUID.class),rs.getObject("administrative_division_id",UUID.class),
            rs.getObject("city_id",UUID.class),rs.getString("country_code"),
            rs.getString("division_code"),rs.getString("city_name")
          ),caseId);
        if(rows.isEmpty()) throw new IllegalArgumentException("Property/location not found for case.");
        return rows.getFirst();
    }

    @Transactional
    public JurisdictionContext setCaseLocation(UUID caseId,UUID countryId,UUID divisionId,UUID cityId){
        validateHierarchy(countryId,divisionId,cityId);
        String province=divisionId==null?null:jdbc.queryForObject(
            "select coalesce(name_native,name_en) from global_administrative_division where id=?",String.class,divisionId);
        String city=cityId==null?null:jdbc.queryForObject(
            "select name from global_city where id=?",String.class,cityId);
        int n=jdbc.update("""
          update property set country_id=?,administrative_division_id=?,city_id=?,province=coalesce(?,province),
                              city=coalesce(?,city),updated_at=now()
          where case_id=?
          """,countryId,divisionId,cityId,province,city,caseId);
        if(n==0) throw new IllegalArgumentException("Property not found for case.");
        jdbc.update("update construction_case set city=coalesce(?,city),updated_at=now() where id=?",city,caseId);
        return forCase(caseId);
    }

    @Transactional(readOnly=true)
    public List<UUID> matchingCrawlerSources(UUID caseId){
        JurisdictionContext x=forCase(caseId);
        return jdbc.query("""
          select id from crawl_source
          where enabled=true
            and (country_id is null or country_id=?)
            and (administrative_division_id is null or administrative_division_id=?)
            and (city_id is null or city_id=?)
          order by
            case when city_id is not null then 1 when administrative_division_id is not null then 2
                 when country_id is not null then 3 else 4 end,
            updated_at desc
          """,(rs,n)->rs.getObject(1,UUID.class),x.countryId(),x.divisionId(),x.cityId());
    }

    @Transactional(readOnly=true)
    public Set<UUID> matchingUrbanRules(UUID caseId){
        JurisdictionContext x=forCase(caseId);
        return new LinkedHashSet<>(jdbc.query("""
          select id from urban_rule
          where active=true
            and (country_id is null or country_id=?)
            and (administrative_division_id is null or administrative_division_id=?)
            and (city_id is null or city_id=?)
          order by
            case when city_id is not null then 1 when administrative_division_id is not null then 2
                 when country_id is not null then 3 else 4 end,
            priority,code
          """,(rs,n)->rs.getObject(1,UUID.class),x.countryId(),x.divisionId(),x.cityId()));
    }

    @Transactional
    public void stampLearning(UUID eventId,UUID candidateId,UUID caseId,String languageCode){
        if(caseId==null)return;
        JurisdictionContext x=forCase(caseId);
        UUID lang=languageCode==null?null:jdbc.query("""
          select id from global_language where code=? limit 1
          """,(rs,n)->rs.getObject(1,UUID.class),languageCode).stream().findFirst().orElse(null);
        String scope=x.cityId()!=null?"CITY":x.divisionId()!=null?"ADMIN_DIVISION":x.countryId()!=null?"COUNTRY":"GLOBAL";
        jdbc.update("""
          update learning_event set country_id=?,administrative_division_id=?,city_id=?,language_id=?,jurisdiction_scope=?
          where id=?
          """,x.countryId(),x.divisionId(),x.cityId(),lang,scope,eventId);
        if(candidateId!=null){
            jdbc.update("""
              update knowledge_candidate set country_id=?,administrative_division_id=?,city_id=?,language_id=?,jurisdiction_scope=?
              where id=?
              """,x.countryId(),x.divisionId(),x.cityId(),lang,scope,candidateId);
        }
    }

    @Transactional
    public void stampCandidateFromCrawler(UUID candidateId,UUID crawlSourceId){
        jdbc.update("""
          update knowledge_candidate k
          set country_id=s.country_id,administrative_division_id=s.administrative_division_id,
              city_id=s.city_id,language_id=s.language_id,jurisdiction_scope=s.jurisdiction_scope
          from crawl_source s where s.id=? and k.id=?
          """,crawlSourceId,candidateId);
    }

    private void validateHierarchy(UUID countryId,UUID divisionId,UUID cityId){
        if(countryId==null) throw new IllegalArgumentException("countryId is required.");
        if(divisionId!=null){
            Integer n=jdbc.queryForObject(
                "select count(*) from global_administrative_division where id=? and country_id=?",
                Integer.class,divisionId,countryId);
            if(n==null||n==0) throw new IllegalArgumentException("Administrative division does not belong to country.");
        }
        if(cityId!=null){
            Integer n=jdbc.queryForObject("""
              select count(*) from global_city
              where id=? and country_id=? and (?::uuid is null or administrative_division_id=?)
              """,Integer.class,cityId,countryId,divisionId,divisionId);
            if(n==null||n==0) throw new IllegalArgumentException("City does not belong to selected jurisdiction.");
        }
    }
}