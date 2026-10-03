package com.sakhtyar.globalization.application;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class CurrencyCatalogBootstrap implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    public CurrencyCatalogBootstrap(JdbcTemplate jdbc){this.jdbc=jdbc;}

    @Override public void run(ApplicationArguments args){
        for(Currency c:Currency.getAvailableCurrencies()){
            UUID id=UUID.nameUUIDFromBytes(("currency:"+c.getCurrencyCode()).getBytes(StandardCharsets.UTF_8));
            Integer minor=c.getDefaultFractionDigits()<0?null:c.getDefaultFractionDigits();
            jdbc.update("""
              insert into global_currency(id,code,numeric_code,name_en,symbol,minor_unit,iso4217,active)
              values(?,?,?,?,?,?,true,true)
              on conflict(code) do update set
                numeric_code=excluded.numeric_code,name_en=excluded.name_en,
                symbol=excluded.symbol,minor_unit=excluded.minor_unit,active=true
              """,id,c.getCurrencyCode(),String.format("%03d",c.getNumericCode()),
              c.getDisplayName(Locale.ENGLISH),c.getSymbol(Locale.ENGLISH),minor);
        }
    }
}