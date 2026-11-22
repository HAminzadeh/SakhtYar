package com.sakhtyar.quality;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;

@RestController
@RequestMapping("/api/v1/admin/quality")
public class QualityController {
    private final QualityService service;
    public QualityController(QualityService service){this.service=service;}
    @GetMapping("/overview") public Map<String,Object> overview(){return service.overview();}
    @GetMapping("/catalog") public List<QualityCatalog.TestDefinition> catalog(){return service.catalog();}
    @GetMapping("/runs") public List<Map<String,Object>> runs(@RequestParam(defaultValue="40")int limit){return service.history(limit);}
    @GetMapping("/tests/{id}/history") public List<Map<String,Object>> testHistory(@PathVariable String id,@RequestParam(defaultValue="30")int limit){return service.testHistory(id,limit);}
    @GetMapping("/runs/{id}") public Map<String,Object> run(@PathVariable UUID id){return service.run(id);}
    @PostMapping("/runs") public Map<String,Object> start(@RequestBody QualityService.RunRequest req, Authentication auth){
        return service.create(req,auth.getName());
    }
    @GetMapping(value="/runs/{id}/report",produces=MediaType.APPLICATION_JSON_VALUE)
    public Map<String,Object> jsonReport(@PathVariable UUID id){return service.run(id);}
    @GetMapping(value="/runs/{id}/report.csv",produces="text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> csv(@PathVariable UUID id){
        var run=service.run(id);
        @SuppressWarnings("unchecked") var rows=(List<Map<String,Object>>)run.get("results");
        StringBuilder csv=new StringBuilder("test_id,module,title,status,expected,actual,detail,duration_ms\r\n");
        for(var row:rows){
            String[] cols={"test_id","module","title","status","expected","actual","detail","duration_ms"};
            for(int i=0;i<cols.length;i++){
                if(i>0)csv.append(',');
                csv.append(csvCell(row.get(cols[i])));
            }
            csv.append("\r\n");
        }
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=quality-"+id+".csv")
            .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
            .body(("\uFEFF"+csv).getBytes(StandardCharsets.UTF_8));
    }
    @GetMapping(value="/runs/{id}/report.html",produces="text/html;charset=UTF-8")
    public ResponseEntity<String> html(@PathVariable UUID id){
        var run=service.run(id);
        @SuppressWarnings("unchecked") var rows=(List<Map<String,Object>>)run.get("results");
        StringBuilder html=new StringBuilder("<!doctype html><html lang='fa' dir='rtl'><head><meta charset='utf-8'><title>گزارش تست ساخت‌یار</title><style>body{font-family:Tahoma,sans-serif;margin:32px}td,th{padding:8px;border:1px solid #ddd}table{border-collapse:collapse;width:100%}th{background:#eef}</style></head><body>");
        html.append("<h1>گزارش مرکز آزمون ساخت‌یار</h1><p>شماره اجرا: ").append(id).append("</p>");
        html.append("<p>نتیجه: ").append(h(run.get("status"))).append(" — موفق: ").append(h(run.get("passed")))
            .append(" / ناموفق: ").append(h(run.get("failed"))).append(" / اجرا نشده: ").append(h(run.get("blocked"))).append("</p>");
        html.append("<p>نسخه کد: ").append(h(run.get("git_commit"))).append(" | محیط: ").append(h(run.get("environment"))).append("</p>");
        html.append("<table><thead><tr><th>شناسه</th><th>ماژول</th><th>سناریو</th><th>نتیجه</th><th>جزئیات</th></tr></thead><tbody>");
        for(var row:rows)html.append("<tr><td>").append(h(row.get("test_id"))).append("</td><td>").append(h(row.get("module")))
            .append("</td><td>").append(h(row.get("title"))).append("</td><td>").append(h(row.get("status")))
            .append("</td><td>").append(h(row.get("actual"))).append(" / ").append(h(row.get("detail"))).append("</td></tr>");
        html.append("</tbody></table><p>برای PDF: Print → Save as PDF</p></body></html>");
        return ResponseEntity.ok().header("Content-Security-Policy","default-src 'none'; style-src 'unsafe-inline'")
           .body(html.toString());
    }
    private String h(Object value){return HtmlUtils.htmlEscape(Objects.toString(value,""));}
    private String csvCell(Object value){
        String s=Objects.toString(value,"").replace("\r"," ").replace("\n"," ");
        if(s.startsWith("=")||s.startsWith("+")||s.startsWith("-")||s.startsWith("@")) s="'"+s;
        return "\""+s.replace("\"","\"\"")+"\"";
    }
}
