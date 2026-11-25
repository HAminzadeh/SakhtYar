package com.sakhtyar.globalization.application;

import static com.sakhtyar.globalization.domain.GlobalDtos.*;
import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.identity.domain.UserRepository;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** User-owned settings: PATCH-like semantics for non-null submitted fields. */
@Service
public class UserPreferenceService {
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final AuditService audit;
    public UserPreferenceService(JdbcTemplate jdbc, UserRepository users, AuditService audit) {
        this.jdbc=jdbc; this.users=users; this.audit=audit;
    }

    @Transactional
    public Preference get(Authentication a) {
        UUID userId=userId(a);
        ensure(userId);
        return read(userId);
    }
    private Preference read(UUID id) {
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
              rs.getString("number_format"),(Integer)rs.getObject("first_day_of_week")),id);
    }

    @Transactional
    public Preference update(Authentication a, PreferenceUpdate r) {
        if (r == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Preference body required.");
        UUID userId=userId(a);
        ensure(userId);
        Preference old=read(userId);
        UUID language=r.languageId() == null ? old.languageId() : r.languageId();
        UUID country=r.countryId() == null ? old.countryId() : r.countryId();
        UUID currency=r.currencyId() == null ? old.currencyId() : r.currencyId();
        validateRef("global_language",language);
        validateRef("global_country",country);
        validateRef("global_currency",currency);
        String theme=r.theme() == null ? old.theme() : r.theme().trim().toUpperCase(java.util.Locale.ROOT);
        if (theme == null || !theme.matches("SYSTEM|LIGHT|DARK|OCEAN|EMERALD|SUNSET|MIDNIGHT"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid theme.");
        String timezone=GlobalizationPolicy.timezone(r.timezone() == null ? old.timezone() : r.timezone());
        String dateFormat=GlobalizationPolicy.dateFormat(r.dateFormat() == null ? old.dateFormat() : r.dateFormat());
        String numberFormat=GlobalizationPolicy.numberFormat(r.numberFormat() == null ? old.numberFormat() : r.numberFormat());
        Integer firstDay=GlobalizationPolicy.firstDay(r.firstDayOfWeek() == null ? old.firstDayOfWeek() : r.firstDayOfWeek());
        int updated=jdbc.update("""
          insert into user_preference(user_id,language_id,country_id,currency_id,timezone,theme,
            date_format,number_format,first_day_of_week,updated_at)
          values(?,?,?,?,?,?,?,?,?,now())
          on conflict(user_id) do update set language_id=excluded.language_id,
            country_id=excluded.country_id,currency_id=excluded.currency_id,timezone=excluded.timezone,
            theme=excluded.theme,date_format=excluded.date_format,
            number_format=excluded.number_format,first_day_of_week=excluded.first_day_of_week,updated_at=now()
          """,userId,language,country,currency,timezone,theme,dateFormat,numberFormat,firstDay);
        if (updated != 1) throw new IllegalStateException("Preference update did not persist.");
        Preference persisted=read(userId);
        if (!java.util.Objects.equals(language,persisted.languageId()) ||
            !java.util.Objects.equals(currency,persisted.currencyId()) ||
            !java.util.Objects.equals(dateFormat,persisted.dateFormat()) ||
            !theme.equalsIgnoreCase(persisted.theme()))
            throw new IllegalStateException("Preference round-trip verification failed.");
        audit.record("USER_PREFERENCE",userId,"USER_PREFERENCE_UPDATED",Map.of("changed",true));
        return persisted;
    }

    private void ensure(UUID userId) {
        jdbc.update("""
          insert into user_preference(user_id,language_id,currency_id,timezone,theme,updated_at)
          select ?,l.id,cu.id,'Asia/Tehran','SYSTEM',now()
          from global_language l cross join global_currency cu
          where l.code='fa' and cu.code='TOMAN'
          on conflict(user_id) do nothing
          """,userId);
    }
    private UUID userId(Authentication a) {
        if (a == null || !a.isAuthenticated() || a.getName() == null ||
                a.getAuthorities() == null || a.getAuthorities().stream()
                .anyMatch(x -> "ROLE_TESTER".equals(x.getAuthority())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Authenticated user required.");
        return users.findByUsernameIgnoreCase(a.getName())
                .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"User not found." )).getId();
    }
    private void validateRef(String table, UUID id) {
        if (id == null) return;
        // Identifiers below are internal, fixed allowlisted constants (never request-derived SQL).
        if (!java.util.Set.of("global_language","global_country","global_currency").contains(table))
            throw new IllegalArgumentException("Unexpected reference table.");
        Integer count=jdbc.queryForObject("select count(*) from "+table+" where id=? and active=true",Integer.class,id);
        if (count == null || count == 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Inactive or unknown preference reference.");
    }
}
