package com.sakhtyar.globalization.application;

import static com.sakhtyar.globalization.domain.GlobalDtos.FxRequest;
import com.sakhtyar.audit.application.AuditService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Reviewed, timestamped, reproducible FX quotations. No live FX network calls. */
@Service
public class FxService {
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    public FxService(JdbcTemplate jdbc, AuditService audit) { this.jdbc=jdbc; this.audit=audit; }

    public record Quote(UUID rateId, UUID baseCurrencyId, UUID quoteCurrencyId,
                        BigDecimal amount, BigDecimal rate, BigDecimal converted,
                        String sourceLabel, Instant observedAt, boolean inverse, boolean stale) { }
    private record Rate(UUID id, BigDecimal rate, String source, Instant observedAt) { }

    @Transactional
    public UUID add(FxRequest r) {
        GlobalizationAccess.requireAdmin();
        if (r == null || r.baseCurrencyId() == null || r.quoteCurrencyId() == null ||
                r.baseCurrencyId().equals(r.quoteCurrencyId()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Distinct currency IDs are required.");
        BigDecimal rate=GlobalizationPolicy.positiveRate(r.rate());
        String label=GlobalizationPolicy.sourceLabel(r.sourceLabel());
        String url=GlobalizationPolicy.sourceUrl(r.sourceUrl());
        BigDecimal confidence=GlobalizationPolicy.confidence(r.confidence());
        Instant observed=GlobalizationPolicy.observedAt(r.observedAt(), Instant.now());
        if (!currencyActive(r.baseCurrencyId()) || !currencyActive(r.quoteCurrencyId()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Inactive or unknown currency.");
        UUID id=UUID.randomUUID();
        int inserted;
        try { inserted=jdbc.update("""
          insert into global_fx_rate(id,base_currency_id,quote_currency_id,rate,source_label,source_url,
                                     observed_at,confidence,review_status,created_at)
          values(?,?,?,?,?,?,?,?,'PENDING',?)
          """,id,r.baseCurrencyId(),r.quoteCurrencyId(),rate,label,url,observed,confidence,Instant.now());
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,"FX rate already exists for that observation.");
        }
        if (inserted != 1) throw new IllegalStateException("FX rate was not persisted.");
        audit.record("FX_RATE",id,"FX_RATE_SUBMITTED",Map.of("base",r.baseCurrencyId().toString(),
                "quote",r.quoteCurrencyId().toString(),"status","PENDING"));
        return id;
    }

    @Transactional
    public void review(UUID id, boolean approve) {
        GlobalizationAccess.requireAdmin();
        if (id == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Rate ID required.");
        int updated=jdbc.update("""
          update global_fx_rate set review_status=?
          where id=? and review_status='PENDING'
          """,approve?"APPROVED":"REJECTED",id);
        if (updated != 1) throw new ResponseStatusException(HttpStatus.CONFLICT,"FX rate not pending or not found.");
        audit.record("FX_RATE",id,approve?"FX_RATE_APPROVED":"FX_RATE_REJECTED",
                Map.of("status",approve?"APPROVED":"REJECTED"));
    }

    @Transactional(readOnly=true)
    public BigDecimal convert(UUID base, UUID quote, BigDecimal amount, Instant at) {
        return quote(base,quote,amount,at).converted();
    }

    @Transactional(readOnly=true)
    public Quote quote(UUID base, UUID quote, BigDecimal amount, Instant at) {
        if (base == null || quote == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Currency IDs required.");
        GlobalizationPolicy.amount(amount);
        if (!currencyActive(base) || !currencyActive(quote))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Inactive or unknown currency.");
        Instant effective=at == null ? Instant.now() : at;
        if (effective.isAfter(Instant.now().plusSeconds(300)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Future FX quotation is not supported.");
        int scale=quoteScale(quote);
        if (base.equals(quote)) return new Quote(null,base,quote,amount,BigDecimal.ONE,
                amount.setScale(scale,RoundingMode.HALF_UP),"SAME_CURRENCY",effective,false,false);
        List<Rate> direct=findApproved(base,quote,effective);
        boolean inverse=direct.isEmpty();
        List<Rate> chosen=inverse?findApproved(quote,base,effective):direct;
        if (chosen.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"No approved FX rate for currency pair.");
        Rate r=chosen.getFirst();
        BigDecimal converted=inverse?amount.divide(r.rate(),scale,RoundingMode.HALF_UP)
                :amount.multiply(r.rate()).setScale(scale,RoundingMode.HALF_UP);
        BigDecimal actualRate=inverse?BigDecimal.ONE.divide(r.rate(),12,RoundingMode.HALF_UP):r.rate();
        boolean stale=r.observedAt().isBefore(effective.minusSeconds(30L*24*60*60));
        return new Quote(r.id(),base,quote,amount,actualRate,converted,r.source(),r.observedAt(),inverse,stale);
    }

    private List<Rate> findApproved(UUID base,UUID quote,Instant effective) {
        return jdbc.query("""
          select id,rate,source_label,observed_at from global_fx_rate
          where base_currency_id=? and quote_currency_id=? and review_status='APPROVED'
            and observed_at<=? and (valid_from is null or valid_from<=?)
            and (valid_to is null or valid_to>?)
          order by observed_at desc,id desc limit 1
          """,(rs,n)->new Rate(rs.getObject("id",UUID.class),rs.getBigDecimal("rate"),
                rs.getString("source_label"),rs.getTimestamp("observed_at").toInstant()),
                base,quote,effective,effective,effective);
    }
    private boolean currencyActive(UUID id) {
        Boolean exists=jdbc.queryForObject("select exists(select 1 from global_currency where id=? and active=true)",Boolean.class,id);
        return Boolean.TRUE.equals(exists);
    }
    private int quoteScale(UUID currency) {
        Integer minor=jdbc.queryForObject("select minor_unit from global_currency where id=?",Integer.class,currency);
        return minor == null ? 8 : Math.min(9,Math.max(0,minor));
    }
}
