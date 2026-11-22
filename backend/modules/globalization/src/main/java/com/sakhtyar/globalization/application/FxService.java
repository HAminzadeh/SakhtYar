package com.sakhtyar.globalization.application;

import static com.sakhtyar.globalization.domain.GlobalDtos.FxRequest;
import java.math.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FxService {
    private final JdbcTemplate jdbc;
    public FxService(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @Transactional
    public UUID add(FxRequest r){
        if(r.baseCurrencyId().equals(r.quoteCurrencyId())) throw new IllegalArgumentException("Currency pair must differ.");
        UUID id=UUID.randomUUID();
        jdbc.update("""
          insert into global_fx_rate(id,base_currency_id,quote_currency_id,rate,source_label,source_url,
                                     observed_at,confidence,review_status,created_at)
          values(?,?,?,?,?,?,?,?, 'APPROVED',?)
          """,id,r.baseCurrencyId(),r.quoteCurrencyId(),r.rate(),r.sourceLabel(),r.sourceUrl(),
          r.observedAt()==null?Instant.now():r.observedAt(),r.confidence(),Instant.now());
        return id;
    }

    @Transactional(readOnly=true)
    public BigDecimal convert(UUID base,UUID quote,BigDecimal amount,Instant at){
        if(base.equals(quote))return amount;
        List<BigDecimal> rates=jdbc.query("""
          select rate from global_fx_rate
          where base_currency_id=? and quote_currency_id=? and observed_at<=?
          order by observed_at desc limit 1
          """,(rs,n)->rs.getBigDecimal(1),base,quote,at==null?Instant.now():at);
        if(!rates.isEmpty())return amount.multiply(rates.getFirst()).setScale(8,RoundingMode.HALF_UP);

        List<BigDecimal> inverse=jdbc.query("""
          select rate from global_fx_rate
          where base_currency_id=? and quote_currency_id=? and observed_at<=?
          order by observed_at desc limit 1
          """,(rs,n)->rs.getBigDecimal(1),quote,base,at==null?Instant.now():at);
        if(inverse.isEmpty())throw new IllegalArgumentException("No FX rate found for currency pair.");
        return amount.divide(inverse.getFirst(),8,RoundingMode.HALF_UP);
    }
}