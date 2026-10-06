package com.sakhtyar.knowledge.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.*;
import java.util.stream.Stream;

@Service
public class ResumableKnowledgePipelineService {
 private static final List<String> STAGES=List.of("DISCOVER_SOURCES","HASH_AND_DEDUP","CLASSIFY_DOCUMENTS","USER_SELECTION","NATIVE_EXTRACTION","PAGE_QUALITY_GATE","OCR_REQUIRED_PAGES","PERSIAN_NORMALIZATION","KNOWLEDGE_EXTRACTION","QUALITY_GATE","DATASET_BUILD","PUBLISH");
 private final JdbcTemplate jdbc; private final Path repo; private final ExecutorService workers=Executors.newFixedThreadPool(2); private final KnowledgeFinalPipelineService finalPipeline;
 public ResumableKnowledgePipelineService(JdbcTemplate jdbc,KnowledgeFinalPipelineService finalPipeline,@Value("${sakhtyar.repo-root:${user.dir}}") String root){this.jdbc=jdbc;this.finalPipeline=finalPipeline;this.repo=findRepo(Path.of(root));}

 @Transactional public Map<String,Object> start(String sourceRoot,String user){
   Path src=resolveSource(sourceRoot);UUID workflow=UUID.randomUUID(),correlation=UUID.randomUUID(),run=UUID.randomUUID(),exec=UUID.randomUUID();
   jdbc.update("insert into knowledge_pipeline_run(id,run_code,mode,status,stage,command_text,runbook_version,started_at,workflow_id,correlation_id,pipeline_version,statistics) values(?,?,'FULL','RUNNING','DISCOVER_SOURCES','Resumable Knowledge Wizard','knowledge-resumable-v1',now(),?,?,'knowledge-resumable-v1','{}'::jsonb)",
     run,"KR-"+run.toString().substring(0,8),workflow,correlation);
   jdbc.update("insert into knowledge_intake_execution(id,run_id,workflow_id,correlation_id,status,current_stage,requested_by,source_root,started_at,heartbeat_at) values(?,?,?,?, 'RUNNING','DISCOVER_SOURCES',?,?,now(),now())",exec,run,workflow,correlation,user==null?"system":user,src.toString());
   for(String st:STAGES) jdbc.update("insert into knowledge_pipeline_checkpoint(execution_id,run_id,stage_code) values(?,?,?) on conflict do nothing",exec,run,st);
   event(exec,run,"DISCOVER_SOURCES","STARTED","Execution created");event(exec,run,"DISCOVER_SOURCES","INFO","Worker queued; source: "+src);
   dispatchAfterCommit(exec);
   return state(exec);
 }

 public Map<String,Object> resume(UUID id){jdbc.update("update knowledge_intake_execution set stop_requested=false,status='RUNNING',error_message=null,resumed_at=now(),heartbeat_at=now() where id=?",id);UUID run=runId(id);jdbc.update("update knowledge_pipeline_run set status='RUNNING',error_message=null,finished_at=null where id=?",run);workers.submit(()->advance(id));return state(id);}
 public Map<String,Object> stop(UUID id){jdbc.update("update knowledge_intake_execution set stop_requested=true,status='STOPPING' where id=?",id);return state(id);}
 public Map<String,Object> retry(UUID id){jdbc.update("update knowledge_pipeline_checkpoint set status='PENDING',error_message=null where execution_id=? and status='FAILED'",id);return resume(id);}

 @Transactional public Map<String,Object> select(UUID id,List<String> keys,String selectedBy){
   Set<String> requested=new LinkedHashSet<>(keys==null?List.of():keys);
   jdbc.update("update knowledge_intake_inventory set selected_by_user=false where execution_id=?",id);
   for(String key:requested) jdbc.update("update knowledge_intake_inventory set selected_by_user=true where execution_id=? and relative_path=? and intake_status<>'SKIP_DUPLICATE_EXACT'",id,key);
   List<Map<String,Object>> acceptedRows=jdbc.queryForList("select relative_path,document_id from knowledge_intake_inventory where execution_id=? and selected_by_user=true order by relative_path",id);
   if(acceptedRows.isEmpty())throw new IllegalArgumentException("Select at least one non-duplicate document.");
   List<String> accepted=acceptedRows.stream().map(r->String.valueOf(r.get("relative_path"))).toList();
   List<String> documentIds=acceptedRows.stream().map(r->r.get("document_id")).filter(Objects::nonNull).map(String::valueOf).toList();
   UUID run=runId(id);
   UUID workflow=jdbc.queryForObject("select workflow_id from knowledge_intake_execution where id=?",UUID.class,id);
   String selectionKey="pipeline:"+id;
   Integer next=jdbc.queryForObject("select coalesce(max(version_no),0)+1 from knowledge_intake_selection_version where selection_key=?",Integer.class,selectionKey);
   UUID previous=jdbc.query("select id from knowledge_intake_selection_version where selection_key=? and status='CURRENT' order by version_no desc limit 1",rs->rs.next()?(UUID)rs.getObject(1):null,selectionKey);
   if(previous!=null)jdbc.update("update knowledge_intake_selection_version set status='SUPERSEDED' where id=?",previous);
   UUID selectionVersion=UUID.randomUUID();
   String relativeJson=jsonArray(accepted), documentJson=jsonArray(documentIds);
   String policyJson="{\"selectionMode\":\"USER_CHECKBOX\",\"selectedRelativePaths\":"+relativeJson+",\"selectedCount\":"+accepted.size()+"}";
   jdbc.update("insert into knowledge_intake_selection_version(id,workflow_id,run_id,selection_key,version_no,status,selected_document_ids,processing_policy,supersedes_id,selected_by) values(?,?,?,?,?,'CURRENT',cast(? as jsonb),cast(? as jsonb),?,?)",
     selectionVersion,workflow,run,selectionKey,next,documentJson,policyJson,previous,selectedBy==null?"system":selectedBy);
   jdbc.update("update knowledge_intake_execution set selection_version_id=?,status='RUNNING',current_stage='NATIVE_EXTRACTION',heartbeat_at=now() where id=?",selectionVersion,id);
   complete(id,run,"USER_SELECTION",accepted.size(),0,0);
   event(id,run,"USER_SELECTION","INFO","Selection frozen: "+accepted.size()+" document(s), version "+next);
   dispatchAfterCommit(id);
   return state(id);
 }

 public List<Map<String,Object>> folders(String requested){
   Path base=(requested==null||requested.isBlank())?repo:resolveBrowsePath(requested);
   if(!Files.isDirectory(base))throw new IllegalArgumentException("Directory not found: "+base);
   List<Map<String,Object>> result=new ArrayList<>();
   Path parent=base.getParent();
   if(parent!=null)result.add(Map.of("name","..","path",parent.toString(),"parent",true));
   try(Stream<Path> stream=Files.list(base)){
    stream.filter(Files::isDirectory).sorted(Comparator.comparing(p->p.getFileName().toString().toLowerCase(Locale.ROOT))).limit(250)
      .forEach(p->result.add(Map.of("name",p.getFileName().toString(),"path",p.toAbsolutePath().normalize().toString(),"parent",false)));
   }catch(IOException e){throw new IllegalStateException("Cannot browse directory: "+base,e);}
   return result;
 }
 private Path resolveBrowsePath(String value){Path p=Path.of(value);if(!p.isAbsolute())p=repo.resolve(p);return p.toAbsolutePath().normalize();}
 private void dispatchAfterCommit(UUID id){
   Runnable job=()->workers.submit(()->advance(id));
   if(TransactionSynchronizationManager.isActualTransactionActive()){
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){job.run();}});
   }else job.run();
 }
 private String jsonArray(Collection<String> values){
   StringJoiner joiner=new StringJoiner(",", "[", "]");
   for(String value:values)joiner.add("\""+value.replace("\\","\\\\").replace("\"","\\\"").replace("\r","\\r").replace("\n","\\n")+"\"");
   return joiner.toString();
 }

 public Map<String,Object> state(UUID id){
   Map<String,Object> e=jdbc.queryForMap("select * from knowledge_intake_execution where id=?",id);
   e.put("checkpoints",jdbc.queryForList("select stage_code,status,attempt,processed_count,failed_count,skipped_count,error_message,started_at,heartbeat_at,finished_at,updated_at from knowledge_pipeline_checkpoint where execution_id=?",id));
   e.put("inventory",jdbc.queryForList("select relative_path,detected_title,detected_category,file_extension,sha256,size_bytes,page_count,native_chars,suspicious_pages,intake_status,reason,selected_by_user,document_id from knowledge_intake_inventory where execution_id=? order by detected_category,relative_path",id));
   return e;
 }
 public Map<String,Object> result(UUID id){return finalPipeline.result(id);}
 public List<Map<String,Object>> recent(){return jdbc.queryForList("select id,run_id,status,current_stage,total_documents,completed_documents,failed_documents,source_root,created_at,heartbeat_at from knowledge_intake_execution order by created_at desc limit 20");}
 public List<Map<String,Object>> events(UUID id,long after){
   return jdbc.queryForList("select id,stage_code,event_type,message,payload,created_at from knowledge_resumable_pipeline_event where execution_id=? and id>? order by id asc limit 1000",id,after);
 }

 private void advance(UUID id){
  try{
   UUID run=runId(id);
   for(String stage:STAGES){
    if(stopRequested(id)){jdbc.update("update knowledge_intake_execution set status='PAUSED',heartbeat_at=now() where id=?",id);event(id,run,stage,"PAUSED","Safe stop requested");return;}
    String st=checkpointStatus(id,stage);if("COMPLETED".equals(st))continue;
    if("USER_SELECTION".equals(stage)){jdbc.update("update knowledge_intake_execution set status='WAITING_FOR_USER',current_stage='USER_SELECTION',heartbeat_at=now() where id=?",id);return;}
    begin(id,run,stage);
    if("DISCOVER_SOURCES".equals(stage))discover(id,run);
    else if("HASH_AND_DEDUP".equals(stage))hash(id,run);
    else if("CLASSIFY_DOCUMENTS".equals(stage))classify(id,run);
    else {int processed=finalPipeline.execute(id,run,stage);complete(id,run,stage,processed,0,0);}
   }
   jdbc.update("update knowledge_intake_execution set status='COMPLETED',current_stage='PUBLISH',error_message=null,finished_at=now(),heartbeat_at=now() where id=?",id);
   jdbc.update("update knowledge_pipeline_run set status='COMPLETED',stage='PUBLISH',finished_at=now() where id=?",run);
  }catch(Exception ex){failExecution(id,ex);}
 }

 private void discover(UUID id,UUID run)throws Exception{
   Path root=Path.of(String.valueOf(jdbc.queryForObject("select source_root from knowledge_intake_execution where id=?",String.class,id)));
   int n=0;try(Stream<Path>w=Files.walk(root)){for(Path p:(Iterable<Path>)w.filter(Files::isRegularFile).filter(this::supported)::iterator){
    if(stopRequested(id))return;String rel=root.relativize(p).toString();long size=Files.size(p);String fileName=p.getFileName().toString();int dot=fileName.lastIndexOf('.');String title=dot>0?fileName.substring(0,dot):fileName;String ext=dot>=0?fileName.substring(dot+1).toLowerCase(Locale.ROOT):"";Path relPath=Path.of(rel);String category=relPath.getNameCount()>1?relPath.getName(0).toString():"ROOT";
    jdbc.update("insert into knowledge_intake_inventory(execution_id,run_id,relative_path,absolute_path,size_bytes,detected_title,detected_category,file_extension) values(?,?,?,?,?,?,?,?) on conflict(execution_id,relative_path) do update set size_bytes=excluded.size_bytes,detected_title=excluded.detected_title,detected_category=excluded.detected_category,file_extension=excluded.file_extension,updated_at=now()",id,run,rel,p.toString(),size,title,category,ext);n++;
    heartbeat(id,"DISCOVER_SOURCES",n);if(n==1||n%25==0)event(id,run,"DISCOVER_SOURCES","PROGRESS","Discovered "+n+" documents; current: "+rel);
   }}jdbc.update("update knowledge_intake_execution set total_documents=? where id=?",n,id);complete(id,run,"DISCOVER_SOURCES",n,0,0);
 }
 private void hash(UUID id,UUID run)throws Exception{
   var rows=jdbc.queryForList("select relative_path,absolute_path,sha256 from knowledge_intake_inventory where execution_id=? order by relative_path",id);Map<String,String> seen=new HashMap<>();int done=0,skip=0;
   for(var r:rows){if(stopRequested(id))return;String rel=(String)r.get("relative_path"),h=(String)r.get("sha256");if(h==null||h.isBlank())h=sha(Path.of((String)r.get("absolute_path")));
    String first=seen.putIfAbsent(h,rel);String status=first==null?"HASHED":"SKIP_DUPLICATE_EXACT";String reason=first==null?null:"Exact duplicate of "+first;
    jdbc.update("update knowledge_intake_inventory set sha256=?,intake_status=?,reason=?,updated_at=now() where execution_id=? and relative_path=?",h,status,reason,id,rel);done++;if(first!=null)skip++;heartbeat(id,"HASH_AND_DEDUP",done);if(done==1||done%10==0)event(id,run,"HASH_AND_DEDUP","PROGRESS","Hashed "+done+"/"+rows.size()+"; current: "+rel);
   }complete(id,run,"HASH_AND_DEDUP",done,0,skip);
 }
 private void classify(UUID id,UUID run)throws Exception{
   Path py=repo.resolve(".local/venv-persian-intelligence/Scripts/python.exe"),script=repo.resolve("python/src/sakhtyar_python/legacy/dry_run_v07.py");
   if(Files.isRegularFile(py)&&Files.isRegularFile(script)){ // Reuse proven intake classifier, then import its report.
    Path sourcePath=Path.of((String)jdbc.queryForObject("select source_root from knowledge_intake_execution where id=?",String.class,id)).toAbsolutePath().normalize();
    ProcessBuilder pb=new ProcessBuilder(py.toString(),script.toString(),"--root",sourcePath.toString());
    pb.directory(repo.toFile());pb.redirectErrorStream(true);Process p=pb.start();String log=new String(p.getInputStream().readAllBytes(),StandardCharsets.UTF_8);int code=p.waitFor();
    if(code!=0)throw new IllegalStateException("Intake classifier failed: "+log.substring(0,Math.min(4000,log.length())));
    Path report=repo.resolve(".local/persian-intake/dry-run-v07.json");importReport(id,report);
   }else{jdbc.update("update knowledge_intake_inventory set intake_status=case when intake_status='HASHED' then 'VALID_NATIVE' else intake_status end where execution_id=?",id);}
   int n=jdbc.queryForObject("select count(*) from knowledge_intake_inventory where execution_id=?",Integer.class,id);complete(id,run,"CLASSIFY_DOCUMENTS",n,0,0);
 }
 private void importReport(UUID id,Path report)throws Exception{
   String json=Files.readString(report);Matcher m=Pattern.compile("\\{([^{}]*)\\}",Pattern.DOTALL).matcher(json);
   while(m.find()){String b=m.group(1),path=sval(b,"path"),status=sval(b,"status"),reason=sval(b,"reason");int pages=ival(b,"page_count"),chars=ival(b,"native_chars");String file=Path.of(path).getFileName().toString();
    jdbc.update("update knowledge_intake_inventory set intake_status=?,reason=?,page_count=?,native_chars=?,updated_at=now() where execution_id=? and (absolute_path=? or relative_path like ?) and intake_status<>'SKIP_DUPLICATE_EXACT'",status,reason,pages,chars,id,path,"%"+file);
   }
 }
 private void placeholder(UUID id,UUID run,String stage){int n=jdbc.queryForObject("select count(*) from knowledge_intake_inventory where execution_id=? and selected_by_user=true",Integer.class,id);complete(id,run,stage,n,0,0);}
 private void begin(UUID id,UUID run,String s){jdbc.update("update knowledge_pipeline_checkpoint set status='RUNNING',attempt=attempt+1,started_at=coalesce(started_at,now()),heartbeat_at=now(),updated_at=now() where execution_id=? and stage_code=?",id,s);jdbc.update("update knowledge_intake_execution set current_stage=?,status='RUNNING',heartbeat_at=now() where id=?",s,id);event(id,run,s,"STARTED",s);}
 private void complete(UUID id,UUID run,String s,int p,int f,int sk){jdbc.update("update knowledge_pipeline_checkpoint set status='COMPLETED',processed_count=?,failed_count=?,skipped_count=?,finished_at=now(),heartbeat_at=now(),updated_at=now() where execution_id=? and stage_code=?",p,f,sk,id,s);event(id,run,s,"COMPLETED",s);}
 private void heartbeat(UUID id,String s,int p){jdbc.update("update knowledge_pipeline_checkpoint set processed_count=?,heartbeat_at=now(),updated_at=now() where execution_id=? and stage_code=?",p,id,s);jdbc.update("update knowledge_intake_execution set heartbeat_at=now() where id=?",id);}
 private void failExecution(UUID id,Exception ex){try{UUID r=runId(id);String s=(String)jdbc.queryForObject("select current_stage from knowledge_intake_execution where id=?",String.class,id);String msg=String.valueOf(ex.getMessage());jdbc.update("update knowledge_pipeline_checkpoint set status='FAILED',error_message=?,updated_at=now() where execution_id=? and stage_code=?",msg,id,s);jdbc.update("update knowledge_intake_execution set status='FAILED',error_message=?,heartbeat_at=now() where id=?",msg,id);jdbc.update("update knowledge_pipeline_run set status='FAILED',error_message=? where id=?",msg,r);event(id,r,s,"FAILED",msg);}catch(Exception ignored){}}
 private void event(UUID e,UUID r,String s,String t,String m){jdbc.update("insert into knowledge_resumable_pipeline_event(execution_id,run_id,stage_code,event_type,message) values(?,?,?,?,?)",e,r,s,t,m);}
 private UUID runId(UUID id){return jdbc.queryForObject("select run_id from knowledge_intake_execution where id=?",UUID.class,id);}
 private String checkpointStatus(UUID id,String s){return jdbc.queryForObject("select status from knowledge_pipeline_checkpoint where execution_id=? and stage_code=?",String.class,id,s);}
 private boolean stopRequested(UUID id){return Boolean.TRUE.equals(jdbc.queryForObject("select stop_requested from knowledge_intake_execution where id=?",Boolean.class,id));}
 private boolean supported(Path p){String n=p.getFileName().toString().toLowerCase();return n.endsWith(".pdf")||n.endsWith(".docx")||n.endsWith(".pptx")||n.endsWith(".txt")||n.endsWith(".md");}
 private Path resolveSource(String s){Path p=(s==null||s.isBlank()?repo.resolve("DocumentationOfLawsAndRegulations"):Path.of(s));if(!p.isAbsolute())p=repo.resolve(p);p=p.normalize();if(!Files.isDirectory(p))throw new IllegalArgumentException("Source directory not found: "+p);return p;}
 private Path findRepo(Path p){p=p.toAbsolutePath().normalize();for(int i=0;i<6&&p!=null;i++,p=p.getParent())if(Files.isDirectory(p.resolve("backend"))&&Files.isDirectory(p.resolve("frontend")))return p;Path k=Path.of("D:/ChatGPT_Projects/SakhtYar/source");return Files.isDirectory(k)?k.toAbsolutePath():Path.of(System.getProperty("user.dir")).toAbsolutePath();}
 private String sha(Path p)throws Exception{MessageDigest md=MessageDigest.getInstance("SHA-256");try(InputStream in=Files.newInputStream(p)){byte[]b=new byte[1024*1024];for(int n;(n=in.read(b))>0;)md.update(b,0,n);}return HexFormat.of().formatHex(md.digest());}
 private String sval(String b,String k){Matcher m=Pattern.compile("\""+Pattern.quote(k)+"\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"").matcher(b);return m.find()?m.group(1).replace("\\\\","\\").replace("\\\"","\""):"";}
 private int ival(String b,String k){Matcher m=Pattern.compile("\""+Pattern.quote(k)+"\"\\s*:\\s*(\\d+)").matcher(b);return m.find()?Integer.parseInt(m.group(1)):0;}
}