package com.sakhtyar.globalization.application;

import static com.sakhtyar.globalization.domain.GlobalDtos.*;
import com.sakhtyar.identity.domain.UserRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserPreferenceService {
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    public UserPreferenceService(JdbcTemplate jdbc,UserRepository users){this.jdbc=jdbc;this.users=users;}

    @Transactional
    public Preference get(Authentication a){
        UUID userId=userId(a);
        ensure(userId);
        return jdbc.queryForObject("""
          select p.user_id,p.language_id,p.country_id,p.currency_id,p.timezone,p.theme,p.date_format,p.number_format,p.first_day_of_week,
                 l.code language_code,c.iso_alpha2 country_code,cu.code currency_code
          from user_preference p
          left join global_language l on l.id=p.language_id
          left join global_country c on c.id=p.country_id
          left join global_currency cu on cu.id=p.currency_id
          where p.user_id=?
          """,(rs,n)->new Preference(
              rs.getObject("user_id",UUID.class),rs.getObject("language_id",UUID.class),
              rs.getObject("country_id",UUID.class),rs.getObject("currency_id",UUID.class),
              rs.getString("language_code"),rs.getString("country_code"),rs.getString("currency_code"),
              rs.getString("timezone"),rs.getString("theme"),rs.getString("date_format"),
              rs.getString("number_format"),(Integer)rs.getObject("first_day_of_week")
          ),userId);
    }

    @Transactional
    public Preference update(Authentication a,PreferenceUpdate r){
        UUID userId=userId(a);
        validateRef("global_language",r.languageId());
        validateRef("global_country",r.countryId());
        validateRef("global_currency",r.currencyId());
        String theme=r.theme()==null?"SYSTEM":r.theme().trim().toUpperCase();
        if(!theme.matches("SYSTEM|LIGHT|DARK|OCEAN|EMERALD|SUNSET|MIDNIGHT")) throw new IllegalArgumentException("Invalid theme.");

        String timezone=clean(r.timezone());
        if(timezone!=null) {
            try { ZoneId.of(timezone); }
            catch(Exception ex) { throw new IllegalArgumentException("Invalid timezone: "+timezone); }
        }

        int updated=jdbc.update("""
          insert into user_preference(user_id,language_id,country_id,currency_id,timezone,theme,date_format,number_format,first_day_of_week,updated_at)
          values(?,?,?,?,?,?,?,?,?,?)
          on conflict(user_id) do update set
            language_id=excluded.language_id,country_id=excluded.country_id,currency_id=excluded.currency_id,
            timezone=excluded.timezone,theme=excluded.theme,date_format=excluded.date_format,
            number_format=excluded.number_format,first_day_of_week=excluded.first_day_of_week,updated_at=excluded.updated_at
          """,userId,r.languageId(),r.countryId(),r.currencyId(),timezone,theme,
          clean(r.dateFormat()),clean(r.numberFormat()),r.firstDayOfWeek(),Instant.now());

        if(updated!=1) throw new IllegalStateException("User preference update did not persist.");
        Preference persisted=get(a);

        if(r.languageId()!=null && !r.languageId().equals(persisted.languageId()))
            throw new IllegalStateException("Language preference round-trip verification failed.");
        if(r.currencyId()!=null && !r.currencyId().equals(persisted.currencyId()))
            throw new IllegalStateException("Currency preference round-trip verification failed.");
        if(!theme.equalsIgnoreCase(persisted.theme()))
            throw new IllegalStateException("Theme preference round-trip verification failed.");
        if(timezone!=null && !timezone.equals(persisted.timezone()))
            throw new IllegalStateException("Timezone preference round-trip verification failed.");

        return persisted;
    }

    private void ensure(UUID userId){
        jdbc.update("""
          insert into user_preference(user_id,language_id,currency_id,timezone,theme,updated_at)
          select ?,l.id,cu.id,'Asia/Tehran','SYSTEM',now()
          from global_language l cross join global_currency cu
          where l.code='fa' and cu.code='TOMAN'
          on conflict(user_id) do nothing
          """,userId);
    }
    private UUID userId(Authentication a){
        if(a==null||a.getName()==null) throw new IllegalStateException("Authenticated user required.");
        return users.findByUsernameIgnoreCase(a.getName())
            .orElseThrow(()->new IllegalArgumentException("User not found.")).getId();
    }
    private void validateRef(String table,UUID id){
        if(id==null)return;
        Integer n=jdbc.queryForObject("select count(*) from "+table+" where id=?",Integer.class,id);
        if(n==null||n==0) throw new IllegalArgumentException("Invalid reference: "+table);
    }
    private static String clean(String s){return s==null||s.isBlank()?null:s.trim();}
}