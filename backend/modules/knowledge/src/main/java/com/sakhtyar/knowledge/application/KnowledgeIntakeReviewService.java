package com.sakhtyar.knowledge.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Stream;

@Service
public class KnowledgeIntakeReviewService {
    private final JdbcTemplate jdbc;
    private final Path configuredRoot;

    public KnowledgeIntakeReviewService(
            JdbcTemplate jdbc,
            @Value("${sakhtyar.repo-root:}") String configuredRoot) {
        this.jdbc = jdbc;
        this.configuredRoot = configuredRoot == null || configuredRoot.isBlank()
                ? null : Path.of(configuredRoot).toAbsolutePath().normalize();
    }

    public Map<String,Object> review() {
        Discovery discovery = discoverReport();
        if (discovery.report() == null) {
            Map<String,Object> out = new LinkedHashMap<>();
            out.put("available", false);
            out.put("documents", List.of());
            out.put("searchedRoots", discovery.searchedRoots());
            out.put("message", "dry-run-v07.json was not found in known SakhtYar locations");
            return out;
        }

        Path report = discovery.report();
        try {
            String json = Files.readString(report, StandardCharsets.UTF_8);
            List<Map<String,Object>> docs = parseArray(json);
            Map<String,Integer> counts = new LinkedHashMap<>();
            int selectable = 0;
            int mapped = 0;

            for (Map<String,Object> d : docs) {
                String status = String.valueOf(d.getOrDefault("status", "UNKNOWN"));
                counts.merge(status, 1, Integer::sum);
                String sha = String.valueOf(d.getOrDefault("sha256", ""));

                UUID id = sha.isBlank() ? null : jdbc.query("""
                    select id from knowledge_source_document
                    where sha256=?
                    order by document_version desc, created_at desc
                    limit 1
                    """, rs -> rs.next() ? (UUID) rs.getObject(1) : null, sha);

                d.put("documentId", id == null ? null : id.toString());
                if (id != null) mapped++;

                boolean duplicate = "SKIP_DUPLICATE_EXACT".equals(status);
                boolean enabled = !duplicate && id != null;
                d.put("selectable", enabled);
                d.put("defaultSelected", enabled &&
                        ("VALID_NATIVE".equals(status) || "VALID_HYBRID".equals(status)));
                if (enabled) selectable++;
            }

            Map<String,Object> out = new LinkedHashMap<>();
            out.put("available", true);
            out.put("reportPath", report.toString());
            out.put("reportLastModified", Files.getLastModifiedTime(report).toString());
            out.put("total", docs.size());
            out.put("mapped", mapped);
            out.put("unmapped", docs.size() - mapped);
            out.put("selectable", selectable);
            out.put("counts", counts);
            out.put("documents", docs);
            out.put("searchedRoots", discovery.searchedRoots());
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read intake report " + report + ": " + e.getMessage(), e);
        }
    }

    private Discovery discoverReport() {
        LinkedHashSet<Path> roots = new LinkedHashSet<>();
        addRootAndParents(roots, configuredRoot);
        addRootAndParents(roots, Path.of(System.getProperty("user.dir", ".")));

        String envRoot = System.getenv("SAKHTYAR_REPO_ROOT");
        if (envRoot != null && !envRoot.isBlank()) addRootAndParents(roots, Path.of(envRoot));

        // Known Windows development layout used by SakhtYar. It is only a fallback.
        Path known = Path.of("D:/ChatGPT_Projects/SakhtYar/source");
        if (Files.isDirectory(known)) addRootAndParents(roots, known);

        List<Path> directCandidates = new ArrayList<>();
        for (Path root : roots) {
            directCandidates.add(root.resolve(".local/persian-intake/dry-run-v07.json"));
            directCandidates.add(root.resolve("source/.local/persian-intake/dry-run-v07.json"));
            directCandidates.add(root.resolve("tools/persian-intelligence/.local/persian-intake/dry-run-v07.json"));
        }

        Path newest = directCandidates.stream()
                .filter(Files::isRegularFile)
                .max(Comparator.comparing(this::lastModifiedSafe))
                .orElse(null);

        // Bounded fallback scan: only inside discovered project roots, never the whole drive.
        if (newest == null) {
            for (Path root : roots) {
                if (!Files.isDirectory(root)) continue;
                try (Stream<Path> walk = Files.walk(root, 6)) {
                    Path found = walk
                            .filter(Files::isRegularFile)
                            .filter(p -> p.getFileName().toString().equalsIgnoreCase("dry-run-v07.json"))
                            .max(Comparator.comparing(this::lastModifiedSafe))
                            .orElse(null);
                    if (found != null && (newest == null ||
                            lastModifiedSafe(found).compareTo(lastModifiedSafe(newest)) > 0)) {
                        newest = found;
                    }
                } catch (Exception ignored) { }
            }
        }

        return new Discovery(newest == null ? null : newest.toAbsolutePath().normalize(),
                roots.stream().map(Path::toString).toList());
    }

    private void addRootAndParents(Set<Path> roots, Path start) {
        if (start == null) return;
        Path p = start.toAbsolutePath().normalize();
        for (int i=0; i<5 && p!=null; i++, p=p.getParent()) {
            if (Files.isDirectory(p)) roots.add(p);
        }
    }

    private FileTime lastModifiedSafe(Path p) {
        try { return Files.getLastModifiedTime(p); }
        catch (Exception e) { return FileTime.fromMillis(0); }
    }

    private List<Map<String,Object>> parseArray(String json) {
        List<Map<String,Object>> out = new ArrayList<>();
        Matcher objects = Pattern.compile("\\{([^{}]*)\\}", Pattern.DOTALL).matcher(json);
        while (objects.find()) {
            String body = objects.group(1);
            Map<String,Object> row = new LinkedHashMap<>();
            putString(body,row,"path");
            putString(body,row,"sha256");
            putString(body,row,"status");
            putString(body,row,"reason");
            putInt(body,row,"page_count");
            putInt(body,row,"native_chars");

            Matcher sp = Pattern.compile("\"suspicious_pages\"\\s*:\\s*\\[([^\\]]*)\\]").matcher(body);
            List<Integer> pages = new ArrayList<>();
            if (sp.find()) {
                for (String n : sp.group(1).split(",")) {
                    try { if (!n.isBlank()) pages.add(Integer.parseInt(n.trim())); }
                    catch (Exception ignored) { }
                }
            }
            row.put("suspicious_pages", pages);
            out.add(row);
        }
        return out;
    }

    private void putString(String body, Map<String,Object> row, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) +
                "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"").matcher(body);
        row.put(key, m.find()
                ? m.group(1).replace("\\\\", "\\").replace("\\\"", "\"")
                : "");
    }

    private void putInt(String body, Map<String,Object> row, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(\\d+)").matcher(body);
        row.put(key, m.find() ? Integer.parseInt(m.group(1)) : 0);
    }

    private record Discovery(Path report, List<String> searchedRoots) { }
}