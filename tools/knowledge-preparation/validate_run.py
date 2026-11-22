#!/usr/bin/env python3
from __future__ import annotations
import argparse, json, os, sys, uuid, zipfile
from collections import Counter
from pathlib import Path
import psycopg

def db():
    url=os.environ.get("DB_URL","").replace("jdbc:","",1)
    user=os.environ.get("DB_USERNAME")
    pwd=os.environ.get("DB_PASSWORD")
    if not url or not user or not pwd:
        raise RuntimeError("DB_URL, DB_USERNAME and DB_PASSWORD are required")
    return psycopg.connect(url,user=user,password=pwd)

def scalar(c,sql,args):
    c.execute(sql,args); return c.fetchone()[0]

def rows(c,sql,args):
    c.execute(sql,args); return c.fetchall()

def main():
    ap=argparse.ArgumentParser(description="Validate a SakhtYar Knowledge Preparation run")
    ap.add_argument("--run-id",required=True,type=uuid.UUID)
    ap.add_argument("--samples",type=int,default=8)
    a=ap.parse_args()
    checks=[]; warnings=[]

    with db() as conn, conn.cursor() as c:
        c.execute("""select id,run_code,status,stage,pipeline_version,workflow_id,correlation_id,
                            statistics,created_at,started_at,finished_at,error_message
                     from knowledge_pipeline_run where id=%s""",(a.run_id,))
        run=c.fetchone()
        if not run: raise SystemExit("FAIL: run not found")
        keys=["id","run_code","status","stage","pipeline_version","workflow_id","correlation_id",
              "statistics","created_at","started_at","finished_at","error_message"]
        info=dict(zip(keys,run))
        counts={}
        queries={
          "documents":"select count(*) from knowledge_pipeline_run_document where run_id=%s",
          "pages":"select count(*) from knowledge_source_page where run_id=%s",
          "nodes":"select count(*) from knowledge_document_node where run_id=%s",
          "classifications":"select count(*) from knowledge_node_classification where run_id=%s",
          "references":"select count(*) from knowledge_node_reference where run_id=%s",
          "entities":"select count(*) from knowledge_node_entity where run_id=%s",
          "candidates":"select count(*) from knowledge_preparation_candidate where run_id=%s",
          "lineage":"select count(*) from knowledge_lineage_edge where run_id=%s",
          "dedup_clusters":"select count(*) from knowledge_dedup_cluster where run_id=%s",
          "steps":"select count(*) from knowledge_pipeline_step_run where run_id=%s",
          "events":"select count(*) from knowledge_pipeline_event where run_id=%s",
          "exports":"select count(*) from knowledge_llm_export where run_id=%s",
        }
        for k,q in queries.items(): counts[k]=scalar(c,q,(a.run_id,))

        suspect=scalar(c, """select coalesce(sum((metadata->>'suspect_chars')::int),0)
                            from knowledge_document_node where run_id=%s""",(a.run_id,))
        low_persian=scalar(c, """select count(*) from knowledge_document_node
                                where run_id=%s and length(normalized_text)>80
                                and coalesce((metadata->>'persian_ratio')::numeric,0)<0.05""",(a.run_id,))
        orphan_candidates=scalar(c, """select count(*) from knowledge_preparation_candidate pc
                                      left join knowledge_document_node n on n.id=pc.node_id
                                      where pc.run_id=%s and n.id is null""",(a.run_id,))
        bad_steps=scalar(c, """select count(*) from knowledge_pipeline_step_run
                              where run_id=%s and status not in ('COMPLETED','SKIPPED')""",(a.run_id,))
        class_dist=rows(c, """select classification_type,label,count(*) from knowledge_node_classification
                             where run_id=%s group by classification_type,label order by count(*) desc""",(a.run_id,))
        cand_dist=rows(c, """select candidate_type,count(*) from knowledge_preparation_candidate
                            where run_id=%s group by candidate_type order by count(*) desc""",(a.run_id,))
        samples=rows(c, """select candidate_type,domain,page_from,left(statement,450)
                           from knowledge_preparation_candidate pc
                           join knowledge_document_node n on n.id=pc.node_id
                           where pc.run_id=%s order by pc.importance_score desc nulls last limit %s""",(a.run_id,a.samples))
        exports=rows(c,"""select output_path,sha256,size_bytes from knowledge_llm_export where run_id=%s order by created_at desc""",(a.run_id,))

    def check(name,ok,detail):
        checks.append((name,ok,detail))

    check("run_status",info["status"]=="COMPLETED" and info["stage"]=="ENRICHED",f'{info["status"]}/{info["stage"]}')
    check("documents",counts["documents"]>0,str(counts["documents"]))
    check("nodes",counts["nodes"]>0,str(counts["nodes"]))
    check("classifications",counts["classifications"]>0,str(counts["classifications"]))
    check("candidates",counts["candidates"]>0,str(counts["candidates"]))
    check("lineage",counts["lineage"]>=counts["nodes"],f'{counts["lineage"]} for {counts["nodes"]} nodes')
    check("orphan_candidates",orphan_candidates==0,str(orphan_candidates))
    check("steps",bad_steps==0,f'bad={bad_steps}, total={counts["steps"]}')
    check("encoding",suspect<=max(10,counts["nodes"]//20),f'suspect_chars={suspect}, low_persian_nodes={low_persian}')
    check("export_record",counts["exports"]>0,str(counts["exports"]))

    export_integrity=True
    export_notes=[]
    for p,expected,size in exports:
        fp=Path(p)
        if not fp.is_file():
            export_integrity=False; export_notes.append(f"missing:{p}"); continue
        import hashlib
        digest=hashlib.sha256(fp.read_bytes()).hexdigest()
        if expected and digest!=expected:
            export_integrity=False; export_notes.append(f"sha-mismatch:{p}")
        try:
            with zipfile.ZipFile(fp) as z:
                names=set(z.namelist())
                required={"manifest.json","command.txt","source-documents.jsonl","nodes.jsonl","knowledge-candidates.jsonl"}
                missing=required-names
                if missing:
                    export_integrity=False; export_notes.append("zip-missing:"+",".join(sorted(missing)))
                manifest=json.loads(z.read("manifest.json"))
                if str(manifest.get("run_id"))!=str(a.run_id):
                    export_integrity=False; export_notes.append("manifest-run-id-mismatch")
        except Exception as e:
            export_integrity=False; export_notes.append("zip-error:"+str(e))
    check("export_integrity",export_integrity,"; ".join(export_notes) or "OK")

    print("\n=== SakhtYar Knowledge Run Validation ===")
    print(f'Run       : {info["id"]}')
    print(f'Code      : {info["run_code"]}')
    print(f'Pipeline  : {info["pipeline_version"]}')
    print(f'Status    : {info["status"]} / {info["stage"]}')
    print("\nCOUNTS")
    for k,v in counts.items(): print(f"  {k:18} {v}")
    print("\nCHECKS")
    for name,ok,detail in checks: print(f'  {"PASS" if ok else "FAIL":4}  {name:22} {detail}')
    print("\nCLASSIFICATION DISTRIBUTION")
    for typ,label,n in class_dist[:20]: print(f"  {typ:16} {label:24} {n}")
    print("\nCANDIDATE DISTRIBUTION")
    for typ,n in cand_dist: print(f"  {typ:24} {n}")
    print("\nTOP CANDIDATE SAMPLES")
    for i,(typ,domain,page,text) in enumerate(samples,1):
        print(f"\n[{i}] type={typ} domain={domain} page={page}")
        print("    "+str(text).replace("\n"," ")[:450])

    failed=[x for x in checks if not x[1]]
    print("\nRESULT:", "FAIL" if failed else "PASS")
    return 2 if failed else 0

if __name__=="__main__":
    raise SystemExit(main())
