package com.sakhtyar.knowledge.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class KnowledgeOcrPageReviewService {
    private final JdbcTemplate jdbc;
    private final Path repo;

    public KnowledgeOcrPageReviewService(JdbcTemplate jdbc,
            @Value("${sakhtyar.repo-root:${user.dir}}") String root) {
        this.jdbc = jdbc;
        Path path = Path.of(root).toAbsolutePath().normalize();
        for (int i=0; i<8 && path!=null; i++,path=path.getParent()) {
            if (Files.isDirectory(path.resolve("backend")) && Files.isDirectory(path.resolve("frontend"))) {
                this.repo = path;
                return;
            }
        }
        throw new IllegalStateException("SakhtYar repo root not found");
    }

    @Transactional(readOnly = true)
    public Map<String,Object> pages(UUID executionId, String status, int page, int pageSize) {
        if (!Set.of("ALL","PENDING","TEXT_PROVIDED","NOT_IMPORTANT").contains(status))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unknown review status");
        int size = Math.max(1,Math.min(pageSize,40)), p = Math.max(1,Math.min(page,100000));
        String base = " from knowledge_ocr_page_review r " +
            " join lateral (select i.relative_path, i.absolute_path from knowledge_intake_inventory i " +
            " where i.execution_id=r.execution_id and i.document_id=r.document_id and i.selected_by_user=true " +
            " order by i.updated_at desc limit 1) i on true " +
            " where 1=1 ";
        List<Object> args = new ArrayList<>();
        if (executionId != null) { base += " and r.execution_id=? "; args.add(executionId); }
        if (!"ALL".equals(status)) { base += " and r.status=? "; args.add(status); }
        Long total = jdbc.queryForObject("select count(*)"+base, Long.class,args.toArray());
        List<Object> paged = new ArrayList<>(args);
        paged.add(size); paged.add((p-1)*size);
        List<Map<String,Object>> rows = jdbc.queryForList(
            "select r.execution_id::text as execution_id, r.document_id::text as document_id, " +
            "r.page_no,r.status,r.reason,r.native_chars,r.ocr_chars,r.review_text,r.reviewed_by, " +
            "r.reviewed_at,r.created_at,i.relative_path" + base +
            " order by r.created_at desc,r.execution_id,r.document_id,r.page_no limit ? offset ?", paged.toArray());
        return Map.of("items",rows,"total",total==null?0:total,"page",p,"pageSize",size);
    }

    @Transactional
    public Map<String,Object> decide(UUID executionId, UUID documentId, int pageNo,
                                  String decision, String text, String reviewer) {
        if (reviewer == null || reviewer.isBlank())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Authenticated reviewer required");
        if (!Set.of("TEXT_PROVIDED","NOT_IMPORTANT").contains(decision))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unknown decision");
        String normalized = text == null ? "" : text.strip();
        if ("TEXT_PROVIDED".equals(decision) && (normalized.length() < 10 || normalized.length() > 100000))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Provide 10-100000 characters of human transcription");
        // The selected execution must still be awaiting human OCR review;
        // no retroactive mutation of a completed, versioned knowledge snapshot.
        List<String> statuses = jdbc.queryForList(
            "select status from knowledge_intake_execution where id=? for update", String.class,executionId);
        if (statuses.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Execution not found");
        if (!"WAITING_FOR_PAGE_REVIEW".equals(statuses.get(0)))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"This execution is not awaiting page review");
        int changed = jdbc.update("update knowledge_ocr_page_review set status=?,review_text=?,reviewed_by=?, " +
            " reviewed_at=now(),updated_at=now() where execution_id=? and document_id=? and page_no=?",
            decision,"TEXT_PROVIDED".equals(decision)?normalized:null,reviewer,executionId,documentId,pageNo);
        if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Review page not found");
        Integer remaining = jdbc.queryForObject("select count(*) from knowledge_ocr_page_review " +
            "where execution_id=? and status='PENDING'",Integer.class,executionId);
        return Map.of("status",decision,"pendingCount",remaining==null?0:remaining);
    }

    @Transactional(readOnly = true)
    public Map<String,String> pageImage(UUID executionId,UUID documentId,int pageNo) throws Exception {
        if (pageNo < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Page number must be positive");
        List<String> filePaths=jdbc.queryForList(
            "select i.absolute_path from knowledge_ocr_page_review r " +
            "join knowledge_intake_inventory i on i.execution_id=r.execution_id " +
            " and i.document_id=r.document_id and i.selected_by_user=true " +
            "where r.execution_id=? and r.document_id=? and r.page_no=? " +
            "order by i.updated_at desc limit 1",String.class,executionId,documentId,pageNo);
        if (filePaths.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Page not in review queue");
        Path path=Path.of(filePaths.get(0)).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path) || !path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf"))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Original PDF unavailable");
        Path localPy = repo.resolve(".local/venv-persian-intelligence/Scripts/python.exe");
        String python = Files.isRegularFile(localPy) ? localPy.toString() : "python";
        String code = "import sys,base64,pymupdf\n"+
            "with pymupdf.open(sys.argv[1]) as d:\n"+
            " n=int(sys.argv[2])\n"+
            " if n<1 or n>len(d): raise ValueError('Invalid page')\n"+
            " p=d[n-1].get_pixmap(matrix=pymupdf.Matrix(1.35,1.35),alpha=False)\n"+
            " b=p.tobytes('png')\n"+
            " if len(b)>3000000: raise ValueError('Page raster too large')\n"+
            " print(base64.b64encode(b).decode('ascii'))\n";
        Path output = Files.createTempFile("sakhtyar-ocr-review-",".out");
        try {
            ProcessBuilder pb=new ProcessBuilder(python,"-c",code,path.toString(),String.valueOf(pageNo));
            pb.directory(repo.toFile());pb.redirectErrorStream(true);pb.redirectOutput(output.toFile());
            Process process=pb.start();
            if (!process.waitFor(35,TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT,"Page preview timed out");
            }
            byte[] data=Files.readAllBytes(output);
            if(process.exitValue()!=0 || data.length>4200000)
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Cannot render this PDF page");
            String b64=new String(data,StandardCharsets.US_ASCII).strip();
            if (!b64.matches("[A-Za-z0-9+/=]+"))
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Invalid page image response");
            return Map.of("dataUrl","data:image/png;base64,"+b64);
        } finally {
            Files.deleteIfExists(output);
        }
    }
}
