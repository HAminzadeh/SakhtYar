package com.sakhtyar.knowledge.application;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.sakhtyar.document.infrastructure.StorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class KnowledgeDatasetPackageService {

    private static final long MAX_ZIP_BYTES = 512L * 1024 * 1024;
    private static final long MAX_EXTRACTED_BYTES = 2L * 1024 * 1024 * 1024;
    private static final int MAX_ZIP_ENTRIES = 100_000;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper mapper;
    private final MinioClient minio;
    private final StorageProperties storage;

    public KnowledgeDatasetPackageService(
            JdbcTemplate jdbc,
            TransactionTemplate tx,
            ObjectMapper mapper,
            MinioClient minio,
            StorageProperties storage
    ) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.mapper = mapper;
        this.minio = minio;
        this.storage = storage;
        ensureBucket();
    }

    public Map<String,Object> upload(MultipartFile file, String actor) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Dataset package is empty.");
        if (file.getSize() > MAX_ZIP_BYTES) throw new IllegalArgumentException("Dataset package exceeds 512 MB.");
        String name = sanitize(file.getOriginalFilename());
        if (!name.toLowerCase(Locale.ROOT).endsWith(".zip")) throw new IllegalArgumentException("Only .zip dataset packages are accepted.");

        UUID id = UUID.randomUUID();
        try {
            String sha = sha256(file);
            String key = "knowledge/datasets/packages/" + id + "/" + name;
            try (InputStream in = file.getInputStream()) {
                minio.putObject(PutObjectArgs.builder()
                        .bucket(storage.bucket()).object(key)
                        .stream(in, file.getSize(), -1L)
                        .contentType(Optional.ofNullable(file.getContentType()).orElse("application/zip"))
                        .build());
            }

            jdbc.update("""
                insert into knowledge_dataset_package(
                    id, original_filename, object_key, sha256, size_bytes, content_type,
                    status, uploaded_by, uploaded_at, updated_at
                ) values(?,?,?,?,?,?,'UPLOADED',?,now(),now())
                """, id, name, key, sha, file.getSize(), file.getContentType(), actor);
            event(id, "UPLOADED", "UPLOADED", "Dataset package uploaded and stored in MinIO.", Map.of("sha256",sha), actor);
            return packageRow(id);
        } catch (Exception e) {
            throw new IllegalStateException("Dataset package upload failed.", e);
        }
    }

    public List<Map<String,Object>> list() {
        return jdbc.query("""
            select id,original_filename,sha256,size_bytes,content_type,dataset_version,status,
                   manifest,validation_report,dry_run_report,imported_dataset_id,error_message,
                   uploaded_by,uploaded_at,validated_at,import_started_at,imported_at,updated_at
            from knowledge_dataset_package
            order by uploaded_at desc
            limit 100
            """, (rs,n) -> {
                LinkedHashMap<String,Object> r = new LinkedHashMap<>();
                r.put("id", rs.getObject("id"));
                r.put("originalFilename", rs.getString("original_filename"));
                r.put("sha256", rs.getString("sha256"));
                r.put("sizeBytes", rs.getLong("size_bytes"));
                r.put("contentType", rs.getString("content_type"));
                r.put("datasetVersion", rs.getString("dataset_version"));
                r.put("status", rs.getString("status"));
                r.put("manifest", parseJson(rs.getString("manifest")));
                r.put("validationReport", parseJson(rs.getString("validation_report")));
                r.put("dryRunReport", parseJson(rs.getString("dry_run_report")));
                r.put("importedDatasetId", rs.getObject("imported_dataset_id"));
                r.put("errorMessage", rs.getString("error_message"));
                r.put("uploadedBy", rs.getString("uploaded_by"));
                r.put("uploadedAt", instant(rs.getTimestamp("uploaded_at")));
                r.put("validatedAt", instant(rs.getTimestamp("validated_at")));
                r.put("importStartedAt", instant(rs.getTimestamp("import_started_at")));
                r.put("importedAt", instant(rs.getTimestamp("imported_at")));
                r.put("updatedAt", instant(rs.getTimestamp("updated_at")));
                return r;
            });
    }

    public Map<String,Object> validate(UUID packageId, String actor) {
        try (Workspace ws = workspace(packageId)) {
            JsonNode manifest = readManifest(ws.root());
            verifyArtifacts(ws.root(), manifest);

            String version = requiredText(manifest, "dataset_version");
            Map<String,Object> report = new LinkedHashMap<>();
            report.put("valid", true);
            report.put("datasetVersion", version);
            report.put("artifactCount", manifest.path("artifacts").size());
            report.put("statistics", mapper.convertValue(manifest.path("statistics"), Map.class));
            report.put("activationPolicy", manifest.path("activation_policy").asText("MANUAL_ONLY"));

            jdbc.update("""
                update knowledge_dataset_package
                set dataset_version=?,status='VALIDATED',manifest=?::jsonb,
                    validation_report=?::jsonb,validated_at=now(),error_message=null,updated_at=now()
                where id=?
                """, version, manifest.toString(), json(report), packageId);
            event(packageId,"VALIDATED","VALIDATED","Checksums and package structure validated.",report,actor);
            return report;
        } catch (Exception e) {
            fail(packageId,"VALIDATION_FAILED",e,actor);
            throw new IllegalStateException("Dataset validation failed: " + e.getMessage(), e);
        }
    }

    public Map<String,Object> dryRun(UUID packageId, String actor) {
        try (Workspace ws = workspace(packageId)) {
            JsonNode manifest = readManifest(ws.root());
            verifyArtifacts(ws.root(),manifest);

            Map<String,Object> result = new LinkedHashMap<>();
            result.put("datasetVersion", requiredText(manifest,"dataset_version"));
            result.put("sourceCommit", manifest.path("source_commit").asText(null));
            result.put("statistics", mapper.convertValue(manifest.path("statistics"), Map.class));
            result.put("documents", countLines(ws.root().resolve("documents.jsonl")));
            result.put("chunks", countLines(ws.root().resolve("chunks.jsonl")));
            result.put("knowledgeCandidates", countLines(ws.root().resolve("knowledge-candidates.jsonl")));
            result.put("ruleCandidates", countLines(ws.root().resolve("rule-candidates.jsonl")));
            result.put("conflicts", countLines(ws.root().resolve("conflicts.jsonl")));
            result.put("targetStatus","IMPORTED");
            result.put("autoActivate",false);

            jdbc.update("""
                update knowledge_dataset_package set dry_run_report=?::jsonb,status='DRY_RUN_OK',
                    error_message=null,updated_at=now() where id=?
                """, json(result), packageId);
            event(packageId,"DRY_RUN","DRY_RUN_OK","Dry run completed without database mutation.",result,actor);
            return result;
        } catch (Exception e) {
            fail(packageId,"DRY_RUN_FAILED",e,actor);
            throw new IllegalStateException("Dry run failed: " + e.getMessage(), e);
        }
    }

    public Map<String,Object> importAsync(UUID packageId, String actor) {
        String current = jdbc.queryForObject("select status from knowledge_dataset_package where id=?", String.class, packageId);
        if (!Set.of("VALIDATED","DRY_RUN_OK","IMPORTED","IMPORT_FAILED").contains(current)) {
            throw new IllegalStateException("Validate or Dry Run the package before import.");
        }
        jdbc.update("""
            update knowledge_dataset_package
            set status='IMPORT_QUEUED',import_started_at=now(),error_message=null,updated_at=now()
            where id=?
            """,packageId);
        event(packageId,"IMPORT_QUEUED","IMPORT_QUEUED","Import queued.",Map.of(),actor);

        Thread.startVirtualThread(() -> {
            try {
                jdbc.update("update knowledge_dataset_package set status='IMPORTING',updated_at=now() where id=?",packageId);
                event(packageId,"IMPORT_STARTED","IMPORTING","Prepared dataset import started.",Map.of(),actor);
                UUID datasetId = tx.execute(status -> doImport(packageId));
                jdbc.update("""
                    update knowledge_dataset_package
                    set status='IMPORTED',imported_dataset_id=?,imported_at=now(),updated_at=now()
                    where id=?
                    """,datasetId,packageId);
                event(packageId,"IMPORTED","IMPORTED","Dataset imported successfully. Activation remains manual.",
                        Map.of("datasetId",datasetId.toString()),actor);
            } catch (Exception e) {
                fail(packageId,"IMPORT_FAILED",e,actor);
            }
        });

        return Map.of("packageId",packageId,"status","IMPORT_QUEUED");
    }

    public DownloadedPackage download(UUID packageId) {
        return jdbc.query("""
            select original_filename,object_key,size_bytes,content_type
            from knowledge_dataset_package where id=?
            """, rs -> {
                if (!rs.next()) throw new IllegalArgumentException("Package not found.");
                try {
                    InputStream stream = minio.getObject(GetObjectArgs.builder()
                            .bucket(storage.bucket()).object(rs.getString("object_key")).build());
                    return new DownloadedPackage(
                            rs.getString("original_filename"),
                            Optional.ofNullable(rs.getString("content_type")).orElse("application/zip"),
                            rs.getLong("size_bytes"),
                            new InputStreamResource(stream)
                    );
                } catch(Exception e) {
                    throw new IllegalStateException("Package download failed.",e);
                }
            },packageId);
    }

    public List<Map<String,Object>> events(UUID packageId) {
        return jdbc.query("""
            select id,event_type,status,message,details,actor,created_at
            from knowledge_dataset_package_event where package_id=?
            order by created_at desc
            """,(rs,n)->Map.of(
                    "id",rs.getLong("id"),
                    "eventType",rs.getString("event_type"),
                    "status",Optional.ofNullable(rs.getString("status")).orElse(""),
                    "message",Optional.ofNullable(rs.getString("message")).orElse(""),
                    "details",parseJson(rs.getString("details")),
                    "actor",Optional.ofNullable(rs.getString("actor")).orElse(""),
                    "createdAt",instant(rs.getTimestamp("created_at"))
            ),packageId);
    }

    private UUID doImport(UUID packageId) {
        try (Workspace ws = workspace(packageId)) {
            JsonNode manifest = readManifest(ws.root());
            verifyArtifacts(ws.root(),manifest);
            String version = requiredText(manifest,"dataset_version");

            UUID datasetId = jdbc.query("""
                select id,status from knowledge_dataset where dataset_version=?
                """, rs -> {
                    if (!rs.next()) return null;
                    if ("ACTIVE".equals(rs.getString("status"))) {
                        throw new IllegalStateException("Refusing to overwrite ACTIVE dataset " + version);
                    }
                    return (UUID)rs.getObject("id");
                },version);

            if (datasetId == null) {
                datasetId = UUID.randomUUID();
                jdbc.update("""
                    insert into knowledge_dataset(
                        id,dataset_version,taxonomy_version,schema_version,source_commit,baseline_commit,
                        extraction_version,embedding_model,embedding_version,status,manifest,statistics,generated_at
                    ) values(?,?,?,?,?,?,?,?,?,'PREPARED',?::jsonb,?::jsonb,now())
                    """,
                    datasetId,version,text(manifest,"taxonomy_version"),text(manifest,"schema_version"),
                    text(manifest,"source_commit"),text(manifest,"baseline_commit"),
                    text(manifest,"extraction_version"),text(manifest,"embedding_model"),
                    text(manifest,"embedding_version"),manifest.toString(),manifest.path("statistics").toString());
            } else {
                deleteDatasetContent(datasetId);
                jdbc.update("""
                    update knowledge_dataset set status='PREPARED',manifest=?::jsonb,statistics=?::jsonb,
                        imported_at=null,activated_at=null where id=?
                    """,manifest.toString(),manifest.path("statistics").toString(),datasetId);
            }

            final UUID targetDatasetId = datasetId;
            Map<String,String> embeddings = loadEmbeddings(ws.root().resolve("embeddings.jsonl"));

            forEachJson(ws.root().resolve("documents.jsonl"), n -> jdbc.update("""
                insert into knowledge_document(
                    id,dataset_id,document_key,title,canonical_path,filename,sha256,extension,size_bytes,
                    page_count,text_chars,domain,classification_confidence,authority,trust,jurisdiction,
                    extraction_quality,summary_short,summary_detailed,topics,entities,agents,review_status,
                    security_classification,source_representation,text_hash,metadata
                ) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?::jsonb,?::jsonb,?::jsonb,?,?,?, ?,?::jsonb)
                """,
                uuid(n,"id"),targetDatasetId,text(n,"document_key"),text(n,"title"),text(n,"canonical_path"),
                text(n,"filename"),text(n,"sha256"),text(n,"extension"),longOrNull(n,"size_bytes"),
                intOrNull(n,"page_count"),longOrNull(n,"text_chars"),text(n,"domain"),doubleOrNull(n,"classification_confidence"),
                text(n,"authority"),text(n,"trust"),node(n,"jurisdiction"),
                text(n,"extraction_quality"),text(n,"summary_short"),text(n,"summary_detailed"),
                node(n,"topics"),node(n,"entities"),node(n,"agents"),text(n,"review_status"),
                text(n,"security_classification"),text(n,"source_representation"),text(n,"text_hash"),
                "{\"manual_attention\":"+node(n,"manual_attention")+",\"classification_rank\":"+node(n,"classification_rank")+"}"
            ));

            forEachJson(ws.root().resolve("source-locations.jsonl"), n -> jdbc.update("""
                insert into knowledge_document_source_location(id,dataset_id,document_id,path,is_canonical,source_folder,source_type)
                values(?,?,?,?,?,?,?)
                """,uuid(n,"id"),targetDatasetId,uuid(n,"document_id"),text(n,"path"),n.path("is_canonical").asBoolean(),
                text(n,"source_folder"),text(n,"source_type")));

            forEachJson(ws.root().resolve("chunks.jsonl"), n -> {
                String vector = embeddings.get(text(n,"id"));
                jdbc.update("""
                    insert into knowledge_chunk(
                        id,dataset_id,document_id,chunk_index,page_from,page_to,normalized_text,text_hash,summary,
                        keywords,topics,entities,legal_refs,questions_answered,confidence,provenance,embedding
                    ) values(?,?,?,?,?,?,?,?,?,?::jsonb,?::jsonb,?::jsonb,?::jsonb,?::jsonb,?,?::jsonb,?::vector)
                    """,uuid(n,"id"),targetDatasetId,uuid(n,"document_id"),n.path("chunk_index").asInt(),
                    intOrNull(n,"page_from"),intOrNull(n,"page_to"),text(n,"normalized_text"),text(n,"text_hash"),
                    text(n,"summary"),node(n,"keywords"),node(n,"topics"),node(n,"entities"),node(n,"legal_refs"),
                    node(n,"questions_answered"),doubleOrNull(n,"confidence"),node(n,"provenance"),vector);
            });

            forEachJson(ws.root().resolve("evidence.jsonl"), n -> jdbc.update("""
                insert into knowledge_evidence(id,dataset_id,document_id,chunk_id,evidence_type,page_number,quote_text,confidence,provenance)
                values(?,?,?,?,?,?,?,?,?::jsonb)
                """,uuid(n,"id"),targetDatasetId,uuid(n,"document_id"),uuid(n,"chunk_id"),text(n,"evidence_type"),
                intOrNull(n,"page_number"),text(n,"quote_text"),doubleOrNull(n,"confidence"),node(n,"provenance")));

            forEachJson(ws.root().resolve("knowledge-candidates.jsonl"), n -> jdbc.update("""
                insert into knowledge_assertion_candidate(
                    id,dataset_id,document_id,evidence_id,candidate_type,statement,statement_hash,domain,authority,
                    trust,confidence,status,topics,provenance
                ) values(?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?::jsonb)
                """,uuid(n,"id"),targetDatasetId,uuid(n,"document_id"),uuid(n,"evidence_id"),text(n,"candidate_type"),
                text(n,"statement"),text(n,"statement_hash"),text(n,"domain"),text(n,"authority"),text(n,"trust"),
                doubleOrNull(n,"confidence"),text(n,"status"),node(n,"topics"),node(n,"provenance")));

            forEachJson(ws.root().resolve("rule-candidates.jsonl"), n -> jdbc.update("""
                insert into knowledge_rule_candidate(
                    id,dataset_id,document_id,evidence_id,statement,statement_hash,domain,rule_type,legal_refs,
                    authority,trust,jurisdiction,effective_from,effective_to,confidence,status,bulk_approvable,provenance
                ) values(?,?,?,?,?,?,?,?,?::jsonb,?,?,?::jsonb,null,null,?,?,?,?::jsonb)
                """,uuid(n,"id"),targetDatasetId,uuid(n,"document_id"),uuid(n,"evidence_id"),text(n,"statement"),
                text(n,"statement_hash"),text(n,"domain"),text(n,"rule_type"),node(n,"legal_refs"),text(n,"authority"),
                text(n,"trust"),node(n,"jurisdiction"),doubleOrNull(n,"confidence"),text(n,"status"),
                n.path("bulk_approvable").asBoolean(false),node(n,"provenance")));

            forEachJson(ws.root().resolve("relations.jsonl"), n -> jdbc.update("""
                insert into knowledge_document_relation(id,dataset_id,from_document_id,to_document_id,relation_type,confidence)
                values(?,?,?,?,?,?)
                """,uuid(n,"id"),targetDatasetId,uuid(n,"from_document_id"),uuid(n,"to_document_id"),
                text(n,"relation_type"),doubleOrNull(n,"confidence")));

            jdbc.update("""
                insert into knowledge_embedding_model(dataset_id,model_code,dimension,metadata)
                values(?,?,?,?::jsonb)
                on conflict(dataset_id) do update set model_code=excluded.model_code,
                    dimension=excluded.dimension,metadata=excluded.metadata
                """,targetDatasetId,text(manifest,"embedding_model"),manifest.path("embedding_dimension").asInt(),
                "{\"embedding_type\":\""+text(manifest,"embedding_type")+"\"}");

            importGovernance(ws.root(), targetDatasetId);

            jdbc.update("update knowledge_dataset set status='IMPORTED',imported_at=now() where id=?",targetDatasetId);
            return targetDatasetId;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void importGovernance(Path root, UUID datasetId) {
        forEachJson(root.resolve("conflicts.jsonl"), n -> jdbc.update("""
            insert into knowledge_conflict(
                id,dataset_id,conflict_key,conflict_type,severity,status,left_evidence,right_evidence,
                suggested_resolution,created_at
            ) values(?,?,?,?,?,?,?::jsonb,?::jsonb,?::jsonb,now())
            on conflict(id) do nothing
            """,uuid(n,"id"),datasetId,text(n,"id"),text(n,"conflict_type"),text(n,"severity"),text(n,"status"),
            "{\"ruleCandidateId\":\""+text(n,"left_rule_candidate_id")+"\"}",
            "{\"ruleCandidateId\":\""+text(n,"right_rule_candidate_id")+"\"}",
            "{\"note\":"+jsonString(text(n,"note"))+"}"));

        forEachJson(root.resolve("golden-questions.jsonl"), n -> jdbc.update("""
            insert into knowledge_golden_question(
                code,question_fa,question_en,expected_domains,expected_source_keys,expected_concepts,minimum_score,enabled
            ) values(?,?,?,?::jsonb,?::jsonb,'[]'::jsonb,?,?)
            on conflict(code) do update set question_fa=excluded.question_fa,
                expected_domains=excluded.expected_domains,expected_source_keys=excluded.expected_source_keys,
                minimum_score=excluded.minimum_score,enabled=excluded.enabled,updated_at=now()
            """,text(n,"code"),text(n,"question_fa"),text(n,"question_en"),node(n,"expected_domains"),
            node(n,"expected_document_ids"),doubleOrNull(n,"minimum_score"),n.path("enabled").asBoolean(true)));

        forEachJson(root.resolve("search-regression-cases.jsonl"), n -> jdbc.update("""
            insert into knowledge_search_regression_case(code,query_text,locale,expected_keys,filters,enabled)
            values(?,?,?,?::jsonb,?::jsonb,?)
            on conflict(code) do update set query_text=excluded.query_text,expected_keys=excluded.expected_keys,
                filters=excluded.filters,enabled=excluded.enabled
            """,text(n,"code"),text(n,"query_text"),text(n,"locale"),node(n,"expected_document_ids"),
            node(n,"filters"),n.path("enabled").asBoolean(true)));

        forEachJson(root.resolve("impact-analysis.jsonl"), n -> jdbc.update("""
            insert into knowledge_impact_analysis(
                id,change_type,changed_object_type,changed_object_key,impacted_agents,
                impacted_rules,impacted_terms,impacted_calculations,details
            ) values(?,?,?,?,?::jsonb,?::jsonb,?::jsonb,?::jsonb,?::jsonb)
            on conflict(id) do nothing
            """,uuid(n,"id"),text(n,"change_type"),text(n,"changed_object_type"),text(n,"changed_object_key"),
            node(n,"impacted_agents"),node(n,"impacted_rules"),node(n,"impacted_terms"),
            node(n,"impacted_calculations"),node(n,"details")));
    }

    private void deleteDatasetContent(UUID datasetId) {
        String[] tables = {
                "knowledge_document_relation","knowledge_rule_candidate","knowledge_assertion_candidate",
                "knowledge_evidence","knowledge_chunk","knowledge_document_source_location",
                "knowledge_document","knowledge_embedding_model"
        };
        for (String table : tables) jdbc.update("delete from " + table + " where dataset_id=?",datasetId);
        jdbc.update("delete from knowledge_conflict where dataset_id=?",datasetId);
    }

    private Workspace workspace(UUID packageId) throws IOException {
        Map<String,Object> row = jdbc.query("""
            select object_key,original_filename from knowledge_dataset_package where id=?
            """, rs -> {
                if (!rs.next()) throw new IllegalArgumentException("Package not found.");
                return Map.of("key",rs.getString(1),"name",rs.getString(2));
            },packageId);

        Path temp = Files.createTempDirectory("sakhtyar-kb-package-");
        Path zip = temp.resolve("package.zip");
        try (InputStream in = minio.getObject(GetObjectArgs.builder()
                .bucket(storage.bucket()).object((String)row.get("key")).build())) {
            Files.copy(in,zip,StandardCopyOption.REPLACE_EXISTING);
        } catch(Exception e) {
            throw new IOException("Could not retrieve package from MinIO.",e);
        }
        Path extracted = temp.resolve("extracted");
        Files.createDirectories(extracted);
        safeUnzip(zip,extracted);

        Path root = locateManifestRoot(extracted);
        return new Workspace(temp,root);
    }

    private Path locateManifestRoot(Path extracted) throws IOException {
        if (Files.exists(extracted.resolve("manifest.json"))) return extracted;
        try (var walk = Files.walk(extracted,3)) {
            return walk.filter(p -> p.getFileName().toString().equals("manifest.json"))
                    .map(Path::getParent)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("manifest.json not found in package."));
        }
    }

    private void safeUnzip(Path zip, Path target) throws IOException {
        long total = 0; int count = 0;
        try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry e;
            while ((e=zin.getNextEntry())!=null) {
                if (++count > MAX_ZIP_ENTRIES) throw new IllegalArgumentException("Too many ZIP entries.");
                Path out = target.resolve(e.getName()).normalize();
                if (!out.startsWith(target)) throw new IllegalArgumentException("Unsafe ZIP path.");
                if (e.isDirectory()) { Files.createDirectories(out); continue; }
                Files.createDirectories(out.getParent());
                try (OutputStream os = Files.newOutputStream(out)) {
                    byte[] b = new byte[8192]; int n;
                    while((n=zin.read(b))!=-1) {
                        total += n;
                        if (total > MAX_EXTRACTED_BYTES) throw new IllegalArgumentException("Extracted package exceeds 2 GB.");
                        os.write(b,0,n);
                    }
                }
            }
        }
    }

    private JsonNode readManifest(Path root) throws IOException {
        return mapper.readTree(root.resolve("manifest.json").toFile());
    }

    private void verifyArtifacts(Path root, JsonNode manifest) throws Exception {
        if (!manifest.hasNonNull("dataset_version")) throw new IllegalArgumentException("dataset_version missing.");
        JsonNode artifacts = manifest.path("artifacts");
        if (!artifacts.isArray()) throw new IllegalArgumentException("artifacts array missing.");
        for (JsonNode a : artifacts) {
            String rel = a.path("path").asText();
            String expected = a.path("sha256").asText();
            Path file = root.resolve(rel).normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) throw new IllegalArgumentException("Missing artifact: " + rel);
            String actual = sha256(file);
            if (!actual.equalsIgnoreCase(expected)) throw new IllegalArgumentException("Checksum mismatch: " + rel);
        }
    }

    private Map<String,String> loadEmbeddings(Path p) {
        Map<String,String> out = new HashMap<>();
        forEachJson(p,n -> {
            StringBuilder sb = new StringBuilder("[");
            JsonNode arr = n.path("vector");
            for (int i=0;i<arr.size();i++) {
                if(i>0) sb.append(',');
                sb.append(arr.get(i).asDouble());
            }
            sb.append(']');
            out.put(text(n,"chunk_id"),sb.toString());
        });
        return out;
    }

    private void forEachJson(Path p, java.util.function.Consumer<JsonNode> consumer) {
        if (!Files.exists(p)) return;
        long lineNo = 0;
        try (BufferedReader r = Files.newBufferedReader(p,StandardCharsets.UTF_8)) {
            String line;
            while((line=r.readLine())!=null) {
                lineNo++;
                if(line.isBlank()) continue;
                try {
                    consumer.accept(mapper.readTree(line));
                } catch(Exception e) {
                    String root = e.getMessage();
                    Throwable cause = e.getCause();
                    while (cause != null) {
                        if (cause.getMessage() != null && !cause.getMessage().isBlank()) {
                            root = cause.getMessage();
                        }
                        cause = cause.getCause();
                    }
                    throw new IllegalStateException(
                            "Failed importing " + p.getFileName()
                                    + " at line " + lineNo
                                    + ": " + root,
                            e
                    );
                }
            }
        } catch(IllegalStateException e) {
            throw e;
        } catch(Exception e) {
            throw new IllegalStateException(
                    "Could not read " + p.getFileName()
                            + " at line " + lineNo
                            + ": " + e.getMessage(),
                    e
            );
        }
    }

    private long countLines(Path p) throws IOException {
        try (var lines = Files.lines(p,StandardCharsets.UTF_8)) { return lines.filter(x -> !x.isBlank()).count(); }
    }

    private Map<String,Object> packageRow(UUID id) {
        return list().stream().filter(x -> id.toString().equals(String.valueOf(x.get("id")))).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Package not found."));
    }

    private void event(UUID id,String type,String status,String message,Map<String,?> details,String actor) {
        jdbc.update("""
            insert into knowledge_dataset_package_event(package_id,event_type,status,message,details,actor)
            values(?,?,?,?,?::jsonb,?)
            """,id,type,status,message,json(details),actor);
    }

    private void fail(UUID id,String status,Exception e,String actor) {
        jdbc.update("""
            update knowledge_dataset_package set status=?,error_message=?,updated_at=now() where id=?
            """,status,limit(e.getMessage(),4000),id);
        event(id,status,status,limit(e.getMessage(),4000),Map.of(),actor);
    }

    private void ensureBucket() {
        try {
            boolean exists = minio.bucketExists(BucketExistsArgs.builder().bucket(storage.bucket()).build());
            if (!exists) minio.makeBucket(MakeBucketArgs.builder().bucket(storage.bucket()).build());
        } catch(Exception e) { throw new IllegalStateException("Could not initialize MinIO bucket.",e); }
    }

    private String sha256(MultipartFile file) throws Exception {
        MessageDigest d=MessageDigest.getInstance("SHA-256");
        try(InputStream in=file.getInputStream()) { byte[] b=new byte[8192]; int n; while((n=in.read(b))!=-1)d.update(b,0,n); }
        return HexFormat.of().formatHex(d.digest());
    }
    private String sha256(Path p) throws Exception {
        MessageDigest d=MessageDigest.getInstance("SHA-256");
        try(InputStream in=Files.newInputStream(p)) { byte[] b=new byte[8192]; int n; while((n=in.read(b))!=-1)d.update(b,0,n); }
        return HexFormat.of().formatHex(d.digest());
    }

    private String sanitize(String s) {
        if (s==null || s.isBlank()) return "knowledge-dataset.zip";
        return s.replace("\\","_").replace("/","_").replace("\0","");
    }
    private String json(Object o) { try { return mapper.writeValueAsString(o); } catch(Exception e) { throw new IllegalStateException(e); } }
    private Object parseJson(String s) { try { return s==null?Map.of():mapper.readValue(s,Object.class); } catch(Exception e) { return Map.of(); } }
    private static String requiredText(JsonNode n,String f) { String v=text(n,f); if(v==null||v.isBlank())throw new IllegalArgumentException(f+" missing."); return v; }
    private static String text(JsonNode n,String f) { JsonNode v=n.get(f); return v==null||v.isNull()?null:v.asText(); }
    private static String node(JsonNode n,String f) { JsonNode v=n.get(f); return v==null||v.isNull()?(f.endsWith("s")?"[]":"{}"):v.toString(); }
    private static UUID uuid(JsonNode n,String f) {
        String value = requiredText(n,f);
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            // Prepared datasets intentionally use stable external IDs such as
            // doc_*, chunk_*, evidence_* and rule_*. Convert them to a stable
            // UUID namespace so every reference maps to the same DB key.
            return UUID.nameUUIDFromBytes(
                    ("sakhtyar-knowledge:" + value).getBytes(StandardCharsets.UTF_8)
            );
        }
    }
    private static Integer intOrNull(JsonNode n,String f) { JsonNode v=n.get(f); return v==null||v.isNull()?null:v.asInt(); }
    private static Long longOrNull(JsonNode n,String f) { JsonNode v=n.get(f); return v==null||v.isNull()?null:v.asLong(); }
    private static Double doubleOrNull(JsonNode n,String f) { JsonNode v=n.get(f); return v==null||v.isNull()?null:v.asDouble(); }
    private static Instant instant(java.sql.Timestamp t) { return t==null?null:t.toInstant(); }
    private static String limit(String s,int n) { if(s==null)return ""; return s.length()<=n?s:s.substring(0,n); }
    private static String jsonString(String s) { if(s==null)return "null"; return "\"" + s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n") + "\""; }

    public record DownloadedPackage(String filename,String contentType,long sizeBytes,InputStreamResource resource) {}

    private record Workspace(Path temp, Path root) implements AutoCloseable {
        public void close() {
            try {
                if (Files.exists(temp)) {
                    try (var w = Files.walk(temp)) {
                        w.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch(Exception ignored) {} });
                    }
                }
            } catch(Exception ignored) {}
        }
    }
}