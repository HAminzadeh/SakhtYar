package com.sakhtyar.config;
import org.springframework.core.env.Environment;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Service;import java.net.*;import java.util.*;
@Service public class ConfigDoctorService {
 private final JdbcTemplate db;private final Environment env;public ConfigDoctorService(JdbcTemplate db,Environment env){this.db=db;this.env=env;}
 public List<Map<String,Object>> run(){List<Map<String,Object>> r=new ArrayList<>();try{db.queryForObject("select 1",Integer.class);r.add(x("PostgreSQL","OK","Connected"));}catch(Exception e){r.add(x("PostgreSQL","FAIL","Database unavailable"));}
 r.add(tcp("Redis",env.getProperty("spring.data.redis.host","localhost"),env.getProperty("spring.data.redis.port",Integer.class,6379)));
 try{URI u=URI.create(env.getProperty("app.ai.ollama.base-url","http://127.0.0.1:11434"));r.add(tcp("Ollama",u.getHost(),u.getPort()>0?u.getPort():80));}catch(Exception e){r.add(x("Ollama","WARN","Invalid endpoint"));}
 try{URI u=URI.create(env.getProperty("app.storage.endpoint","http://localhost:9000"));r.add(tcp("MinIO",u.getHost(),u.getPort()>0?u.getPort():80));}catch(Exception e){r.add(x("MinIO","WARN","Invalid endpoint"));}return r;}
 private Map<String,Object> tcp(String n,String h,int p){try(Socket s=new Socket()){s.connect(new InetSocketAddress(h,p),600);return x(n,"OK",h+":"+p);}catch(Exception e){return x(n,"WARN",h+":"+p+" unavailable");}}
 private Map<String,Object>x(String n,String s,String m){return Map.of("name",n,"status",s,"message",m);}
}