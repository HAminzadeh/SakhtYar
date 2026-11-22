package com.sakhtyar.globalization.application;

import static com.sakhtyar.globalization.domain.GlobalDtos.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CatalogService {
    private final JdbcTemplate jdbc;
    public CatalogService(JdbcTemplate jdbc){this.jdbc=jdbc;}

    public List<LanguageItem> languages(){
        return jdbc.query("""
          select id,code,bcp47_tag,name_en,name_native,direction
          from global_language where active=true order by system_default desc,name_en
          """,(rs,n)->new LanguageItem(uuid(rs,"id"),rs.getString("code"),rs.getString("bcp47_tag"),
                rs.getString("name_en"),rs.getString("name_native"),rs.getString("direction")));
    }

    public List<CurrencyItem> currencies(){
        return jdbc.query("""
          select id,code,name_en,symbol,minor_unit,iso4217
          from global_currency where active=true order by code
          """,(rs,n)->new CurrencyItem(uuid(rs,"id"),rs.getString("code"),rs.getString("name_en"),
                rs.getString("symbol"),(Integer)rs.getObject("minor_unit"),rs.getBoolean("iso4217")));
    }

    public List<CountryItem> countries(String q){
        String like=q==null||q.isBlank()?"%":"%"+q.trim().toLowerCase(Locale.ROOT)+"%";
        return jdbc.query("""
          select id,iso_alpha2,iso_alpha3,name_en,name_native,default_currency_id
          from global_country
          where active=true and (lower(name_en) like ? or lower(coalesce(name_native,'')) like ? or lower(iso_alpha2) like ?)
          order by name_en limit 500
          """,(rs,n)->new CountryItem(uuid(rs,"id"),rs.getString("iso_alpha2"),rs.getString("iso_alpha3"),
                rs.getString("name_en"),rs.getString("name_native"),uuidNullable(rs,"default_currency_id")),like,like,like);
    }

    public List<DivisionItem> divisions(UUID countryId,UUID parentId){
        if(parentId==null){
            return jdbc.query("""
              select id,country_id,parent_id,code,division_type,name_en,name_native,level
              from global_administrative_division
              where active=true and country_id=? and parent_id is null order by name_en
              """,(rs,n)->division(rs),countryId);
        }
        return jdbc.query("""
          select id,country_id,parent_id,code,division_type,name_en,name_native,level
          from global_administrative_division
          where active=true and country_id=? and parent_id=? order by name_en
          """,(rs,n)->division(rs),countryId,parentId);
    }

    public List<CityItem> cities(UUID countryId,UUID divisionId,String q){
        String like=q==null||q.isBlank()?"%":"%"+q.trim().toLowerCase(Locale.ROOT)+"%";
        if(divisionId==null){
            return jdbc.query("""
              select id,country_id,administrative_division_id,geonames_id,name,ascii_name,timezone,population
              from global_city
              where active=true and country_id=? and lower(name) like ?
              order by population desc nulls last,name limit 1000
              """,(rs,n)->city(rs),countryId,like);
        }
        return jdbc.query("""
          select id,country_id,administrative_division_id,geonames_id,name,ascii_name,timezone,population
          from global_city
          where active=true and country_id=? and administrative_division_id=? and lower(name) like ?
          order by population desc nulls last,name limit 1000
          """,(rs,n)->city(rs),countryId,divisionId,like);
    }

    private DivisionItem division(ResultSet rs)throws SQLException{
        return new DivisionItem(uuid(rs,"id"),uuid(rs,"country_id"),uuidNullable(rs,"parent_id"),
            rs.getString("code"),rs.getString("division_type"),rs.getString("name_en"),
            rs.getString("name_native"),rs.getInt("level"));
    }
    private CityItem city(ResultSet rs)throws SQLException{
        return new CityItem(uuid(rs,"id"),uuid(rs,"country_id"),uuidNullable(rs,"administrative_division_id"),
            rs.getLong("geonames_id"),rs.getString("name"),rs.getString("ascii_name"),
            rs.getString("timezone"),(Long)rs.getObject("population"));
    }
    private static UUID uuid(ResultSet rs,String c)throws SQLException{return rs.getObject(c,UUID.class);}
    private static UUID uuidNullable(ResultSet rs,String c)throws SQLException{return rs.getObject(c,UUID.class);}
}