package com.sakhtyar.config;
import org.springframework.stereotype.Component;import java.util.*;
@Component public class ConfigRegistry {
 private static ConfigDefinition d(String k,String c,String fa,String en,String dfa,String den,String type,boolean secret,String mode,String def,String prop,String env,boolean boot,boolean req){return new ConfigDefinition(k,c,fa,en,dfa,den,type,secret,mode,def,prop,env,boot,req);}
 private final List<ConfigDefinition> defs=List.of(
 d("database.url","Database","آدرس JDBC","JDBC URL","آدرس اتصال PostgreSQL","PostgreSQL JDBC URL","STRING",false,"RESTART_REQUIRED","jdbc:postgresql://localhost:5432/sakhtyar","spring.datasource.url","DB_URL",true,true),
 d("database.name","Database","نام دیتابیس","Database","نام پایگاه داده PostgreSQL","PostgreSQL database name","STRING",false,"RESTART_REQUIRED","sakhtyar",null,"POSTGRES_DB",true,true),
 d("database.username","Database","نام کاربری","Username","کاربر PostgreSQL","PostgreSQL user","STRING",false,"RESTART_REQUIRED","sakhtyar","spring.datasource.username","POSTGRES_USER",true,true),
 d("database.password","Database","رمز عبور","Password","Secret اتصال PostgreSQL","PostgreSQL credential","SECRET",true,"RESTART_REQUIRED",null,"spring.datasource.password","POSTGRES_PASSWORD",true,true),
 d("database.pool.maximumSize","Database","حداکثر Pool","Maximum pool size","حداکثر اتصال Hikari","Hikari maximum pool size","INTEGER",false,"RESTART_REQUIRED","10","spring.datasource.hikari.maximum-pool-size",null,false,false),
 d("database.pool.minimumIdle","Database","حداقل Idle","Minimum idle","حداقل اتصال آماده","Minimum idle connections","INTEGER",false,"RESTART_REQUIRED","2","spring.datasource.hikari.minimum-idle",null,false,false),

 d("redis.host","Redis","میزبان","Host","میزبان Redis","Redis host","STRING",false,"RESTART_REQUIRED","localhost","spring.data.redis.host","REDIS_HOST",true,false),
 d("redis.port","Redis","پورت","Port","پورت Redis","Redis port","INTEGER",false,"RESTART_REQUIRED","6379","spring.data.redis.port","REDIS_PORT",true,false),
 d("redis.password","Redis","رمز عبور","Password","Secret اتصال Redis","Redis credential","SECRET",true,"RESTART_REQUIRED",null,"spring.data.redis.password","REDIS_PASSWORD",true,false),

 d("storage.endpoint","Storage","آدرس MinIO","MinIO endpoint","آدرس ذخیره‌سازی شیء","Object storage endpoint","STRING",false,"RESTART_REQUIRED","http://localhost:9000","app.storage.endpoint","MINIO_ENDPOINT",true,false),
 d("storage.accessKey","Storage","کلید دسترسی","Access key","شناسه دسترسی MinIO","MinIO access key","SECRET",true,"RESTART_REQUIRED",null,"app.storage.access-key","MINIO_ACCESS_KEY",true,false),
 d("storage.secretKey","Storage","کلید محرمانه","Secret key","Secret مربوط به MinIO","MinIO secret key","SECRET",true,"RESTART_REQUIRED",null,"app.storage.secret-key","MINIO_SECRET_KEY",true,false),
 d("storage.bucket","Storage","Bucket","Bucket","Bucket اسناد ساخت‌یار","Document bucket","STRING",false,"RESTART_REQUIRED","sakhtyar-documents","app.storage.bucket","MINIO_BUCKET",false,false),

 d("ai.enabled","AI","فعال","Enabled","فعال‌سازی AI","Enable AI","BOOLEAN",false,"RESTART_REQUIRED","true","app.ai.enabled","SAKHTYAR_AI_ENABLED",false,false),
 d("ai.provider","AI","ارائه‌دهنده","Provider","ارائه‌دهنده مدل","AI provider","STRING",false,"RESTART_REQUIRED","ollama","app.ai.provider","SAKHTYAR_AI_PROVIDER",false,false),
 d("ai.ollama.baseUrl","AI","آدرس Ollama","Ollama URL","آدرس Runtime مدل","Ollama runtime URL","STRING",false,"RESTART_REQUIRED","http://127.0.0.1:11434","app.ai.ollama.base-url","OLLAMA_BASE_URL",true,false),
 d("ai.ollama.model","AI","مدل","Model","مدل پیش‌فرض Ollama","Default Ollama model","STRING",false,"HOT_RELOAD","qwen3.5:4b","app.ai.ollama.model","OLLAMA_MODEL",false,false),
 d("ai.ollama.temperature","AI","Temperature","Temperature","میزان تنوع پاسخ","Sampling temperature","DECIMAL",false,"HOT_RELOAD","0.1","app.ai.ollama.temperature","OLLAMA_TEMPERATURE",false,false),
 d("ai.ollama.timeout","AI","مهلت پاسخ","Timeout","مهلت پاسخ مدل","Model timeout","INTEGER",false,"HOT_RELOAD","90","app.ai.ollama.timeout-seconds","OLLAMA_TIMEOUT_SECONDS",false,false),
 d("ai.ollama.contextLength","AI","طول Context","Context length","طول زمینه مدل","Model context length","INTEGER",false,"RESTART_REQUIRED","2048","app.ai.ollama.context-length","OLLAMA_CONTEXT_LENGTH",false,false),
 d("ai.ollama.forceCpu","AI","اجبار CPU","Force CPU","اجرای CPU","Force CPU execution","BOOLEAN",false,"RESTART_REQUIRED","true","app.ai.ollama.force-cpu","OLLAMA_FORCE_CPU",false,false),

 d("integration.neshan.enabled","Integrations","نشان فعال","Neshan enabled","فعال‌سازی سرویس نشان","Enable Neshan","BOOLEAN",false,"HOT_RELOAD","false","app.integrations.neshan.enabled","NESHAN_ENABLED",false,false),
 d("integration.neshan.baseUrl","Integrations","آدرس نشان","Neshan URL","آدرس سرویس نشان","Neshan URL","STRING",false,"HOT_RELOAD","https://api.neshan.org","app.integrations.neshan.base-url","NESHAN_BASE_URL",false,false),
 d("integration.neshan.apiKey","Integrations","کلید API نشان","Neshan API key","Secret سرویس نشان","Neshan credential","SECRET",true,"HOT_RELOAD",null,"app.integrations.neshan.service-api-key","NESHAN_SERVICE_API_KEY",true,false),
 d("integration.neshan.timeout","Integrations","مهلت نشان","Neshan timeout","مهلت درخواست نشان","Neshan timeout","INTEGER",false,"HOT_RELOAD","10","app.integrations.neshan.timeout-seconds","NESHAN_TIMEOUT_SECONDS",false,false),

 d("operations.prometheus","Observability","Prometheus","Prometheus","آدرس Prometheus","Prometheus URL","STRING",false,"RESTART_REQUIRED","http://localhost:9090","app.operations.prometheus-url","PROMETHEUS_URL",true,false),
 d("operations.loki","Observability","Loki","Loki","آدرس Loki","Loki URL","STRING",false,"RESTART_REQUIRED","http://localhost:3100","app.operations.loki-url","LOKI_URL",true,false),
 d("operations.tempo","Observability","Tempo","Tempo","آدرس Tempo","Tempo URL","STRING",false,"RESTART_REQUIRED","http://localhost:3200","app.operations.tempo-url","TEMPO_URL",true,false),
 d("operations.grafana","Observability","Grafana","Grafana","آدرس Grafana","Grafana URL","STRING",false,"RESTART_REQUIRED","http://localhost:13001","app.operations.grafana-url","GRAFANA_URL",true,false),
 d("operations.otlp","Observability","OTLP Traces","OTLP traces","آدرس خروجی Trace","Trace exporter endpoint","STRING",false,"RESTART_REQUIRED","http://localhost:4318/v1/traces","management.opentelemetry.tracing.export.otlp.endpoint","OTEL_EXPORTER_OTLP_TRACES_ENDPOINT",true,false),

 d("security.cors","Security","مبدأهای CORS","CORS origins","Originهای مجاز","Allowed origins","STRING",false,"RESTART_REQUIRED","http://localhost:5175","app.cors.allowed-origins","CORS_ALLOWED_ORIGINS",false,false),
 d("security.jwtSecret","Security","کلید JWT","JWT secret","Secret امضای JWT","JWT signing secret","SECRET",true,"RESTART_REQUIRED",null,"app.security.jwt.secret","APP_JWT_SECRET",true,true),
 d("security.jwtExpiration","Security","عمر Access Token","Access token lifetime","دقیقه انقضا","Expiration minutes","INTEGER",false,"HOT_RELOAD","30","app.security.jwt.access-expiration-minutes","APP_JWT_ACCESS_EXPIRATION_MINUTES",false,false),
 d("security.refreshDays","Security","عمر Refresh","Refresh lifetime","روز انقضای Refresh","Refresh expiration days","INTEGER",false,"HOT_RELOAD","30","app.security.refresh.expiration-days","APP_REFRESH_EXPIRATION_DAYS",false,false),
 d("security.cookieSecure","Security","Cookie امن","Secure cookie","Secure flag","Secure cookie flag","BOOLEAN",false,"RESTART_REQUIRED","false","app.security.cookies.secure","APP_COOKIE_SECURE",false,false),
 d("security.cookieSameSite","Security","SameSite","SameSite","سیاست SameSite","SameSite policy","STRING",false,"RESTART_REQUIRED","Strict","app.security.cookies.same-site","APP_COOKIE_SAME_SITE",false,false),

 d("bootstrap.adminUsername","Bootstrap","کاربر مدیر اولیه","Bootstrap admin","نام مدیر اولیه","Bootstrap administrator","STRING",false,"RESTART_REQUIRED","admin","app.bootstrap-admin.username","APP_BOOTSTRAP_ADMIN_USERNAME",true,true),
 d("bootstrap.adminPassword","Bootstrap","رمز مدیر اولیه","Admin password","Secret مدیر اولیه","Bootstrap admin secret","SECRET",true,"RESTART_REQUIRED",null,"app.bootstrap-admin.password","APP_BOOTSTRAP_ADMIN_PASSWORD",true,true),
 d("runtime.serverPort","Runtime","پورت Backend","Backend port","پورت HTTP","HTTP server port","INTEGER",false,"RESTART_REQUIRED","8080","server.port",null,true,true)
 );
 public List<ConfigDefinition> all(){return defs;} public Optional<ConfigDefinition> find(String k){return defs.stream().filter(x->x.key().equals(k)).findFirst();} public List<String> categories(){return defs.stream().map(ConfigDefinition::category).distinct().toList();}
}