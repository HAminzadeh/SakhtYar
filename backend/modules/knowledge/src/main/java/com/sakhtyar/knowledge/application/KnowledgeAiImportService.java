package com.sakhtyar.knowledge.application;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** A separate, review-only intake. Never writes approved/verified production knowledge rules. */
@Service
public class KnowledgeAiImportService {
    private static final long MAX_ARCHIVE = 24L * 1024 * 1024;
    private static final int MAX_FILE = 6 * 1024 * 1024;
    private static final int MAX_TOTAL = 16 * 1024 * 1024;
    private static final int MAX_RECORDS = 3000;
    private static final List<String> REQUIRED = List.of(
        "manifest.json", "documents.jsonl", "rules.jsonl", "legal_segments.jsonl");
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final ObjectMapper mapper;

    public KnowledgeAiImportService(JdbcTemplate jdbc, TransactionTemplate transaction, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.mapper = mapper;
    }

    public Map<String, Object> instructions(String provider) {
        String ai = provider == null || provider.isBlank() ? "ChatGPT" : provider.strip();
        if (ai.length() > 80) throw new IllegalArgumentException("Provider label too long");
        String prompt = """
            نقش شما: استخراج‌کننده مستندات حقوقی و مهندسی برای سامانه ساخت‌یار.
            زبان خروجی: فارسی؛ قالب خروجی دقیقاً JSON Lines و فایل ZIP در پوشه ریشه آرشیو.
            خروجی‌های شما به‌صورت پیش‌فرض قانون معتبر نیستند و باید توسط کارشناس بازبینی شوند.

            ۱) فایل‌های قانونی/فنی را که کاربر در همین گفتگو آپلود می‌کند بخوان. هیچ حکم، ماده، تبصره، عدد، تاریخ یا شماره صفحه‌ای اختراع نکن.
            ۲) اگر صفحه‌ای نامفهوم است، آن بخش را حذف نکن؛ در فایل legal_segments.jsonl با segment_type=PASSAGE ثبت کن.
            ۳) متن full_text را عیناً از منبع، بدون خلاصه‌سازی یا ترکیب دو ماده مستقل منتقل کن. evidence_quote باید زیررشته دقیق full_text باشد.
            ۴) برای هر سند doc_key یکتا بساز؛ عنوان، مرجع، نوع و نشانی منبع را ثبت کن. منبع قانونی را از جزوه آموزشی تفکیک کن.
            ۵) یک فایل documents.jsonl شامل یک شیء JSON در هر سطر با فیلدهای:
               {"doc_key":"doc-001","title":"عنوان کامل سند","category":"LAW","source_reference":"نام فایل اصلی و شناسه منبع","authority":"نام مرجع رسمی یا نامعلوم","source_sha256":null}
               category فقط LAW / EDUCATION / TECHNICAL / REFERENCE / OTHER / UNCLASSIFIED باشد.
               مقدار source_sha256 فقط اگر هش واقعی فایل اصلی را حساب کرده‌ای ثبت شود، وگرنه null.
            ۶) در فایل legal_segments.jsonl هر سطر:
               {"doc_key":"doc-001","item_key":"seg-001","segment_type":"ARTICLE","article_label":"ماده ۱","full_text":"متن عینی منبع با حداقل ۱۲ کاراکتر","evidence_quote":"عبارت عینی از همان متن با حداقل ۸ کاراکتر","page_from":1,"page_to":1}
               segment_type فقط ARTICLE / NOTE / CLAUSE / PASSAGE.
            ۷) در فایل rules.jsonl فقط احکام صریح و قابل‌استناد با همین ساختار سطر بنویس؛ متن full_text عین عبارت حکم باشد.
               برای منابع آموزشی بدون حکم قانونی، قوانین ساختگی ننویس و rules.jsonl را خالی بگذار.
               برای هر item_key در هر فایل شناسه یکتا تعیین کن. شماره صفحات باید مربوط به PDF اصلی و صحیح باشند.
            ۸) فایل manifest.json در ریشه ZIP:
               {"schema_version":"sakhtyar-ai-knowledge-v1","instruction_version":"v53.0","source_ai":"ChatGPT","language":"fa","review_policy":"HUMAN_REQUIRED"}
               مقدار source_ai را برابر نام مدل/سرویس واقعی قرار بده.
            ۹) خروجی دقیقاً چهار فایل UTF-8 باشد: manifest.json, documents.jsonl, rules.jsonl, legal_segments.jsonl.
               فرمت JSONL یعنی هر خط یک JSON کامل و مستقل؛ از Markdown و code fence داخل فایل‌ها استفاده نکن.
               نه PDF خام و نه فایل‌های اضافی در بسته نباشند. فایل ZIP خروجی را برای دانلود تحویل بده؛ در صورت امکان RAR هم مجاز است.
            ۱۰) سقف هر بسته ۳۰۰۰ رکورد و ۲۴ مگابایت؛ اگر بیشتر است چند بسته مستقل بساز.
            ۱۱) همه خروجی‌ها صرفاً نامزد بررسی هستند. اعتبار رسمی، تاریخ اجرای قانون و صحت استناد باید دستی بررسی شود.
            """;
        return Map.of("provider", ai, "schemaVersion", "sakhtyar-ai-knowledge-v1", "prompt", prompt);
    }

    /** New explicit mode; the old single-argument method remains for compatibility. */
    public Map<String,Object> instructions(String provider,String mode) {
        String ai=provider==null||provider.isBlank()?"ChatGPT":provider.strip();
        if(ai.length()>80)throw new IllegalArgumentException("Provider label too long");
        String selected=mode==null||mode.isBlank()?"LAW":mode.trim().toUpperCase(Locale.ROOT);
        return Map.of("provider",ai,"mode",selected,"schemaVersion","sakhtyar-ai-knowledge-v1",
                      "prompt",KnowledgeAiPromptLibrary.prompt(selected));
    }

    /** Hash-only preflight: avoids transferring previously processed ZIP/RAR files. */
    public Map<String,Object> knownHashes(List<String> supplied) {
        if(supplied==null||supplied.size()>500)throw new IllegalArgumentException("Hash list must have at most 500 entries");
        LinkedHashSet<String> hashes=new LinkedHashSet<>();
        for(String h:supplied){
            if(h==null||!h.matches("[0-9a-fA-F]{64}"))throw new IllegalArgumentException("Invalid SHA-256 hash");
            hashes.add(h.toLowerCase(Locale.ROOT));
        }
        if(hashes.isEmpty())return Map.of("hashes",List.of());
        String marks=String.join(",",Collections.nCopies(hashes.size(),"?"));
        List<String> found=jdbc.queryForList(
            "select archive_sha256 from knowledge_ai_import_batch where archive_sha256 in ("+marks+")",
            String.class,hashes.toArray());
        return Map.of("hashes",found);
    }

    /** Recent educational passages across all imported AI bundles, never promoted as rules. */
    public List<Map<String,Object>> educationalItems() {
        return jdbc.queryForList("""
            select i.id::text as id, b.archive_name,d.title as document_title,
                   d.category,d.source_reference,i.segment_type,i.article_label,
                   i.full_text,i.evidence_quote,i.page_from,i.page_to,i.review_status
              from knowledge_ai_import_item i
              join knowledge_ai_import_document d on d.id=i.document_id
              join knowledge_ai_import_batch b on b.id=i.batch_id
             where i.record_type='SEGMENT' and d.category in ('EDUCATION','TECHNICAL') and i.archived_at is null and d.archived_at is null and b.archived_at is null
             order by b.created_at desc,d.title,i.page_from,i.item_key
             limit 2000
            """);
    }

    private record Doc(String key, String title, String category, String reference, String authority, String hash) {}
    private record Item(String key, String doc, String recordType, String segmentType,
                        String label, String fullText, String evidence, int from, int to, String hash) {}

    public Map<String,Object> upload(MultipartFile upload, String actor) {
        if (upload == null || upload.isEmpty() || upload.getSize() > MAX_ARCHIVE)
            throw new IllegalArgumentException("Archive must be nonempty and <=24 MB.");
        String filename = Optional.ofNullable(upload.getOriginalFilename()).orElse("knowledge.zip");
        if (filename.length() > 250 || filename.contains("/") || filename.contains("\\"))
            throw new IllegalArgumentException("Invalid archive filename");
        String lower = filename.toLowerCase(Locale.ROOT);
        String fmt = lower.endsWith(".zip") ? "ZIP" : lower.endsWith(".rar") ? "RAR" : "";
        if (fmt.isEmpty()) throw new IllegalArgumentException("Only ZIP or RAR is supported.");
        try {
            byte[] data = upload.getBytes();
            if (data.length > MAX_ARCHIVE) throw new IllegalArgumentException("Archive too large");
            String archiveHash = sha256(data);
            Integer existing = jdbc.queryForObject("select count(*) from knowledge_ai_import_batch where archive_sha256=?", Integer.class, archiveHash);
            if (existing != null && existing > 0) {
                String oldId=jdbc.queryForObject("select id::text from knowledge_ai_import_batch where archive_sha256=?",String.class,archiveHash);
                return Map.of("id",oldId,"status","SKIPPED_DUPLICATE","documents",0,
                    "rules",0,"segments",0,"sha256",archiveHash);
            }
            Map<String,byte[]> files = "ZIP".equals(fmt) ? unzip(data) : unrar(data);
            JsonNode manifest = mapper.readTree(decode(files.get("manifest.json")));
            if (!"sakhtyar-ai-knowledge-v1".equals(req(manifest,"schema_version",60)))
                throw new IllegalArgumentException("Unsupported manifest schema_version");
            String instruction = req(manifest,"instruction_version",40);
            String provider = req(manifest,"source_ai",160);
            if (!"HUMAN_REQUIRED".equals(req(manifest,"review_policy",40)))
                throw new IllegalArgumentException("review_policy must be HUMAN_REQUIRED");
            // SAKHTYAR_V533_ROW_QUARANTINE: a broken row cannot block sound rows in the same archive.
            List<Map<String,Object>> issues = new ArrayList<>();
            List<JsonNode> documentLines = readJsonlWithIssues(files.get("documents.jsonl"),"documents.jsonl",issues);
            List<JsonNode> ruleLines = readJsonlWithIssues(files.get("rules.jsonl"),"rules.jsonl",issues);
            List<JsonNode> segmentLines = readJsonlWithIssues(files.get("legal_segments.jsonl"),"legal_segments.jsonl",issues);
            if(documentLines.size()+ruleLines.size()+segmentLines.size()+issues.size()>MAX_RECORDS)
                throw new IllegalArgumentException("More than 3000 input records");
            LinkedHashMap<String,Doc> docs = new LinkedHashMap<>();
            String mode = instruction.toLowerCase(Locale.ROOT).contains("education")?"EDUCATION":"LAW";
            for(int i=0;i<documentLines.size();i++) {
                JsonNode row=documentLines.get(i);
                try {
                    String key=req(row,"doc_key",120), cat=req(row,"category",30);
                    if(!Set.of("LAW","EDUCATION","TECHNICAL","REFERENCE","OTHER","UNCLASSIFIED").contains(cat))
                        throw new IllegalArgumentException("Invalid document category for "+key);
                    String srcHash=optional(row,"source_sha256",64);
                    if(srcHash != null && !srcHash.matches("[a-fA-F0-9]{64}"))throw new IllegalArgumentException("Invalid source_sha256 for "+key);
                    // Respect KnowledgeAiPromptLibrary's LAW / EDUCATION separation.
                    // Contradictions are never auto-promoted to legally binding rules.
                    if("EDUCATION".equals(mode) && "LAW".equals(cat)) {
                        issues.add(importIssue("documents.jsonl",i+1,row,"CATEGORY_CONFLICT",
                            "Education prompt produced LAW category", "Review the source and use EDUCATION/TECHNICAL or the LAW extraction prompt"));
                        cat="UNCLASSIFIED";
                    }
                    if("EDUCATION".equals(mode) && "UNCLASSIFIED".equals(cat))cat="EDUCATION";
                    String title=req(row,"title",700), reference=req(row,"source_reference",1800);
                    // Never mistake a course handout for an official statute based on section numbering.
                    String clues=(title+" "+reference).toLowerCase(Locale.ROOT);
                    boolean training=clues.matches("(?s).*(جزوه|آموزش|تمرین|درسنامه|کلاس|lecture|tutorial|handout|training).*");
                    if(training && "LAW".equals(cat)){
                        issues.add(importIssue("documents.jsonl",i+1,row,"EDUCATIONAL_SOURCE",
                            "Legal category conflicts with educational title/source", "Classify as EDUCATION; verify any quoted law against the official document"));
                        cat="EDUCATION";
                    }
                    if(training && "UNCLASSIFIED".equals(cat))cat="EDUCATION";
                    Doc d=new Doc(key,title,cat,reference,optional(row,"authority",500),srcHash);
                    if(docs.putIfAbsent(key,d)!=null)throw new IllegalArgumentException("Duplicate doc_key: "+key);
                } catch(Exception ex) {
                    issues.add(importIssue("documents.jsonl",i+1,row,"DOCUMENT_INVALID",ex.getMessage(),
                        "Correct the document title/category/source reference, then upload a corrected archive"));
                }
            }
            List<Item> items=new ArrayList<>();Set<String> keys=new HashSet<>();
            appendValidItems(ruleLines,"RULE",docs,keys,items,issues,"rules.jsonl");
            appendValidItems(segmentLines,"SEGMENT",docs,keys,items,issues,"legal_segments.jsonl");
            UUID batchId=UUID.randomUUID();
            transaction.executeWithoutResult(tx -> {
                jdbc.update("insert into knowledge_ai_import_batch(id,archive_name,archive_sha256,archive_format,schema_version,source_ai,instruction_version,source_mode,uploaded_by) values(?,?,?,?,?,?,?,?,?)",
                    batchId,filename,archiveHash,fmt,"sakhtyar-ai-knowledge-v1",provider,instruction,mode,actor);
                Map<String,UUID> documentIds=new HashMap<>();
                for(Doc d:docs.values()) {
                    UUID id=UUID.randomUUID();documentIds.put(d.key(),id);
                    jdbc.update("insert into knowledge_ai_import_document(id,batch_id,doc_key,title,category,source_reference,authority,source_sha256,suggested_category,classification_reason) values(?,?,?,?,?,?,?,?,?,?)",
                        id,batchId,d.key(),d.title(),d.category(),d.reference(),d.authority(),d.hash(),d.category(),
                        "Classification based on declared document category and KnowledgeAiPromptLibrary "+mode+" mode; expert verification required");
                }
                for(Item item:items) {
                    jdbc.update("insert into knowledge_ai_import_item(id,batch_id,document_id,item_key,record_type,segment_type,article_label,full_text,evidence_quote,page_from,page_to,content_sha256) values(?,?,?,?,?,?,?,?,?,?,?,?)",
                        UUID.randomUUID(),batchId,documentIds.get(item.doc()),item.key(),item.recordType(),item.segmentType(),item.label(),item.fullText(),item.evidence(),item.from(),item.to(),item.hash());
                }
                for(Map<String,Object> issue:issues) {
                    jdbc.update("insert into knowledge_ai_import_issue(batch_id,origin_file,line_number,doc_key,item_key,issue_code,explanation,suggestion,row_payload) values(?,?,?,?,?,?,?,?,?::jsonb)",
                        batchId,issue.get("file"),issue.get("line"),issue.get("doc_key"),issue.get("item_key"),issue.get("code"),issue.get("message"),issue.get("suggestion"),issue.get("payload"));
                }
            });
            return Map.of("id",batchId.toString(),"status",issues.isEmpty()?"AWAITING_REVIEW":"IMPORTED_WITH_ISSUES",
                "documents",docs.size(),"rules",items.stream().filter(i->"RULE".equals(i.recordType())).count(),
                "segments",items.stream().filter(i->"SEGMENT".equals(i.recordType())).count(),
                "issues",issues.size(),"sha256",archiveHash);
        } catch (IllegalArgumentException ex) { throw ex; }
        catch(Exception ex) { throw new IllegalArgumentException("Cannot parse AI knowledge archive: "+ex.getMessage(),ex); }
    }

    /** Parse JSONL independently; retain invalid lines as quarantined diagnostics. */
    private List<JsonNode> readJsonlWithIssues(byte[] bytes,String file,List<Map<String,Object>> problems) throws Exception {
        List<JsonNode> rows=new ArrayList<>();int lineNo=0;
        for(String line:decode(bytes).split("\\R",-1)) {
            lineNo++;if(line.isBlank())continue;
            try {JsonNode node=mapper.readTree(line);
                if(node==null || !node.isObject())throw new IllegalArgumentException("JSONL row must be an object");
                ((tools.jackson.databind.node.ObjectNode)node).put("__sakhtyar_source_line",lineNo);
                rows.add(node);
            }catch(Exception ex){problems.add(importIssue(file,lineNo,null,"INVALID_JSONL",
                 "Line "+lineNo+": "+ex.getMessage(),"Fix JSON syntax in this line and upload an updated ZIP"));}
            if(rows.size()+problems.size()>MAX_RECORDS)throw new IllegalArgumentException("More than 3000 rows");
        }
        return rows;
    }
    private static String safeIssueText(String s) { return s==null?null:s.replace(String.valueOf((char)0),"[NUL]"); }
    private Map<String,Object> importIssue(String file,int line,JsonNode row,String code,String message,String suggestion){
        Map<String,Object> m=new LinkedHashMap<>();m.put("file",file);m.put("line",row==null?line:row.path("__sakhtyar_source_line").asInt(line));
        m.put("doc_key",row==null?null:safeIssueText(row.path("doc_key").asText(null)));
        m.put("item_key",row==null?null:safeIssueText(row.path("item_key").asText(null)));
        m.put("code",code);m.put("message",safeIssueText(message==null?code:message));m.put("suggestion",safeIssueText(suggestion));
        m.put("payload",row==null?null:row.toString()
            .replace("\\u0000","[NUL]").replace(String.valueOf((char)0),"[NUL]"));return m;
    }
    private void appendValidItems(List<JsonNode> rows,String type,Map<String,Doc> docs,Set<String> keys,
                                  List<Item> out,List<Map<String,Object>> problems,String file){
        for(int i=0;i<rows.size();i++){
            JsonNode row=rows.get(i);String key=row.path("item_key").asText("");boolean existed=keys.contains(type+":"+key);
            try{readItems(List.of(row),type,docs,keys,out);}
            catch(Exception ex){if(!existed)keys.remove(type+":"+key);
                problems.add(importIssue(file,i+1,row,"ITEM_INVALID",ex.getMessage(),
                "Correct record type, evidence_quote, page number or document category, then re-import"));}
        }
    }
    private void readItems(List<JsonNode> rows,String type,Map<String,Doc> docs,Set<String> keys,List<Item> out) {
        for(JsonNode row:rows) {
            String key=req(row,"item_key",160), doc=req(row,"doc_key",120);
            if(!docs.containsKey(doc)) throw new IllegalArgumentException("Unknown doc_key: "+doc);
            if("RULE".equals(type) && !"LAW".equals(docs.get(doc).category()))
                throw new IllegalArgumentException("Legal rules can only refer to LAW documents: "+doc);
            if(!keys.add(type+":"+key)) throw new IllegalArgumentException("Duplicate item_key: "+key);
            String kind=req(row,"segment_type",20);
            if(!Set.of("ARTICLE","NOTE","CLAUSE","PASSAGE").contains(kind)) throw new IllegalArgumentException("Invalid segment_type: "+kind);
            String full=req(row,"full_text",20000), evidence=req(row,"evidence_quote",3000);
            if(full.length()<12 || evidence.length()<8 || !full.contains(evidence))
                throw new IllegalArgumentException("Evidence quote not found verbatim in full_text for "+key);
            int from=page(row,"page_from"),to=page(row,"page_to");
            if(to<from || to-from>100) throw new IllegalArgumentException("Invalid page range for "+key);
            String label=optional(row,"article_label",250);
            out.add(new Item(key,doc,type,kind,label,full,evidence,from,to,sha256(full.getBytes(StandardCharsets.UTF_8))));
        }
    }
    private int page(JsonNode n,String name) {
        JsonNode v=n.get(name);
        if(v==null || !v.isIntegralNumber() || v.asInt()<1 || v.asInt()>1_000_000) throw new IllegalArgumentException("Invalid "+name);
        return v.asInt();
    }
    private String req(JsonNode n,String name,int max) {
        String val=optional(n,name,max);
        if(val==null || val.isBlank()) throw new IllegalArgumentException("Missing "+name);
        return val;
    }
    private String optional(JsonNode n,String name,int max) {
        JsonNode v=n.get(name);
        if(v==null || v.isNull()) return null;
        if(!v.isTextual()) throw new IllegalArgumentException("Expected text at "+name);
        String s=v.asText().strip();
        if(s.indexOf((char)0)>=0)
            throw new IllegalArgumentException("Field "+name+" contains forbidden NUL character (U+0000); row quarantined");
        if(s.length()>max) throw new IllegalArgumentException("Field "+name+" exceeds "+max+" characters");
        return s;
    }
    private List<JsonNode> jsonl(byte[] bytes,String name) throws Exception {
        List<JsonNode> rows=new ArrayList<>(); int lineNo=0;
        for(String line:decode(bytes).split("\\R",-1)) {
            lineNo++; if(line.isBlank()) continue;
            JsonNode n=mapper.readTree(line);
            if(n==null || !n.isObject()) throw new IllegalArgumentException(name+" line "+lineNo+" must be an object");
            rows.add(n); if(rows.size()>MAX_RECORDS) throw new IllegalArgumentException("Too many records in "+name);
        }
        return rows;
    }
    private String decode(byte[] bytes) throws CharacterCodingException {
        String decoded=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        return decoded.startsWith("\uFEFF") ? decoded.substring(1) : decoded;
    }
    private Map<String,byte[]> unzip(byte[] bytes) throws Exception {
        if(bytes.length<4 || bytes[0]!=0x50 || bytes[1]!=0x4b) throw new IllegalArgumentException("Not a ZIP archive");
        Map<String,byte[]> files=new HashMap<>(); int total=0, entries=0;
        try(ZipInputStream zin=new ZipInputStream(new ByteArrayInputStream(bytes),StandardCharsets.UTF_8)) {
            ZipEntry e; while((e=zin.getNextEntry())!=null) {
                if(++entries>4 || e.isDirectory() || !REQUIRED.contains(e.getName()) || files.containsKey(e.getName()))
                    throw new IllegalArgumentException("Unexpected/duplicate archive entry: "+e.getName());
                ByteArrayOutputStream bos=new ByteArrayOutputStream();byte[] buf=new byte[8192];int read;
                while((read=zin.read(buf))!=-1) {
                    if(bos.size()+read>MAX_FILE || (total+=read)>MAX_TOTAL) throw new IllegalArgumentException("Archive decompression limit exceeded");
                    bos.write(buf,0,read);
                }
                files.put(e.getName(),bos.toByteArray());
            }
        }
        if(!files.keySet().containsAll(REQUIRED)) throw new IllegalArgumentException("Archive needs exactly four root files: "+REQUIRED);
        return files;
    }
    private Map<String,byte[]> unrar(byte[] bytes) throws Exception {
        // No archive extraction to disk: 7-Zip streams only each permitted basename to stdout.
        String executable=System.getenv("SAKHTYAR_7ZIP_EXE");
        if(executable==null || executable.isBlank()) {
            Path win=Path.of("C:/Program Files/7-Zip/7z.exe");
            executable=Files.isRegularFile(win)?win.toString():"7z";
        }
        if(bytes.length<7 || bytes[0]!=0x52 || bytes[1]!=0x61 || bytes[2]!=0x72 || bytes[3]!=0x21)
            throw new IllegalArgumentException("Not a valid RAR file");
        Path tmp=Files.createTempFile("sakhtyar-ai-import-", ".rar");
        try {
            Files.write(tmp,bytes);
            Map<String,byte[]> files=new HashMap<>();int total=0;
            for(String name:REQUIRED) {
                ProcessBuilder pb=new ProcessBuilder(executable,"e","-so","-bd","-y",tmp.toString(),name);
                pb.redirectError(ProcessBuilder.Redirect.DISCARD);
                Process process;
                try { process=pb.start(); }
                catch(IOException e) {throw new IllegalArgumentException("RAR requires local 7-Zip. Install 7-Zip or upload ZIP, or set SAKHTYAR_7ZIP_EXE.");}
                ExecutorService reader=Executors.newSingleThreadExecutor(r -> {
                    Thread t=new Thread(r,"sakhtyar-rar-stream");t.setDaemon(true);return t;
                });
                try {
                    Future<byte[]> future=reader.submit(() -> readLimited(process.getInputStream(),MAX_FILE));
                    byte[] data=future.get(30,TimeUnit.SECONDS);
                    if(!process.waitFor(5,TimeUnit.SECONDS) || process.exitValue()!=0 || data.length==0)
                        throw new IllegalArgumentException("RAR file missing or unreadable: "+name);
                    if((total+=data.length)>MAX_TOTAL) throw new IllegalArgumentException("RAR expands too much");
                    files.put(name,data);
                } finally {
                    if(process.isAlive()) process.destroyForcibly();
                    reader.shutdownNow();
                }
            }
            return files;
        } finally { Files.deleteIfExists(tmp); }
    }
    private byte[] readLimited(InputStream stream,int max) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
        while((n=stream.read(buf))!=-1) {
            if(out.size()+n>max) throw new IllegalArgumentException("RAR extracted entry too large");
            out.write(buf,0,n);
        }
        return out.toByteArray();
    }
    private String sha256(byte[] data) {
        try {byte[] bytes=MessageDigest.getInstance("SHA-256").digest(data);
            return HexFormat.of().formatHex(bytes);
        } catch(Exception e) {throw new IllegalStateException(e);}
    }

    public List<Map<String,Object>> batches() {
        return jdbc.queryForList("""
            select b.id::text as id,b.archive_name,b.archive_format,b.source_ai,b.schema_version,
              b.status,b.uploaded_by,b.created_at,
              (select count(*) from knowledge_ai_import_document d where d.batch_id=b.id) as documents,
              (select count(*) from knowledge_ai_import_item i where i.batch_id=b.id and i.record_type='RULE') as rules,
              (select count(*) from knowledge_ai_import_item i where i.batch_id=b.id and i.record_type='SEGMENT') as segments
            from knowledge_ai_import_batch b where b.archived_at is null order by b.created_at desc limit 50
            """);
    }
    public List<Map<String,Object>> documents(UUID batchId) {
        return jdbc.queryForList("""
            select id::text as id,doc_key,title,category,source_reference,authority,source_sha256
            from knowledge_ai_import_document where batch_id=? and archived_at is null order by doc_key limit 3000
            """,batchId);
    }
    public List<Map<String,Object>> items(UUID batchId,String type) {
        if(!Set.of("RULE","SEGMENT").contains(type)) throw new IllegalArgumentException("Invalid record type");
        return jdbc.queryForList("""
            select i.id::text as id,i.item_key,i.record_type,i.segment_type,i.article_label,
              i.full_text,i.evidence_quote,i.page_from,i.page_to,i.content_sha256,
              i.review_status,i.review_notes,i.reviewed_by,i.reviewed_at,
              d.doc_key,d.title as document_title,d.category,d.source_reference
            from knowledge_ai_import_item i join knowledge_ai_import_document d on d.id=i.document_id
            where i.batch_id=? and i.record_type=? and i.archived_at is null and d.archived_at is null order by d.doc_key,i.page_from,i.item_key limit 3000
            """,batchId,type);
    }
    public Map<String,Object> review(UUID batchId,UUID itemId,String decision,String notes,String actor) {
        if(!Set.of("APPROVED","REJECTED","PENDING_REVIEW").contains(decision)) throw new IllegalArgumentException("Invalid decision");
        if(notes!=null && notes.length()>2000) throw new IllegalArgumentException("Review notes too long");
        if("APPROVED".equals(decision) && (notes==null || notes.strip().length()<12))
            throw new IllegalArgumentException("برای تأیید باید حداقل ۱۲ نویسه درباره بررسی منبع و استناد ثبت شود.");
        transaction.executeWithoutResult(tx -> {
            int affected=jdbc.update("""
                update knowledge_ai_import_item set review_status=?,review_notes=?,reviewed_by=?,reviewed_at=now()
                where id=? and batch_id=?
                """,decision,notes,actor,itemId,batchId);
            if(affected!=1) throw new IllegalArgumentException("Item not found in this package");
            jdbc.update("insert into knowledge_ai_import_review_event(id,item_id,decision,notes,reviewer) values(?,?,?,?,?)",
                UUID.randomUUID(),itemId,decision,notes,actor);
            jdbc.update("""
                update knowledge_ai_import_batch b
                set status=case when exists(select 1 from knowledge_ai_import_item i
                                            where i.batch_id=b.id and i.review_status='PENDING_REVIEW')
                    then 'AWAITING_REVIEW' else 'REVIEWED' end
                where b.id=?
                """,batchId);
        });
        return Map.of("id",itemId.toString(),"status",decision,
            "publication","NOT_PUBLISHED; review is staged only");
    }
    /**
     * Transactional staging review. V53.2 extends the V53.1 rule-only API to
     * selected rules + legal segments, while preserving legacy scopes.
     * APPROVED requires category LAW; REJECTED can remove non-law candidates
     * from the review queue. No changes to canonical knowledge_rule/releases.
     */
    public Map<String,Object> bulkReview(UUID batchId, String scope, List<UUID> itemIds,
                                         String decision, String notes, Boolean acknowledged,
                                         Integer expectedCount, String actor) {
        if (!Set.of("SELECTED", "ALL_PENDING_RULES", "SELECTED_ITEMS", "ALL_PENDING_ITEMS").contains(scope))
            throw new IllegalArgumentException("دامنه بازبینی گروهی معتبر نیست.");
        if (!Set.of("APPROVED", "REJECTED").contains(decision))
            throw new IllegalArgumentException("تصمیم گروهی معتبر نیست.");
        if (!Boolean.TRUE.equals(acknowledged))
            throw new IllegalArgumentException("تأیید آگاهی از بازبینی گروهی الزامی است.");
        if (notes != null && notes.length() > 2000)
            throw new IllegalArgumentException("متن یادداشت بیشتر از ۲۰۰۰ نویسه است.");
        if ("APPROVED".equals(decision) && (notes == null || notes.strip().length() < 12))
            throw new IllegalArgumentException("برای تأیید گروهی حداقل ۱۲ نویسه توضیح درباره راستی‌آزمایی منبع لازم است.");
        if (expectedCount == null || expectedCount < 1 || expectedCount > MAX_RECORDS)
            throw new IllegalArgumentException("تعداد مورد انتظار بازبینی نامعتبر است.");
        boolean selectedScope = Set.of("SELECTED", "SELECTED_ITEMS").contains(scope);
        final LinkedHashSet<UUID> selected = new LinkedHashSet<>();
        if (selectedScope) {
            if (itemIds == null || itemIds.isEmpty() || itemIds.size() > MAX_RECORDS || itemIds.contains(null))
                throw new IllegalArgumentException("لطفاً موارد قابل بازبینی را انتخاب کن.");
            selected.addAll(itemIds);
            if (selected.size() != itemIds.size() || selected.size() != expectedCount)
                throw new IllegalArgumentException("انتخاب تکراری است یا تعداد موارد تغییر کرده است.");
        } else if (itemIds != null && !itemIds.isEmpty()) {
            throw new IllegalArgumentException("در حالت همه موارد، شناسه‌های جداگانه مجاز نیست.");
        }
        return transaction.execute(tx -> {
            // Consistent snapshot for all selected items, with serialization against other bulk reviews.
            List<Map<String,Object>> batches = jdbc.queryForList(
                "select id from knowledge_ai_import_batch where id=? for update", batchId);
            if (batches.size()!=1) throw new IllegalArgumentException("بسته دانش پیدا نشد.");
            List<Map<String,Object>> rows = jdbc.queryForList("""
                select i.id::text as id, i.review_status, i.record_type, d.category
                from knowledge_ai_import_item i
                join knowledge_ai_import_document d on d.id=i.document_id
                where i.batch_id=?
                order by i.id
                for update of i
                """, batchId);
            LinkedHashSet<UUID> accepted = new LinkedHashSet<>();
            for (Map<String,Object> row: rows) {
                UUID id = UUID.fromString((String)row.get("id"));
                String status = (String)row.get("review_status");
                String category = (String)row.get("category");
                String recordType = (String)row.get("record_type");
                boolean pending = "PENDING_REVIEW".equals(status);
                boolean legal = "LAW".equals(category);
                boolean isRule = "RULE".equals(recordType);
                if ("ALL_PENDING_RULES".equals(scope)) {
                    if (pending && legal && isRule) accepted.add(id);
                } else if ("ALL_PENDING_ITEMS".equals(scope)) {
                    if (pending && ("REJECTED".equals(decision) || legal)) accepted.add(id);
                } else if (selected.contains(id)) {
                    if (!pending)
                        throw new IllegalArgumentException("بعضی موارد قبلاً بررسی شده‌اند؛ فهرست را تازه‌سازی کن.");
                    if ("SELECTED".equals(scope) && !isRule)
                        throw new IllegalArgumentException("حالت قدیمی انتخاب قانون فقط رکوردهای RULE را می‌پذیرد.");
                    if (("APPROVED".equals(decision) || "SELECTED".equals(scope)) && !legal)
                        throw new IllegalArgumentException("تأیید گروهی فقط برای اسناد دسته قانون مجاز است.");
                    accepted.add(id);
                }
            }
            if (accepted.size()!=expectedCount || (selectedScope && !accepted.equals(selected)))
                throw new IllegalArgumentException("تعداد یا وضعیت موارد تغییر کرده است؛ فهرست را تازه‌سازی کن.");
            String auditNote = "[بازبینی گروهی - " + scope + "] " + (notes==null ? "" : notes.strip());
            for (UUID itemId: accepted) {
                int changed=jdbc.update("""
                    update knowledge_ai_import_item
                    set review_status=?,review_notes=?,reviewed_by=?,reviewed_at=now()
                    where id=? and batch_id=? and review_status='PENDING_REVIEW'
                    """, decision, auditNote, actor, itemId, batchId);
                if (changed!=1) throw new IllegalStateException("ثبت تصمیم گروهی ناقص ماند؛ تراکنش بازگردانده شد.");
                jdbc.update("""
                    insert into knowledge_ai_import_review_event(id,item_id,decision,notes,reviewer)
                    values(?,?,?,?,?)
                    """, UUID.randomUUID(),itemId,decision,auditNote,actor);
            }
            jdbc.update("""
                update knowledge_ai_import_batch b
                set status=case when exists(select 1 from knowledge_ai_import_item i
                    where i.batch_id=b.id and i.review_status='PENDING_REVIEW')
                    then 'AWAITING_REVIEW' else 'REVIEWED' end
                where b.id=?
                """, batchId);
            return Map.<String,Object>of("updated",accepted.size(),"decision",decision,
                "publication","NOT_PUBLISHED; staging review only");
        });
    }

    public List<Map<String,Object>> reviewEvents(UUID batchId,UUID itemId) {
        return jdbc.queryForList("""
            select e.id::text as id,e.decision,e.notes,e.reviewer,e.created_at
            from knowledge_ai_import_review_event e
            join knowledge_ai_import_item i on i.id=e.item_id
            where i.batch_id=? and i.id=? order by e.created_at desc limit 100
            """,batchId,itemId);
    }

}
