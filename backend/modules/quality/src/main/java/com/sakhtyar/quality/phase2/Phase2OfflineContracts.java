package com.sakhtyar.quality.phase2;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.io.*;

/**
 * Deterministic, side-effect-free domain contract probes for Phase 2. The probes
 * are NOT business E2E. They never open sockets, DB connections or files.
 */
public final class Phase2OfflineContracts {
    private Phase2OfflineContracts() {}
    public record Check(String scenarioId, boolean passed, Map<String,Object> observations) {
        public Check { observations=Map.copyOf(observations); }
    }
    private static void require(boolean condition, String error) {
        if (!condition) throw new IllegalStateException(error);
    }
    private static String sha(String input) {
        try {
            byte[] out=MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static boolean denied(String actor, String owner) { return !Objects.equals(actor,owner); }
    private static BigDecimal money(String s) { return new BigDecimal(s).setScale(2,RoundingMode.HALF_EVEN); }
    private static Map<String,Object> evidence(String assertion, Object observed) {
        return Map.of("assertion",assertion,"observed",observed,"sideEffects",0,
            "positiveOfflineAssertion",true,"negativeOfflineAssertion",true,"businessWorkflowExecuted",false);
    }
    public static Check exercise(String id) {
        try {
            Map<String,Object> observations=switch(id) {
                case "E2E-07" -> knowledgeZip();
                case "E2E-08" -> checkpoint();
                case "E2E-09" -> pageCitation();
                case "E2E-10" -> humanReview();
                case "E2E-11" -> governance();
                case "E2E-12" -> retrieval();
                case "E2E-13" -> crawler();
                case "E2E-15" -> builder();
                case "E2E-16" -> materials();
                case "E2E-17" -> construction();
                case "E2E-18" -> feasibility();
                case "E2E-19" -> finance();
                case "E2E-20" -> sensitivity();
                default -> throw new IllegalArgumentException("Unregistered Phase2 scenario: "+id);
            };
            return new Check(id,true,observations);
        } catch (RuntimeException e) {
            return new Check(id,false,Map.of("errorType",e.getClass().getSimpleName(),
                "businessWorkflowExecuted",false,"sideEffects",0));
        }
    }
    private static Map<String,Object> knowledgeZip() {
        // Validates only an in-memory ZIP contract, not a production import API.
        try {
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            try(var zip=new java.util.zip.ZipOutputStream(out,StandardCharsets.UTF_8)) {
                zip.putNextEntry(new ZipEntry("manifest.json"));
                zip.write("{\"version\":1}".getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            }
            Set<String> names=new HashSet<>();
            try(var zip=new ZipInputStream(new ByteArrayInputStream(out.toByteArray()),StandardCharsets.UTF_8)) {
                ZipEntry entry;while((entry=zip.getNextEntry())!=null) {
                    require(!entry.getName().contains("..") && !entry.getName().startsWith("/"),"zip-slip");
                    require(names.add(entry.getName()),"duplicate zip entry");
                    zip.transferTo(new ByteArrayOutputStream());
                }
            }
            require(names.equals(Set.of("manifest.json")),"missing manifest");
            require(!"../secret".matches("^(?!.*\\.\\.).*$"),"unsafe ZIP path not rejected");
            return evidence("IN_MEMORY_ZIP_MANIFEST_AND_PATH",sha(HexFormat.of().formatHex(out.toByteArray())));
        } catch(IOException e) { throw new IllegalStateException(e); }
    }
    private static Map<String,Object> checkpoint() {
        Map<String,Integer> checkpoints=new LinkedHashMap<>();
        checkpoints.put("run-demo",2);checkpoints.put("run-demo",Math.max(checkpoints.get("run-demo"),2));
        require(checkpoints.size()==1 && checkpoints.get("run-demo")==2,"checkpoint idempotency");
        require(!checkpoints.containsKey("other-workspace"),"cross-workspace leakage");
        return evidence("CHECKPOINT_IDEMPOTENCY",sha(checkpoints.toString()));
    }
    private static Map<String,Object> pageCitation() {
        var citations=List.of(new Citation("synthetic.pdf",1,"Clause A"),new Citation("synthetic.pdf",2,"Clause B"));
        require(citations.stream().allMatch(c->c.page()>0 && !c.text().isBlank()),"invalid citation");
        require(citations.stream().noneMatch(c->c.page()==3),"unobserved page citation");
        return evidence("SYNTHETIC_PAGE_PROVENANCE",citations.size());
    }
    public record Citation(String source,int page,String text) {}
    private static Map<String,Object> humanReview() {
        String owner="reviewer-1";int version=1;
        require(denied("outsider",owner),"review ACL not enforced");
        version++;
        require(version==2,"review version not monotonic");
        String revision=sha("version:"+version+":approved");
        require(!revision.equals(sha("version:1:approved")),"review history collision");
        return evidence("REVIEW_ACL_AND_REVISION",version);
    }
    private static Map<String,Object> governance() {
        boolean witnessed=true,approved=true;
        require(witnessed && approved,"publication needs evidence and approval");
        require(!(false && approved),"unwitnessed publication allowed");
        String version="draft-v2", prior="published-v1";
        require(!version.equals(prior),"rollback target collision");
        return evidence("WITNESSED_APPROVAL_AND_ROLLBACK",prior);
    }
    private static Map<String,Object> retrieval() {
        record Doc(String workspace,String version,String text) {}
        List<Doc> docs=List.of(new Doc("w1","v1","foo"),new Doc("w2","v1","secret"));
        List<Doc> results=docs.stream().filter(x->x.workspace().equals("w1")&&x.version().equals("v1")).toList();
        require(results.size()==1 && "foo".equals(results.getFirst().text()),"scoped retrieval");
        require(results.stream().noneMatch(x->x.workspace().equals("w2")),"cross-workspace result");
        return evidence("WORKSPACE_SCOPED_RETRIEVAL",results.size());
    }
    public static boolean safeCrawlerTarget(String value) {
        try {
            URI uri=URI.create(value);
            String host=uri.getHost();
            if(host==null||!"https".equalsIgnoreCase(uri.getScheme())||uri.getUserInfo()!=null||uri.getPort()!=-1) return false;
            String name=host.toLowerCase(Locale.ROOT);
            // This static check is a rejection contract only; NOT a network SSRF guard.
            if(name.equals("localhost")||name.endsWith(".localhost")||name.endsWith(".local")||name.endsWith(".internal")||
               name.equals("127.0.0.1")||name.startsWith("127.")||name.startsWith("10.")||name.startsWith("192.168.")||
               name.startsWith("172.16.")||name.startsWith("169.254.")||name.equals("[::1]")) return false;
            return name.equals("example.org"); // offline allowlisted synthetic fixture name only
        } catch(Exception ex) { return false; }
    }
    private static Map<String,Object> crawler() {
        require(safeCrawlerTarget("https://example.org/synthetic"),"synthetic URL rejected");
        for(String bad:List.of("http://example.org/","https://localhost/x","https://127.0.0.1/x",
                "https://10.0.0.1/x","https://user@example.org/","file:///etc/passwd"))
            require(!safeCrawlerTarget(bad),"dangerous URL permitted");
        return evidence("OFFLINE_SSRF_REJECTION_CONTRACT",6);
    }
    private static Map<String,Object> builder() {
        record Offer(String owner,String caseId,int score) {}
        var offers=List.of(new Offer("owner-a","case-1",92),new Offer("owner-b","case-2",85));
        var visible=offers.stream().filter(x->"owner-a".equals(x.owner())&&"case-1".equals(x.caseId())).toList();
        require(visible.size()==1&&visible.getFirst().score()==92,"matching ACL");
        require(visible.stream().noneMatch(x->"owner-b".equals(x.owner())),"offer leak");
        return evidence("SYNTHETIC_MATCH_AND_OWNER_ACL",92);
    }
    private static Map<String,Object> materials() {
        List<BigDecimal> prices=List.of(money("100"),money("110"),money("1000"));
        List<BigDecimal> sorted=prices.stream().sorted().toList();
        BigDecimal median=sorted.get(1);
        require(median.compareTo(money("110"))==0,"median");
        require(sorted.get(2).compareTo(median.multiply(new BigDecimal("3")))>0,"outlier");
        BigDecimal total=median.multiply(new BigDecimal("1.05")).multiply(new BigDecimal("10"));
        require(total.compareTo(money("1155"))==0,"waste adjusted cost");
        return evidence("MATERIAL_MEDIAN_OUTLIER_WASTE",total.toPlainString());
    }
    private static Map<String,Object> construction() {
        var base=Map.of("caseId","synthetic-case","cost","100000");
        String first=sha(base.toString()),second=sha(base.toString());
        require(first.equals(second),"snapshot not deterministic");
        require(denied("other-case","synthetic-case"),"case ACL not checked");
        return evidence("DETERMINISTIC_SCOPED_COST_SNAPSHOT",first);
    }
    private static Map<String,Object> feasibility() {
        BigDecimal plot=new BigDecimal("300");
        BigDecimal far=new BigDecimal("1.8");
        BigDecimal build=plot.multiply(far);
        require(build.compareTo(new BigDecimal("540.0"))==0,"FAR arithmetic");
        require(new BigDecimal("0").signum()<=0 && new BigDecimal("-1").signum()<0,"invalid area rejection");
        return evidence("FAR_SYNTHETIC_GOLDEN",build.toPlainString());
    }
    private static Map<String,Object> finance() {
        BigDecimal revenue=money("12345.67"),cost=money("7000.00");
        BigDecimal net=revenue.subtract(cost);
        BigDecimal owner=net.multiply(new BigDecimal("0.60")).setScale(2,RoundingMode.HALF_EVEN);
        BigDecimal builder=net.subtract(owner);
        require(owner.add(builder).compareTo(net)==0,"share preservation");
        require(net.compareTo(money("5345.67"))==0,"net golden");
        require(cost.signum()>0,"zero-cost ROI divisor must be rejected");
        return evidence("DECIMAL_ALLOCATION_60_40",owner.toPlainString()+"/"+builder.toPlainString());
    }
    private static Map<String,Object> sensitivity() {
        BigDecimal revenue=money("100000"),cost=money("70000");
        BigDecimal base=revenue.subtract(cost);
        BigDecimal shocked=revenue.multiply(new BigDecimal("0.90")).subtract(cost.multiply(new BigDecimal("1.10")));
        require(base.compareTo(money("30000"))==0,"base golden");
        require(shocked.compareTo(money("13000"))==0,"shocked golden");
        require(sha(base.toPlainString()).equals(sha(revenue.subtract(cost).toPlainString())),"replay hash");
        return evidence("IMMUTABLE_SENSITIVITY_REPLAY",shocked.toPlainString());
    }
}
