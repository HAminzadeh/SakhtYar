package com.sakhtyar.monitoring.application;

import static com.sakhtyar.monitoring.api.MonitoringDtos.*;
import static com.sakhtyar.notification.api.NotificationDtos.EnqueueRequest;

import com.sakhtyar.audit.application.AuditService;
import com.sakhtyar.market.api.MarketDtos.MarketSummary;
import com.sakhtyar.market.application.MarketIntelligenceService;
import com.sakhtyar.notification.application.NotificationService;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonitoringService {
    private final JdbcTemplate jdbc;
    private final MarketIntelligenceService market;
    private final NotificationService notifications;
    private final AuditService audit;

    public MonitoringService(JdbcTemplate jdbc,MarketIntelligenceService market,NotificationService notifications,AuditService audit){
        this.jdbc=jdbc;this.market=market;this.notifications=notifications;this.audit=audit;
    }

    @Transactional
    public RuleResponse create(UpsertRuleRequest r){
        Integer exists=jdbc.queryForObject("select count(*) from monitoring_rule where upper(code)=upper(?)",Integer.class,r.code());
        if(exists!=null&&exists>0)throw new IllegalArgumentException("Monitoring rule code already exists.");
        UUID id=UUID.randomUUID();Instant now=Instant.now();
        jdbc.update("""
          insert into monitoring_rule(id,code,metric_code,operator,threshold,city,district,period_days,
             notification_channel,notification_target,cooldown_minutes,active,created_at,updated_at)
          values (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
          """,id,r.code().trim().toUpperCase(Locale.ROOT),r.metricCode(),r.operator(),r.threshold(),r.city().trim(),
          clean(r.district()),r.periodDays(),r.notificationChannel().trim().toUpperCase(Locale.ROOT),
          r.notificationTarget().trim(),r.cooldownMinutes(),r.active(),java.sql.Timestamp.from(now),java.sql.Timestamp.from(now));
        audit.record("MONITORING_RULE",id,"MONITORING_RULE_CREATED",Map.of("code",r.code(),"metric",r.metricCode()));
        return get(id);
    }

    @Transactional(readOnly=true)
    public List<RuleResponse> list(){return jdbc.query("select * from monitoring_rule order by updated_at desc",(rs,row)->rule(rs));}

    @Transactional(readOnly=true)
    public RuleResponse get(UUID id){return jdbc.query("select * from monitoring_rule where id=?",(rs,row)->rule(rs),id).stream()
      .findFirst().orElseThrow(()->new IllegalArgumentException("Monitoring rule not found."));}

    @Transactional
    public List<EventResponse> evaluateAll(){
        List<EventResponse> fired=new ArrayList<>();
        for(RuleResponse r:list()){
            if(!r.active())continue;
            try{evaluate(r).ifPresent(fired::add);}catch(RuntimeException ignored){}
        }
        return fired;
    }

    @Scheduled(fixedDelayString="${app.monitoring.market-evaluation-ms:900000}")
    public void scheduledEvaluate(){evaluateAll();}

    @Transactional
    public Optional<EventResponse> evaluate(RuleResponse r){
        MarketSummary s=market.summary(r.city(),r.district(),null,null,r.periodDays());
        BigDecimal value=switch(r.metricCode()){
            case "MARKET_MEDIAN_PRICE" -> s.medianPricePerM2();
            case "MARKET_PRICE_CHANGE" -> s.changePercent();
            case "MARKET_SAMPLE_COUNT" -> BigDecimal.valueOf(s.sampleCount());
            default -> null;
        };
        if(value==null || s.sampleCount()<1)return Optional.empty();
        if(!compare(value,r.operator(),r.threshold()))return Optional.empty();
        if(inCooldown(r))return Optional.empty();

        UUID id=UUID.randomUUID();Instant now=Instant.now();
        String message=r.code()+": "+r.metricCode()+" = "+value+" "+r.operator()+" "+r.threshold();
        jdbc.update("""
          insert into monitoring_event(id,rule_id,status,observed_value,threshold,message,observed_at)
          values (?,?,'TRIGGERED',?,?,?,?)
          """,id,r.id(),value,r.threshold(),message,java.sql.Timestamp.from(now));

        notifications.enqueue(new EnqueueRequest(
          r.notificationChannel(),r.notificationTarget(),"هشدار ساخت‌یار: "+r.code(),
          message+" | "+r.city()+(r.district()==null?"":" / "+r.district()),Map.of("monitoringRuleId",r.id().toString())
        ));
        audit.record("MONITORING_EVENT",id,"MONITORING_RULE_TRIGGERED",Map.of("ruleId",r.id().toString(),"value",value.toPlainString()));
        return Optional.of(new EventResponse(id,r.id(),"TRIGGERED",value,r.threshold(),message,now));
    }

    @Transactional(readOnly=true)
    public List<EventResponse> events(UUID ruleId,int limit){
        int safe=Math.max(1,Math.min(limit,500));
        return jdbc.query("select * from monitoring_event where rule_id=? order by observed_at desc limit "+safe,
          (rs,row)->event(rs),ruleId);
    }

    private boolean inCooldown(RuleResponse r){
        Instant cutoff=Instant.now().minus(Duration.ofMinutes(r.cooldownMinutes()));
        Integer count=jdbc.queryForObject("select count(*) from monitoring_event where rule_id=? and observed_at>=?",
          Integer.class,r.id(),java.sql.Timestamp.from(cutoff));
        return count!=null&&count>0;
    }
    private boolean compare(BigDecimal value,String op,BigDecimal threshold){
        int c=value.compareTo(threshold);
        return switch(op){case "GT"->c>0;case "GTE"->c>=0;case "LT"->c<0;case "LTE"->c<=0;case "EQ"->c==0;default->false;};
    }
    private RuleResponse rule(ResultSet rs)throws SQLException{
        return new RuleResponse(rs.getObject("id",UUID.class),rs.getString("code"),rs.getString("metric_code"),
          rs.getString("operator"),rs.getBigDecimal("threshold"),rs.getString("city"),rs.getString("district"),
          rs.getInt("period_days"),rs.getString("notification_channel"),rs.getString("notification_target"),
          rs.getInt("cooldown_minutes"),rs.getBoolean("active"),instant(rs,"created_at"),instant(rs,"updated_at"));
    }
    private EventResponse event(ResultSet rs)throws SQLException{
        return new EventResponse(rs.getObject("id",UUID.class),rs.getObject("rule_id",UUID.class),rs.getString("status"),
          rs.getBigDecimal("observed_value"),rs.getBigDecimal("threshold"),rs.getString("message"),instant(rs,"observed_at"));
    }
    private Instant instant(ResultSet rs,String c)throws SQLException{var t=rs.getTimestamp(c);return t==null?null:t.toInstant();}
    private String clean(String s){if(s==null)return null;var v=s.trim();return v.isEmpty()?null:v;}
}
