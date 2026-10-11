package com.sakhtyar.notification.application;

import static com.sakhtyar.notification.api.NotificationDtos.*;
import com.sakhtyar.audit.application.AuditService;
import tools.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final ObjectMapper mapper;
    public NotificationService(JdbcTemplate jdbc,AuditService audit,ObjectMapper mapper){this.jdbc=jdbc;this.audit=audit;this.mapper=mapper;}

    @Transactional
    public NotificationResponse enqueue(EnqueueRequest request) {
        UUID id=UUID.randomUUID(); Instant now=Instant.now();
        jdbc.update("""
          insert into notification_outbox(id,channel,target,subject,body,status,attempt_count,metadata,created_at)
          values (?,?,?,?,?,'PENDING',0,?::jsonb,?)
          """,id,request.channel().trim().toUpperCase(Locale.ROOT),request.target().trim(),
          request.subject().trim(),request.body().trim(),json(request.metadata()),java.sql.Timestamp.from(now));
        audit.record("NOTIFICATION",id,"NOTIFICATION_ENQUEUED",Map.of("channel",request.channel(),"target",request.target()));
        return get(id);
    }

    @Transactional(readOnly=true)
    public List<NotificationResponse> pending(int limit) {
        int safe=Math.max(1,Math.min(limit,500));
        return jdbc.query("""
          select id,channel,target,subject,body,status,attempt_count,last_error,metadata,created_at,sent_at
          from notification_outbox where status in ('PENDING','FAILED')
          order by created_at asc limit """+safe,(rs,row)->map(rs));
    }

    @Transactional(readOnly=true)
    public NotificationResponse get(UUID id) {
        return jdbc.query("""
          select id,channel,target,subject,body,status,attempt_count,last_error,metadata,created_at,sent_at
          from notification_outbox where id=?
          """,(rs,row)->map(rs),id).stream().findFirst()
          .orElseThrow(()->new IllegalArgumentException("Notification not found."));
    }

    @Transactional
    public NotificationResponse markSent(UUID id) {
        int changed=jdbc.update("""
          update notification_outbox set status='SENT',attempt_count=attempt_count+1,sent_at=now(),last_error=null where id=?
          """,id);
        if(changed==0)throw new IllegalArgumentException("Notification not found.");
        return get(id);
    }

    @Transactional
    public NotificationResponse markFailed(UUID id,String error) {
        int changed=jdbc.update("""
          update notification_outbox set status='FAILED',attempt_count=attempt_count+1,last_error=? where id=?
          """,error==null?"Unknown delivery failure":error,id);
        if(changed==0)throw new IllegalArgumentException("Notification not found.");
        return get(id);
    }

    private NotificationResponse map(ResultSet rs)throws SQLException{
        return new NotificationResponse(
          rs.getObject("id",UUID.class),rs.getString("channel"),rs.getString("target"),rs.getString("subject"),
          rs.getString("body"),rs.getString("status"),rs.getInt("attempt_count"),rs.getString("last_error"),
          parse(rs.getString("metadata")),instant(rs,"created_at"),instant(rs,"sent_at")
        );
    }
    private Instant instant(ResultSet rs,String c)throws SQLException{var t=rs.getTimestamp(c);return t==null?null:t.toInstant();}
    private String json(Map<String,Object> m){try{return mapper.writeValueAsString(m==null?Map.of():m);}catch(Exception e){throw new IllegalArgumentException("metadata must be JSON serializable.",e);}}
    @SuppressWarnings("unchecked")
    private Map<String,Object> parse(String raw){try{return raw==null?Map.of():mapper.readValue(raw,Map.class);}catch(Exception e){return Map.of();}}
}
