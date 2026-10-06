package com.sakhtyar.knowledge.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

@Service
public class KnowledgeFinalPipelineService {
    private final JdbcTemplate jdbc;
    private final Path repo;
    private final Path python;
    private final Path processor;
    private static final Pattern NUMBER = Pattern.compile("(?<!\\d)(\\d+(?:[\\.,]\\d+)?)(?!\\d)");

    public KnowledgeFinalPipelineService(JdbcTemplate jdbc, @Value("${sakhtyar.repo-root:${user.dir}}") String root) {
        this.jdbc = jdbc;
        this.repo = findRepo(Path.of(root));
        Path venv = repo.resolve(".local/venv-persian-intelligence/Scripts/python.exe");
        this.python = Files.isRegularFile(venv) ? venv : Path.of("python");
        this.processor = repo.resolve("tools/persian-intelligence/final_pipeline_v019.py");
    }

    public int execute(UUID executionId, UUID runId, String stage) throws Exception {
        return switch (stage) {
            case "NATIVE_EXTRACTION" -> nativeExtraction(executionId);
            case "PAGE_QUALITY_GATE" -> qualityGate(executionId);
            case "OCR_REQUIRED_PAGES" -> ocrRequired(executionId);
            case "PERSIAN_NORMALIZATION" -> normalize(executionId);
            case "KNOWLEDGE_EXTRACTION" -> extractKnowledge(executionId);
            case "QUALITY_GATE" -> validate(executionId);
            case "DATASET_BUILD" -> buildDataset(executionId);
            case "PUBLISH" -> publish(executionId);
            default -> throw new IllegalArgumentException("Unsupported final stage: " + stage);
        };
    }

    public Map<String, Object> result(UUID executionId) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("execution", jdbc.queryForMap("select id,run_id,status,current_stage,selection_version_id,source_root,started_at,finished_at from knowledge_intake_execution where id=?", executionId));
        r.put("documents", jdbc.queryForList("select relative_path,detected_title,detected_category,file_extension,sha256,page_count,native_chars,suspicious_pages,intake_status,document_id from knowledge_intake_inventory where execution_id=? and selected_by_user=true order by relative_path", executionId));
        r.put("pages", jdbc.queryForList("select a.document_id,a.artifact_type,a.page_from as page_no,a.quality_score,a.processor_id,a.processor_version,left(a.content,1200) as preview,a.created_at from knowledge_source_artifact a where a.execution_id=? and a.artifact_type in ('RAW_PAGE','OCR_PAGE','NORMALIZED_PAGE') order by a.document_id,a.page_from,a.artifact_type", executionId));
        r.put("rules", jdbc.queryForList("select kr.id,kr.stable_key,kr.operator,kr.numeric_value,kr.text_value,kr.confidence,kr.status,n.page_from,n.title,left(n.content,600) as evidence from knowledge_rule kr left join knowledge_source_node n on n.id=kr.source_node_id join knowledge_execution_version ev on ev.knowledge_version_id=kr.knowledge_version_id where ev.execution_id=? order by n.page_from,kr.stable_key limit 500", executionId));
        r.put("facts", jdbc.queryForList("select kf.id,kf.fact_type,kf.predicate,kf.numeric_value,kf.literal_value,kf.confidence,kf.status,n.page_from,left(n.content,600) as evidence from knowledge_fact kf left join knowledge_source_node n on n.id=kf.source_node_id join knowledge_execution_version ev on ev.knowledge_version_id=kf.knowledge_version_id where ev.execution_id=? order by n.page_from limit 500", executionId));
        r.put("catalogCandidates", jdbc.queryForList("select c.display_term,c.normalized_term,c.candidate_type,c.occurrence_count,c.confidence,c.status from construction_catalog_candidate c join knowledge_execution_version ev on ev.catalog_version_id=c.catalog_version_id where ev.execution_id=? order by c.occurrence_count desc,c.display_term limit 500", executionId));
        r.put("graph", jdbc.queryForMap("select (select count(*) from knowledge_graph_node n where n.graph_version_id=ev.graph_version_id) nodes,(select count(*) from knowledge_graph_edge e where e.graph_version_id=ev.graph_version_id) edges,(select count(*) from knowledge_graph_community c where c.graphrag_version_id=ev.graphrag_version_id) communities from knowledge_execution_version ev where ev.execution_id=?", executionId));
        List<Map<String, Object>> rel = jdbc.queryForList("select r.release_key,r.status,r.source_snapshot_hash,r.compatibility_hash,r.created_at,r.published_at,r.retrieval_version,r.embedding_version from knowledge_release r join knowledge_execution_version ev on ev.release_id=r.id where ev.execution_id=?", executionId);
        r.put("release", rel.isEmpty() ? null : rel.get(0));
        return r;
    }

    private int nativeExtraction(UUID executionId) throws Exception {
        List<Map<String, Object>> docs = selected(executionId);
        int processed = 0;
        for (Map<String, Object> d : docs) {
            Path file = Path.of(String.valueOf(d.get("absolute_path")));
            String hash = String.valueOf(d.get("sha256"));
            UUID documentId = ensureDocument(executionId, file, hash);
            List<Page> pages = runProcessor("native", file, List.of());
            for (Page p : pages)
                upsertArtifact(executionId, documentId, "RAW_PAGE", "document.extract.native", "0.19.0", p.page, p.quality, p.text, null);
            int chars = pages.stream().mapToInt(p -> p.text.length()).sum();
            jdbc.update("update knowledge_intake_inventory set document_id=?,page_count=?,native_chars=?,updated_at=now() where execution_id=? and absolute_path=?", documentId, pages.size(), chars, executionId, file.toString());
            processed++;
        }
        return processed;
    }

    private int qualityGate(UUID executionId) {
        List<Map<String, Object>> rows = jdbc.queryForList("select id,content from knowledge_source_artifact where execution_id=? and artifact_type='RAW_PAGE'", executionId);
        int bad = 0;
        for (Map<String, Object> r : rows) {
            String s = Objects.toString(r.get("content"), "");
            BigDecimal q = BigDecimal.valueOf(Math.min(1.0, s.strip().length() / 400.0));
            if (q.doubleValue() < 0.25) bad++;
            jdbc.update("update knowledge_source_artifact set quality_score=? where id=?", q, r.get("id"));
        }
        jdbc.update("update knowledge_intake_inventory i set suspicious_pages=coalesce((select jsonb_agg(a.page_from order by a.page_from) filter (where a.page_from is not null) from knowledge_source_artifact a join knowledge_source_document d on d.id=a.document_id where a.execution_id=i.execution_id and d.sha256=i.sha256 and a.artifact_type='RAW_PAGE' and coalesce(a.quality_score,0)<0.25),'[]'::jsonb) where i.execution_id=?", executionId);
        return rows.size();
    }

    private int ocrRequired(UUID executionId) throws Exception {
        List<Map<String, Object>> docs = jdbc.queryForList("select distinct i.absolute_path,i.document_id from knowledge_intake_inventory i join knowledge_source_artifact a on a.document_id=i.document_id and a.execution_id=i.execution_id where i.execution_id=? and i.selected_by_user=true and lower(i.file_extension)='pdf' and a.artifact_type='RAW_PAGE' and coalesce(a.quality_score,0)<0.25", executionId);
        int pagesDone = 0;
        for (Map<String, Object> d : docs) {
            UUID doc = (UUID) d.get("document_id");
            List<Integer> pages = jdbc.queryForList("select page_from from knowledge_source_artifact where execution_id=? and document_id=? and artifact_type='RAW_PAGE' and coalesce(quality_score,0)<0.25 order by page_from", Integer.class, executionId, doc);
            if (pages.isEmpty()) continue;
            List<Page> out = runProcessor("ocr", Path.of(String.valueOf(d.get("absolute_path"))), pages);
            for (Page p : out) {
                upsertArtifact(executionId, doc, "OCR_PAGE", "document.extract.ocr.tesseract", "0.19.0", p.page, p.quality, p.text, null);
                pagesDone++;
            }
        }
        return pagesDone;
    }

    private int normalize(UUID executionId) throws Exception {
        List<Map<String, Object>> docs = selected(executionId);
        int count = 0;
        for (Map<String, Object> d : docs) {
            UUID doc = (UUID) d.get("document_id");
            if (doc == null) continue;
            List<Map<String, Object>> pages = jdbc.queryForList("select r.page_from,coalesce(o.content,r.content) content,case when o.id is null then r.id else o.id end parent_id from knowledge_source_artifact r left join knowledge_source_artifact o on o.execution_id=r.execution_id and o.document_id=r.document_id and o.page_from=r.page_from and o.artifact_type='OCR_PAGE' where r.execution_id=? and r.document_id=? and r.artifact_type='RAW_PAGE' order by r.page_from", executionId, doc);
            for (Map<String, Object> p : pages) {
                String normalized = normalizeFa(Objects.toString(p.get("content"), ""));
                UUID parent = (UUID) p.get("parent_id");
                UUID artifact = upsertArtifact(executionId, doc, "NORMALIZED_PAGE", "text.normalize.persian", "0.19.0", (Integer) p.get("page_from"), BigDecimal.ONE, normalized, parent);
                upsertNode(executionId, artifact, "PAGE", (Integer) p.get("page_from"), "Page " + p.get("page_from"), normalized);
                count++;
            }
        }
        return count;
    }

    private int extractKnowledge(UUID executionId) {
        Versions v = ensureVersions(executionId);
        jdbc.update("delete from construction_catalog_candidate where catalog_version_id=?", v.catalog);
        jdbc.update("delete from knowledge_rule where knowledge_version_id=?", v.knowledge);
        jdbc.update("delete from knowledge_fact where knowledge_version_id=?", v.knowledge);
        List<Map<String, Object>> nodes = jdbc.queryForList("select id,page_from,content from knowledge_source_node where execution_id=? and node_type='PAGE' order by page_from", executionId);
        int facts = 0;
        for (Map<String, Object> n : nodes) {
            String text = Objects.toString(n.get("content"), "");
            UUID node = (UUID) n.get("id");
            Matcher m = NUMBER.matcher(text);
            int ordinal = 0;
            while (m.find() && ordinal < 80) {
                String raw = m.group(1).replace(",", ".");
                BigDecimal num;
                try {
                    num = new BigDecimal(raw);
                } catch (Exception ex) {
                    continue;
                }
                int from = Math.max(0, m.start() - 70), to = Math.min(text.length(), m.end() + 70);
                String evidence = text.substring(from, to).replace('\n', ' ').strip();
                String term = termBefore(text, m.start());
                if (!term.isBlank()) upsertCandidate(v.catalog, term, node);
                String key = "AUTO-" + node + "-" + ordinal;
                jdbc.update("insert into knowledge_fact(id,knowledge_version_id,fact_type,stable_key,predicate,numeric_value,literal_value,confidence,status,source_node_id,metadata_json) values(?,?,'NUMERIC_STATEMENT',?,'HAS_NUMERIC_VALUE',?,?,0.70,'CANDIDATE',?,'{}'::jsonb)", UUID.randomUUID(), v.knowledge, key, num, evidence, node);
                jdbc.update("insert into knowledge_rule(id,knowledge_version_id,stable_key,operator,numeric_value,text_value,confidence,status,source_node_id) values(?,?,?,'OBSERVED',?,?,0.65,'CANDIDATE',?)", UUID.randomUUID(), v.knowledge, key, num, evidence, node);
                facts++;
                ordinal++;
            }
        }
        return facts;
    }

    private int validate(UUID executionId) {
        Integer pages = jdbc.queryForObject("select count(*) from knowledge_source_artifact where execution_id=? and artifact_type='NORMALIZED_PAGE'", Integer.class, executionId);
        if (pages == null || pages == 0)
            throw new IllegalStateException("Quality gate failed: no normalized pages were produced.");
        Versions v = ensureVersions(executionId);
        jdbc.update("update knowledge_fact set status='VERIFIED' where knowledge_version_id=? and confidence>=0.65", v.knowledge);
        jdbc.update("update knowledge_rule set status='VERIFIED' where knowledge_version_id=? and confidence>=0.65", v.knowledge);
        jdbc.update("update construction_catalog_candidate set status='VALIDATED' where catalog_version_id=? and confidence>=0.60", v.catalog);
        return pages;
    }

    private int buildDataset(UUID executionId) throws Exception {
        Versions v = ensureVersions(executionId);
        UUID graph = UUID.randomUUID();
        Long no = jdbc.queryForObject("select coalesce(max(version_no),0)+1 from knowledge_graph_version", Long.class);
        jdbc.update("insert into knowledge_graph_version(id,version_no,knowledge_version_id,catalog_version_id,status,content_hash) values(?,?,?,?,'BUILT',?)", graph, no, v.knowledge, v.catalog, shaText(executionId + "|" + v.knowledge + "|" + v.catalog));
        Map<UUID, UUID> docNodes = new HashMap<>();
        for (Map<String, Object> d : jdbc.queryForList("select document_id,detected_title from knowledge_intake_inventory where execution_id=? and selected_by_user=true and document_id is not null", executionId)) {
            UUID entity = (UUID) d.get("document_id"), gn = UUID.randomUUID();
            docNodes.put(entity, gn);
            jdbc.update("insert into knowledge_graph_node(id,graph_version_id,node_type,entity_type,entity_id,stable_key,properties_json) values(?,?,'DOCUMENT','SOURCE_DOCUMENT',?,?,jsonb_build_object('title',?))", gn, graph, entity, "DOC-" + entity, Objects.toString(d.get("detected_title"), ""));
        }
        List<Map<String, Object>> rules = jdbc.queryForList("select r.id,r.stable_key,r.source_node_id,a.document_id from knowledge_rule r join knowledge_source_node n on n.id=r.source_node_id join knowledge_source_artifact a on a.id=n.artifact_id where r.knowledge_version_id=? and r.status='VERIFIED'", v.knowledge);
        for (Map<String, Object> r : rules) {
            UUID gn = UUID.randomUUID(), entity = (UUID) r.get("id"), doc = (UUID) r.get("document_id");
            jdbc.update("insert into knowledge_graph_node(id,graph_version_id,node_type,entity_type,entity_id,stable_key) values(?,?,'RULE','KNOWLEDGE_RULE',?,?)", gn, graph, entity, r.get("stable_key"));
            UUID dn = docNodes.get(doc);
            if (dn != null)
                jdbc.update("insert into knowledge_graph_edge(id,graph_version_id,from_node_id,relation_type,to_node_id,confidence,source_node_id) values(?,?,?,'SUPPORTED_BY',?,1,?)", UUID.randomUUID(), graph, gn, dn, r.get("source_node_id"));
        }
        UUID gr = UUID.randomUUID();
        Long grNo = jdbc.queryForObject("select coalesce(max(version_no),0)+1 from knowledge_graphrag_version", Long.class);
        String sourceHash = shaText(graph.toString());
        jdbc.update("insert into knowledge_graphrag_version(id,version_no,graph_version_id,source_hash,community_algorithm,community_algorithm_version,status) values(?,?,?,?,'DOCUMENT_PARTITION','1.0','BUILT')", gr, grNo, graph, sourceHash);
        for (Map.Entry<UUID, UUID> e : docNodes.entrySet()) {
            UUID c = UUID.randomUUID();
            jdbc.update("insert into knowledge_graph_community(id,graphrag_version_id,level,stable_key,title,member_count,metadata_json) values(?,?,0,?,?,0,'{}'::jsonb)", c, gr, "DOC-" + e.getKey(), "Document community");
            jdbc.update("insert into knowledge_graph_community_member(community_id,graph_node_id,weight) select ?,id,1 from knowledge_graph_node where graph_version_id=? and (id=? or id in(select from_node_id from knowledge_graph_edge where graph_version_id=? and to_node_id=?)) on conflict do nothing", c, graph, e.getValue(), graph, e.getValue());
            jdbc.update("update knowledge_graph_community set member_count=(select count(*) from knowledge_graph_community_member where community_id=?) where id=?", c, c);
        }
        jdbc.update("update knowledge_execution_version set graph_version_id=?,graphrag_version_id=?,updated_at=now() where execution_id=?", graph, gr, executionId);
        return rules.size();
    }

    private int publish(UUID executionId) throws Exception {
        Versions v = ensureVersions(executionId);
        Map<String, Object> ev = jdbc.queryForMap("select graph_version_id,graphrag_version_id from knowledge_execution_version where execution_id=?", executionId);
        UUID graph = (UUID) ev.get("graph_version_id"), gr = (UUID) ev.get("graphrag_version_id");
        if (graph == null) throw new IllegalStateException("Cannot publish without graph version.");
        String sourceHash = shaText(jdbc.queryForList("select sha256 from knowledge_intake_inventory where execution_id=? and selected_by_user=true order by sha256", String.class, executionId).toString());
        String compat = shaText(sourceHash + "|" + v.catalog + "|" + v.knowledge + "|" + graph + "|" + gr);
        Long no = jdbc.queryForObject("select coalesce(max(release_no),0)+1 from knowledge_release", Long.class);
        UUID release = UUID.randomUUID();
        String key = String.format("SAKHTYAR-KNOWLEDGE-%06d", no);
        jdbc.update("insert into knowledge_release(id,release_no,release_key,status,source_snapshot_hash,catalog_version_id,knowledge_version_id,graph_version_id,graphrag_version_id,retrieval_version,compatibility_hash,published_at,notes) values(?,?,?,'PUBLISHED',?,?,?,?,?,'postgres-fts-v1',?,now(),'Generated by final pipeline v0.19.0')", release, no, key, sourceHash, v.catalog, v.knowledge, graph, gr, compat);
        String[][] comps = {{"SOURCE", sourceHash}, {"CATALOG", v.catalog.toString()}, {"KNOWLEDGE", v.knowledge.toString()}, {"GRAPH", graph.toString()}, {"GRAPHRAG", Objects.toString(gr, "none")}, {"RETRIEVAL", "postgres-fts-v1"}};
        for (String[] c : comps)
            jdbc.update("insert into knowledge_release_component(id,release_id,component_type,component_version,content_hash,dependency_hash,compatibility_status) values(?,?,?,?,?,?, 'COMPATIBLE')", UUID.randomUUID(), release, c[0], c[1], shaText(c[1]), compat);
        jdbc.update("update construction_catalog_version set status='PUBLISHED',published_at=now(),content_hash=? where id=?", shaText(v.catalog.toString()), v.catalog);
        jdbc.update("update structured_knowledge_version set status='PUBLISHED',published_at=now(),content_hash=? where id=?", shaText(v.knowledge.toString()), v.knowledge);
        jdbc.update("update knowledge_graph_version set status='PUBLISHED' where id=?", graph);
        if (gr != null) jdbc.update("update knowledge_graphrag_version set status='PUBLISHED' where id=?", gr);
        jdbc.update("update knowledge_execution_version set release_id=?,updated_at=now() where execution_id=?", release, executionId);
        return 1;
    }

    private List<Map<String, Object>> selected(UUID id) {
        return jdbc.queryForList("select absolute_path,relative_path,sha256,document_id from knowledge_intake_inventory where execution_id=? and selected_by_user=true and intake_status<>'SKIP_DUPLICATE_EXACT' order by relative_path", id);
    }

    private UUID ensureDocument(UUID executionId, Path file, String hash) throws Exception {
        List<UUID> ids = jdbc.queryForList("select id from knowledge_source_document where sha256=?", UUID.class, hash);
        UUID id = ids.isEmpty() ? UUID.randomUUID() : ids.get(0);
        if (ids.isEmpty())
            jdbc.update("insert into knowledge_source_document(id,sha256,original_name,media_type,storage_uri) values(?,?,?,?,?)", id, hash, file.getFileName().toString(), media(file), file.toUri().toString());
        jdbc.update("update knowledge_intake_inventory set document_id=? where execution_id=? and absolute_path=?", id, executionId, file.toString());
        return id;
    }

    private UUID upsertArtifact(UUID executionId, UUID doc, String type, String processorId, String version, int page, BigDecimal quality, String content, UUID parent) throws Exception {
        String hash = shaText(content);
        List<UUID> ids = jdbc.queryForList("select id from knowledge_source_artifact where execution_id=? and document_id=? and artifact_type=? and page_from=? and content_hash=?", UUID.class, executionId, doc, type, page, hash);
        if (!ids.isEmpty()) return ids.get(0);
        UUID id = UUID.randomUUID();
        jdbc.update("insert into knowledge_source_artifact(id,document_id,parent_artifact_id,artifact_type,processor_id,processor_version,content_hash,content,page_from,page_to,quality_score,metadata_json,execution_id) values(?,?,?,?,?,?,?,?,?,?,?,'{}'::jsonb,?)", id, doc, parent, type, processorId, version, hash, content, page, page, quality, executionId);
        return id;
    }

    private UUID upsertNode(UUID executionId, UUID artifact, String type, int page, String title, String content) {
        List<UUID> ids = jdbc.queryForList("select id from knowledge_source_node where execution_id=? and artifact_id=? and node_type=? and page_from=?", UUID.class, executionId, artifact, type, page);
        if (!ids.isEmpty()) return ids.get(0);
        UUID id = UUID.randomUUID();
        jdbc.update("insert into knowledge_source_node(id,artifact_id,node_type,ordinal,title,content,page_from,page_to,metadata_json,execution_id) values(?,?,?,0,?,?,?,?, '{}'::jsonb,?)", id, artifact, type, title, content, page, page, executionId);
        return id;
    }

    private void upsertCandidate(UUID catalog, String term, UUID node) {
        String norm = normalizeFa(term).toLowerCase(Locale.ROOT);
        List<UUID> ids = jdbc.queryForList("select id from construction_catalog_candidate where catalog_version_id=? and normalized_term=?", UUID.class, catalog, norm);
        if (ids.isEmpty())
            jdbc.update("insert into construction_catalog_candidate(id,catalog_version_id,normalized_term,display_term,candidate_type,occurrence_count,confidence,status,evidence_json) values(?,?,?,?,'TERM',1,0.65,'DISCOVERED',jsonb_build_array(jsonb_build_object('sourceNodeId',?)))", UUID.randomUUID(), catalog, norm, term, node.toString());
        else
            jdbc.update("update construction_catalog_candidate set occurrence_count=occurrence_count+1 where id=?", ids.get(0));
    }

    private Versions ensureVersions(UUID executionId) {
        jdbc.update("insert into knowledge_execution_version(execution_id) values(?) on conflict do nothing", executionId);
        Map<String, Object> row = jdbc.queryForMap("select catalog_version_id,knowledge_version_id from knowledge_execution_version where execution_id=?", executionId);
        UUID cat = (UUID) row.get("catalog_version_id"), know = (UUID) row.get("knowledge_version_id");
        if (cat == null) {
            cat = UUID.randomUUID();
            Long no = jdbc.queryForObject("select coalesce(max(version_no),0)+1 from construction_catalog_version", Long.class);
            jdbc.update("insert into construction_catalog_version(id,version_no,status) values(?,?,'DRAFT')", cat, no);
            jdbc.update("update knowledge_execution_version set catalog_version_id=?,updated_at=now() where execution_id=?", cat, executionId);
        }
        if (know == null) {
            know = UUID.randomUUID();
            Long no = jdbc.queryForObject("select coalesce(max(version_no),0)+1 from structured_knowledge_version", Long.class);
            jdbc.update("insert into structured_knowledge_version(id,version_no,catalog_version_id,status) values(?,?,?,'DRAFT')", know, no, cat);
            jdbc.update("update knowledge_execution_version set knowledge_version_id=?,updated_at=now() where execution_id=?", know, executionId);
        }
        return new Versions(cat, know);
    }

    private List<Page> runProcessor(String mode, Path file, List<Integer> pages) throws Exception {
        List<String> cmd = new ArrayList<>(List.of(python.toString(), processor.toString(), "--mode", mode, "--file", file.toString()));
        if (!pages.isEmpty()) {
            cmd.add("--pages");
            cmd.add(pages.stream().map(String::valueOf).collect(Collectors.joining(",")));
        }
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(repo.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(20, java.util.concurrent.TimeUnit.MINUTES)) {
            p.destroyForcibly();
            throw new IllegalStateException("Processor timeout for " + file);
        }
        if (p.exitValue() != 0)
            throw new IllegalStateException("Processor failed (" + p.exitValue() + "): " + output.substring(0, Math.min(4000, output.length())));
        List<Page> result = new ArrayList<>();
        for (String line : output.split("\\R")) {
            String[] x = line.split("\\t", 4);
            if (x.length != 4) continue;
            try {
                result.add(new Page(Integer.parseInt(x[0]), x[1], new BigDecimal(x[2]), new String(Base64.getDecoder().decode(x[3]), StandardCharsets.UTF_8)));
            } catch (Exception ignored) {
            }
        }
        if (result.isEmpty() && "native".equals(mode)) throw new IllegalStateException("No page output for " + file);
        return result;
    }

    private String normalizeFa(String s) {
        return s == null ? "" : s.replace('ي', 'ی').replace('ى', 'ی').replace('ك', 'ک').replace('ة', 'ه').replace('ۀ', 'ه').replace("\u200f", "").replace("\u200e", "").replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\\n\\n").strip();
    }

    private String termBefore(String text, int pos) {
        int from = Math.max(0, pos - 55);
        String s = text.substring(from, pos).replace('\n', ' ').replaceAll("[^\\p{L}\\p{M}\\s‌-]", " ").replaceAll("\\s+", " ").strip();
        String[] w = s.split(" ");
        return Arrays.stream(w).skip(Math.max(0, w.length - 5)).collect(Collectors.joining(" "));
    }

    private String media(Path p) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        if (n.endsWith(".pdf")) return "application/pdf";
        if (n.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (n.endsWith(".pptx")) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        return "text/plain";
    }

    private String shaText(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
    }

    private Path findRepo(Path p) {
        p = p.toAbsolutePath().normalize();
        for (int i = 0; i < 8 && p != null; i++, p = p.getParent())
            if (Files.isDirectory(p.resolve("backend")) && Files.isDirectory(p.resolve("frontend"))) return p;
        throw new IllegalStateException("SakhtYar repo root not found");
    }

    private record Page(int page, String method, BigDecimal quality, String text) {
    }

    private record Versions(UUID catalog, UUID knowledge) {
    }
}
