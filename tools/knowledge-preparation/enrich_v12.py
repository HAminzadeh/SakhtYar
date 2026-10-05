import os, re, json, uuid, hashlib, sys
from decimal import Decimal
import psycopg
from psycopg import sql

VERSION = "knowledge-preparation-v1.2"

DOMAIN_PATTERNS = {
    "urban_planning": ["Ø·Ø±Ø­ ØªÙØµÛŒÙ„ÛŒ","Ø·Ø±Ø­ Ø¬Ø§Ù…Ø¹","Ù¾Ù‡Ù†Ù‡","ØªØ±Ø§Ú©Ù…","Ú©Ø§Ø±Ø¨Ø±ÛŒ","Ø³Ø·Ø­ Ø§Ø´ØºØ§Ù„","Ø¨Ø±","Ø§ØµÙ„Ø§Ø­ÛŒ","Ø¹Ù‚Ø¨ Ù†Ø´ÛŒÙ†ÛŒ","Ø§Ø±ØªÙØ§Ø¹","Ø·Ø¨Ù‚Ù‡"],
    "municipal": ["Ø´Ù‡Ø±Ø¯Ø§Ø±ÛŒ","Ú©Ù…ÛŒØ³ÛŒÙˆÙ†","Ù¾Ø±ÙˆØ§Ù†Ù‡","Ù¾Ø§ÛŒØ§Ù† Ú©Ø§Ø±","Ø¹ÙˆØ§Ø±Ø¶","ØªØ®Ù„Ù Ø³Ø§Ø®ØªÙ…Ø§Ù†ÛŒ"],
    "construction": ["Ø³Ø§Ø®ØªÙ…Ø§Ù†","Ø³Ø§Ø®Øª","Ø§Ø­Ø¯Ø§Ø«","Ø³Ø§Ø²Ù‡","Ù…Ø¹Ù…Ø§Ø±ÛŒ","ØªØ£Ø³ÛŒØ³Ø§Øª","ØªØ§Ø³ÛŒØ³Ø§Øª","Ù…Ù‚Ø±Ø±Ø§Øª Ù…Ù„ÛŒ Ø³Ø§Ø®ØªÙ…Ø§Ù†"],
    "registration": ["Ø«Ø¨Øª Ø§Ø³Ù†Ø§Ø¯","Ø³Ù†Ø¯ Ù…Ø§Ù„Ú©ÛŒØª","ØªÙÚ©ÛŒÚ©","Ø§ÙØ±Ø§Ø²","Ù¾Ù„Ø§Ú© Ø«Ø¨ØªÛŒ","Ø«Ø¨Øª Ù…Ù„Ú©"],
    "property": ["Ù…Ø§Ù„Ú©","Ù…Ø§Ù„Ú©ÛŒØª","Ù…Ù„Ú©","Ø²Ù…ÛŒÙ†","Ù‚Ø·Ø¹Ù‡","Ø¹Ø±ØµÙ‡","Ø§Ø¹ÛŒØ§Ù†"],
    "fire": ["Ø¢ØªØ´ Ù†Ø´Ø§Ù†ÛŒ","Ø¢ØªØ´â€ŒÙ†Ø´Ø§Ù†ÛŒ","Ø­Ø±ÛŒÙ‚","Ù…Ù‚Ø§ÙˆÙ…Øª Ø­Ø±ÛŒÙ‚"],
    "safety": ["Ø§ÛŒÙ…Ù†ÛŒ","Ø®Ø·Ø±","Ø­ÙØ§Ø¸Øª","Ø­Ø§Ø¯Ø«Ù‡"],
    "legal": ["Ù‚Ø§Ù†ÙˆÙ†","Ø¢ÛŒÛŒÙ† Ù†Ø§Ù…Ù‡","Ø¢ÛŒÛŒÙ†â€ŒÙ†Ø§Ù…Ù‡","Ù…ØµÙˆØ¨Ù‡","Ù…Ø§Ø¯Ù‡","ØªØ¨ØµØ±Ù‡","Ø¨Ù†Ø¯","Ø§Ù„Ø²Ø§Ù…"]
}
TYPE_PATTERNS = {
    "prohibition": ["Ù…Ù…Ù†ÙˆØ¹","Ù…Ø¬Ø§Ø² Ù†ÛŒØ³Øª","Ù†Ø¨Ø§ÛŒØ¯","Ù…Ù…Ù†ÙˆØ¹ Ø§Ø³Øª"],
    "permission": ["Ù…Ø¬Ø§Ø² Ø§Ø³Øª","Ø¨Ù„Ø§Ù…Ø§Ù†Ø¹","Ù…ÛŒ ØªÙˆØ§Ù†Ø¯","Ù…ÛŒâ€ŒØªÙˆØ§Ù†Ø¯","Ø§Ø¬Ø§Ø²Ù‡"],
    "requirement": ["Ø§Ù„Ø²Ø§Ù…","Ø§Ù„Ø²Ø§Ù…ÛŒ","Ø¨Ø§ÛŒØ¯","Ù…ÙˆØ¸Ù","Ù…Ú©Ù„Ù","Ø¶Ø±ÙˆØ±ÛŒ Ø§Ø³Øª"],
    "exception": ["Ø§Ø³ØªØ«Ù†Ø§","Ù…Ø³ØªØ«Ù†ÛŒ","Ø¨Ù‡ Ø§Ø³ØªØ«Ù†Ø§ÛŒ","Ø¨Ù‡â€ŒØ§Ø³ØªØ«Ù†Ø§ÛŒ"],
    "condition": ["Ù…Ø´Ø±ÙˆØ·","Ø¯Ø± ØµÙˆØ±ØªÛŒ Ú©Ù‡","Ø¯Ø±ØµÙˆØ±Øª","Ú†Ù†Ø§Ù†Ú†Ù‡","Ø¨Ù‡ Ø´Ø±Ø·"],
    "definition": ["Ø¹Ø¨Ø§Ø±Øª Ø§Ø³Øª Ø§Ø²","ØªØ¹Ø±ÛŒÙ Ù…ÛŒ Ø´ÙˆØ¯","ØªØ¹Ø±ÛŒÙ Ù…ÛŒâ€ŒØ´ÙˆØ¯","Ù…Ù†Ø¸ÙˆØ± Ø§Ø²"],
    "threshold": ["Ø­Ø¯Ø§Ú©Ø«Ø±","Ø­Ø¯Ø§Ù‚Ù„","Ø¨ÛŒØ´ Ø§Ø²","Ú©Ù…ØªØ± Ø§Ø²","ØªØ§ Ø³Ù‚Ù"],
    "penalty": ["Ø¬Ø±ÛŒÙ…Ù‡","Ù…Ø¬Ø§Ø²Ø§Øª","ØªØ®Ù„Ù"],
    "procedure": ["Ø¯Ø±Ø®ÙˆØ§Ø³Øª","Ù…Ø±Ø§Ø­Ù„","ÙØ±Ø¢ÛŒÙ†Ø¯","ÙØ±Ø§ÛŒÙ†Ø¯","Ø§Ø±Ø§Ø¦Ù‡","ØµØ¯ÙˆØ±","ØªØ£ÛŒÛŒØ¯","ØªØµÙˆÛŒØ¨"],
    "reference": ["Ù…Ø·Ø§Ø¨Ù‚ Ù…Ø§Ø¯Ù‡","Ù…ÙˆØ¶ÙˆØ¹ Ù…Ø§Ø¯Ù‡","Ø¨Ø± Ø§Ø³Ø§Ø³ Ù…Ø§Ø¯Ù‡","Ø¨Ø±Ø§Ø³Ø§Ø³ Ù…Ø§Ø¯Ù‡","Ø·Ø¨Ù‚ Ù…Ø§Ø¯Ù‡","ØªØ¨ØµØ±Ù‡","Ø¨Ù†Ø¯"]
}
REF_RE = re.compile(r'(?P<kind>Ù…Ø§Ø¯Ù‡|ØªØ¨ØµØ±Ù‡|Ø¨Ù†Ø¯|ÙØµÙ„|Ø¨Ø®Ø´)\s*[Â«"(\[]?\s*(?P<num>[Û°-Û¹Ù -Ù©0-9]+(?:[-Ù€/][Û°-Û¹Ù -Ù©0-9]+)?)', re.U)
NUM_RE = re.compile(r'(?P<num>[Û°-Û¹Ù -Ù©0-9]+(?:[.,Ù«][Û°-Û¹Ù -Ù©0-9]+)?)\s*(?P<unit>Ù…ØªØ±Ù…Ø±Ø¨Ø¹|Ù…ØªØ± Ù…Ø±Ø¨Ø¹|Ù…ØªØ±|Ø³Ø§Ù†ØªÛŒ[â€Œ ]?Ù…ØªØ±|Ø¯Ø±ØµØ¯|Ùª|Ø·Ø¨Ù‚Ù‡|ÙˆØ§Ø­Ø¯|Ù‡Ú©ØªØ§Ø±)', re.U)

def dburl():
    u=os.getenv("DB_URL")
    if not u: raise RuntimeError("DB_URL is required")
    return u[5:] if u.startswith("jdbc:") else u

def connect():
    user=os.getenv("DB_USERNAME")
    pwd=os.getenv("DB_PASSWORD")
    if not user: raise RuntimeError("DB_USERNAME is required")
    if not pwd: raise RuntimeError("DB_PASSWORD is required")
    return psycopg.connect(dburl(), user=user, password=pwd)

def cols(conn, table):
    rows=conn.execute("""
      select column_name, is_nullable, column_default, data_type, udt_name
      from information_schema.columns
      where table_schema=current_schema() and table_name=%s
      order by ordinal_position
    """,(table,)).fetchall()
    return {r[0]: {"nullable":r[1]=="YES","default":r[2],"type":r[3],"udt":r[4]} for r in rows}

def pick(c, *names):
    return next((x for x in names if x in c), None)

def j(v): return json.dumps(v, ensure_ascii=False)

def insert_adaptive(conn, table, values):
    c=cols(conn,table)
    data={k:v for k,v in values.items() if k in c and v is not None}
    # Supply UUID ids when required and not defaulted.
    if "id" in c and "id" not in data and not c["id"]["default"]:
        data["id"]=uuid.uuid4()
    missing=[n for n,m in c.items()
             if not m["nullable"] and m["default"] is None and n not in data]
    if missing:
        raise RuntimeError(f"{table}: unmapped required columns: {missing}; available={list(c)}")
    q=sql.SQL("insert into {} ({}) values ({})").format(
        sql.Identifier(table),
        sql.SQL(",").join(map(sql.Identifier,data.keys())),
        sql.SQL(",").join(sql.Placeholder()*len(data))
    )
    conn.execute(q, tuple(data.values()))

def norm(s):
    return re.sub(r'\s+',' ',(s or '').replace('ÙŠ','ÛŒ').replace('Ùƒ','Ú©')).strip()

def domains(t):
    scores=[]
    for d, pats in DOMAIN_PATTERNS.items():
        n=sum(1 for p in pats if p in t)
        if n: scores.append((d,min(.99,.58+n*.08)))
    return sorted(scores,key=lambda x:x[1],reverse=True)

def types(t):
    found=[]
    for k,pats in TYPE_PATTERNS.items():
        n=sum(1 for p in pats if p in t)
        if n: found.append((k,min(.99,.62+n*.08)))
    return sorted(found,key=lambda x:x[1],reverse=True)

def node_rows(conn, run_id):
    c=cols(conn,"knowledge_document_node")
    textcol=pick(c,"normalized_text","node_text","text","content","raw_text")
    if not textcol: raise RuntimeError(f"Cannot find node text column. columns={list(c)}")
    idcol=pick(c,"id","node_id")
    if not idcol: raise RuntimeError("Cannot find node id column")
    if "run_id" not in c: raise RuntimeError("knowledge_document_node.run_id missing")
    q=sql.SQL("select {}, {} from knowledge_document_node where run_id=%s").format(
        sql.Identifier(idcol),sql.Identifier(textcol))
    return conn.execute(q,(run_id,)).fetchall()

def latest_run(conn):
    return conn.execute("""
      select id from knowledge_pipeline_run
      where status='COMPLETED' and stage='PREPARED'
      order by started_at desc limit 1
    """).fetchone()

def count_for_run(conn, table, run_id):
    c=cols(conn,table)
    if "run_id" in c:
        return conn.execute(sql.SQL("select count(*) from {} where run_id=%s").format(sql.Identifier(table)),(run_id,)).fetchone()[0]
    if "node_id" in c:
        return conn.execute(sql.SQL("""
          select count(*) from {} x join knowledge_document_node n on n.id=x.node_id where n.run_id=%s
        """).format(sql.Identifier(table)),(run_id,)).fetchone()[0]
    return 0

def main():
    conn=connect()
    try:
        rr=latest_run(conn)
        if not rr: raise RuntimeError("No COMPLETED/PREPARED run found to enrich")
        run_id=rr[0]
        nodes=node_rows(conn,run_id)
        print(f"Enrichment v1.2 run={run_id} nodes={len(nodes)}")

        # Idempotent for this run: only enrich when the legacy run has no enrichment.
        existing=sum(count_for_run(conn,t,run_id) for t in [
            "knowledge_node_classification","knowledge_node_entity",
            "knowledge_node_reference","knowledge_preparation_candidate"])
        if existing:
            print(f"Enrichment already exists ({existing} rows); leaving it unchanged.")
            return

        nclass=nentity=nref=ncand=0
        for node_id, raw in nodes:
            t=norm(raw)
            if not t: continue
            ds=domains(t); ts=types(t)
            refs=list(REF_RE.finditer(t))
            nums=list(NUM_RE.finditer(t))

            # Classification: multi-label domains + knowledge types.
            for d,conf in ds[:4]:
                insert_adaptive(conn,"knowledge_node_classification",{
                    "node_id":node_id,"run_id":run_id,
                    "classification_type":"DOMAIN","label":d,"domain":d,
                    "confidence":conf,"classifier_version":VERSION,
                    "model_version":VERSION,"metadata":j({"source":"rule-v1.2"})
                }); nclass+=1
            for k,conf in ts[:4]:
                insert_adaptive(conn,"knowledge_node_classification",{
                    "node_id":node_id,"run_id":run_id,
                    "classification_type":"KNOWLEDGE_TYPE","label":k,
                    "knowledge_type":k,"confidence":conf,
                    "classifier_version":VERSION,"model_version":VERSION,
                    "metadata":j({"source":"rule-v1.2"})
                }); nclass+=1

            for m in refs:
                val=m.group(0)
                insert_adaptive(conn,"knowledge_node_reference",{
                    "node_id":node_id,"run_id":run_id,
                    "reference_type":m.group("kind"),"ref_type":m.group("kind"),
                    "reference_text":val,"raw_value":val,
                    "normalized_reference":norm(val),"normalized_value":norm(val),
                    "target_key":norm(val),"confidence":Decimal("0.90"),
                    "metadata":j({"source":"regex-v1.2"})
                }); nref+=1

            for m in nums:
                val=m.group(0)
                insert_adaptive(conn,"knowledge_node_entity",{
                    "node_id":node_id,"run_id":run_id,
                    "entity_type":"MEASUREMENT","type":"MEASUREMENT",
                    "entity_value":val,"value":val,"raw_value":val,
                    "normalized_value":norm(val),"unit":m.group("unit"),
                    "confidence":Decimal("0.88"),
                    "metadata":j({"source":"regex-v1.2"})
                }); nentity+=1

            # Candidate gate: regulatory signal OR domain + numeric/reference signal.
            signal = bool(ts or refs or (ds and nums))
            if signal and len(t) >= 25:
                kt=ts[0][0] if ts else ("reference" if refs else "threshold")
                dom=ds[0][0] if ds else "legal"
                conf=max([x[1] for x in ts+ds] or [.60])
                importance=min(.99,.45 + .08*len(refs) + .05*len(nums) + .08*len(ts) + .04*len(ds))
                h=hashlib.sha256(t.encode("utf-8")).hexdigest()
                insert_adaptive(conn,"knowledge_preparation_candidate",{
                    "run_id":run_id,"node_id":node_id,
                    "candidate_type":kt,"knowledge_type":kt,
                    "statement":t,"statement_text":t,"text":t,
                    "statement_hash":h,"text_hash":h,
                    "domain":dom,"confidence":conf,
                    "importance_score":importance,"importance":importance,
                    "status":"PREPARED",
                    "evidence_text":t,
                    "provenance":j({"node_id":str(node_id),"run_id":str(run_id),"engine":VERSION}),
                    "metadata":j({"engine":VERSION})
                }); ncand+=1

        stats={"classifications":nclass,"entities":nentity,"references":nref,"candidates":ncand,"nodes":len(nodes),"engine":VERSION}

        # Quality gate: legal/regulatory corpus with hundreds of nodes must not silently pass empty.
        failures=[]
        if len(nodes) >= 20 and nclass == 0: failures.append("classification=0")
        if len(nodes) >= 20 and ncand == 0: failures.append("candidates=0")
        if failures:
            conn.execute("""
              update knowledge_pipeline_run
              set status='FAILED', stage='ENRICHMENT_FAILED',
                  error_message=%s,
                  statistics=coalesce(statistics,'{}'::jsonb) || %s::jsonb
              where id=%s
            """,("Quality gate failed: "+", ".join(failures),j(stats),run_id))
            conn.commit()
            raise RuntimeError("Quality gate failed: "+", ".join(failures))

        conn.execute("""
          update knowledge_pipeline_run
          set stage='ENRICHED',
              pipeline_version=%s,
              statistics=coalesce(statistics,'{}'::jsonb) || %s::jsonb
          where id=%s
        """,(VERSION,j(stats),run_id))
        conn.commit()
        print("Enrichment completed:", stats)
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()

if __name__=="__main__":
    main()