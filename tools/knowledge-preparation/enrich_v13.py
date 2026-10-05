import os,re,sys,json,hashlib,uuid,unicodedata
import psycopg
from psycopg.types.json import Jsonb

VERSION="knowledge-preparation-v1.3"

def U(s):
    return s.encode("ascii").decode("unicode_escape")

TOK={
"article":U(r"\u0645\u0627\u062f\u0647"),
"note":U(r"\u062a\u0628\u0635\u0631\u0647"),
"clause":U(r"\u0628\u0646\u062f"),
"municipality":U(r"\u0634\u0647\u0631\u062f\u0627\u0631\u06cc"),
"planning":U(r"\u0634\u0647\u0631\u0633\u0627\u0632\u06cc"),
"construction":U(r"\u0633\u0627\u062e\u062a"),
"land":U(r"\u0627\u0631\u0627\u0636\u06cc"),
"density":U(r"\u062a\u0631\u0627\u06a9\u0645"),
"use":U(r"\u06a9\u0627\u0631\u0628\u0631\u06cc"),
"allowed":U(r"\u0645\u062c\u0627\u0632"),
"must":U(r"\u0628\u0627\u06cc\u062f"),
"required":U(r"\u0627\u0644\u0632\u0627\u0645"),
"forbidden":U(r"\u0645\u0645\u0646\u0648\u0639"),
"minimum":U(r"\u062d\u062f\u0627\u0642\u0644"),
"maximum":U(r"\u062d\u062f\u0627\u06a9\u062b\u0631"),
"percent":U(r"\u062f\u0631\u0635\u062f"),
"meter":U(r"\u0645\u062a\u0631"),
"sqm":U(r"\u0645\u062a\u0631\u0645\u0631\u0628\u0639"),
"floor":U(r"\u0637\u0628\u0642\u0647"),
"commission":U(r"\u06a9\u0645\u06cc\u0633\u06cc\u0648\u0646"),
"council":U(r"\u0634\u0648\u0631\u0627"),
}

DOMAIN_PATTERNS={
"urban_planning":[TOK["planning"],TOK["density"],TOK["use"],TOK["land"]],
"municipal":[TOK["municipality"],TOK["commission"],TOK["council"]],
"construction":[TOK["construction"],TOK["meter"],TOK["floor"]],
"legal":[TOK["article"],TOK["note"],TOK["clause"]],
}
TYPE_PATTERNS={
"prohibition":[TOK["forbidden"]],
"permission":[TOK["allowed"]],
"requirement":[TOK["must"],TOK["required"],TOK["minimum"],TOK["maximum"]],
"threshold":[TOK["minimum"],TOK["maximum"],TOK["percent"]],
"reference":[TOK["article"],TOK["note"],TOK["clause"]],
}
MOJI=set(chr(x) for x in list(range(0x00C0,0x0100))+list(range(0x0080,0x00A0)))

def badness(s):
    return sum(ch in MOJI for ch in s) + 3*s.count("\ufffd")

def fa_count(s):
    return sum(0x0600 <= ord(ch) <= 0x06ff for ch in s)

def repair_segment(seg):
    best=seg
    bestscore=(badness(seg),-fa_count(seg))
    cur=seg
    for _ in range(3):
        candidates=[]
        for enc in ("latin1","cp1252"):
            try:
                candidates.append(cur.encode(enc).decode("utf-8"))
            except (UnicodeEncodeError,UnicodeDecodeError):
                pass
        if not candidates: break
        nxt=min(candidates,key=lambda x:(badness(x),-fa_count(x)))
        score=(badness(nxt),-fa_count(nxt))
        if score >= bestscore: break
        best,bestscore=nxt,score
        cur=nxt
    return best

def repair_mixed(s):
    # Repair suspicious runs only; preserve already-correct Persian.
    parts=re.split(r'((?:[\u0080-\u00ff]|[^\x00-\x7f]){2,})',s)
    out=[]
    for part in parts:
        if badness(part)>=2:
            out.append(repair_segment(part))
        else:
            out.append(part)
    return "".join(out)

def norm(s):
    s=repair_mixed(s or "")
    s=unicodedata.normalize("NFKC",s)
    s=s.replace(U(r"\u064a"),U(r"\u06cc")).replace(U(r"\u0649"),U(r"\u06cc")).replace(U(r"\u0643"),U(r"\u06a9"))
    s=s.replace("\u200c"," ")
    s=re.sub(r"[ \t]+"," ",s)
    s=re.sub(r"\n{3,}","\n\n",s)
    return s.strip()

def connect():
    url=os.environ.get("DB_URL")
    user=os.environ.get("DB_USERNAME")
    password=os.environ.get("DB_PASSWORD")
    if not url or not user or not password:
        raise RuntimeError("DB_URL, DB_USERNAME and DB_PASSWORD are required")
    return psycopg.connect(url.replace("jdbc:",""),user=user,password=password)

def cols(c,t):
    return {r[0]:r for r in c.execute("""select column_name,data_type,is_nullable,column_default
      from information_schema.columns where table_schema=current_schema() and table_name=%s""",(t,))}

def insert(c,t,data):
    cs=cols(c,t)
    d={k:v for k,v in data.items() if k in cs and v is not None}
    required=[k for k,r in cs.items() if r[2]=="NO" and r[3] is None and k not in d]
    if required: raise RuntimeError(f"{t}: unmapped required columns: {required}")
    names=list(d)
    q="insert into "+t+" ("+",".join(names)+") values ("+",".join(["%s"]*len(names))+")"
    vals=[]
    for k in names:
        v=d[k]
        if cs[k][1] in ("json","jsonb") and not isinstance(v,Jsonb): v=Jsonb(v)
        vals.append(v)
    c.execute(q,vals)

def labels(text,patterns):
    low=text.casefold()
    return [k for k,ps in patterns.items() if any(p.casefold() in low for p in ps)]

def main():
    rid=sys.argv[1] if len(sys.argv)>1 else None
    c=connect()
    if not rid:
        r=c.execute("""select id from knowledge_pipeline_run where status='COMPLETED'
          and stage='PREPARED' order by completed_at desc nulls last, started_at desc limit 1""").fetchone()
        if not r: raise RuntimeError("No PREPARED run found")
        rid=str(r[0])
    run=c.execute("select id,correlation_id from knowledge_pipeline_run where id=%s",(rid,)).fetchone()
    if not run: raise RuntimeError("Run not found: "+rid)
    nodes=c.execute("""select id,source_document_id,correlation_id,normalized_text,importance_score
       from knowledge_document_node where run_id=%s order by node_index""",(rid,)).fetchall()
    print(f"Enrichment v1.3 run={rid} nodes={len(nodes)}")
    # Idempotent rerun for this run.
    for t in ("knowledge_node_classification","knowledge_node_entity","knowledge_node_reference","knowledge_preparation_candidate"):
        c.execute("delete from "+t+" where run_id=%s",(rid,))
    nclass=nentity=nref=ncand=repaired=0
    before_bad=after_bad=0
    for nid,sdoc,corr,text,importance in nodes:
        raw=text or ""
        before_bad += badness(raw)
        clean=norm(raw)
        after_bad += badness(clean)
        if clean != raw:
            repaired+=1
            c.execute("update knowledge_document_node set normalized_text=%s where id=%s",(clean,nid))
        ds=labels(clean,DOMAIN_PATTERNS)
        ks=labels(clean,TYPE_PATTERNS)
        for d in ds:
            insert(c,"knowledge_node_classification",dict(run_id=rid,node_id=nid,classification_type="DOMAIN",label=d,confidence=.88,classifier_version=VERSION,metadata={"method":"rule-v13"})); nclass+=1
        for k in ks:
            insert(c,"knowledge_node_classification",dict(run_id=rid,node_id=nid,classification_type="KNOWLEDGE_TYPE",label=k,confidence=.88,classifier_version=VERSION,metadata={"method":"rule-v13"})); nclass+=1
        # Legal references: article/note/clause + nearby number.
        legal=[]
        for typ,tok in (("ARTICLE",TOK["article"]),("NOTE",TOK["note"]),("CLAUSE",TOK["clause"])):
            for m in re.finditer(re.escape(tok)+r"\s*[\(\[]?\s*([0-9\u06f0-\u06f9\u0660-\u0669]+)",clean):
                val=m.group(0)
                legal.append(val)
                insert(c,"knowledge_node_reference",dict(run_id=rid,node_id=nid,reference_type=typ,reference_text=val,normalized_reference=norm(val),confidence=.92,metadata={"method":"rule-v13"})); nref+=1
        # Measurements.
        unit_alt="|".join(map(re.escape,[TOK["sqm"],TOK["meter"],TOK["percent"],TOK["floor"]]))
        for m in re.finditer(r"([0-9\u06f0-\u06f9\u0660-\u0669]+(?:[.,][0-9\u06f0-\u06f9\u0660-\u0669]+)?)\s*("+unit_alt+r")",clean):
            insert(c,"knowledge_node_entity",dict(run_id=rid,node_id=nid,entity_type="MEASUREMENT",value=m.group(0),normalized_value=norm(m.group(1)),unit=m.group(2),confidence=.9,start_offset=m.start(),end_offset=m.end(),metadata={"method":"rule-v13"})); nentity+=1
        signal=bool(ks or legal or re.search(r"[0-9\u06f0-\u06f9\u0660-\u0669]",clean))
        if len(clean)>=25 and signal and (ds or ks):
            kt=ks[0] if ks else "reference"
            dom=ds[0] if ds else None
            h=hashlib.sha256(clean.encode("utf-8")).hexdigest()
            insert(c,"knowledge_preparation_candidate",dict(run_id=rid,source_document_id=sdoc,node_id=nid,correlation_id=corr or run[1],candidate_type=kt,statement=clean,statement_hash=h,domain=dom,topics=ds,applicability={},authority={},legal_refs=legal,confidence=.82,importance_score=float(importance or 0),status="PREPARED",provenance={"engine":VERSION,"node_id":str(nid)})); ncand+=1
    stats=dict(nodes=len(nodes),repaired_nodes=repaired,bad_chars_before=before_bad,bad_chars_after=after_bad,classifications=nclass,entities=nentity,references=nref,candidates=ncand,engine=VERSION)
    failures=[]
    if len(nodes)>=20 and nclass==0: failures.append("classification=0")
    if len(nodes)>=20 and ncand==0: failures.append("candidates=0")
    if after_bad > max(5,int(before_bad*.20)): failures.append(f"mojibake remains high: {after_bad}")
    if failures:
        c.rollback()
        raise RuntimeError("Quality gate failed: "+", ".join(failures))
    c.execute("""update knowledge_pipeline_run set stage='ENRICHED',pipeline_version=%s,
      summary=coalesce(summary,'{}'::jsonb)||%s::jsonb where id=%s""",(VERSION,json.dumps(stats),rid))
    c.commit()
    print(json.dumps(stats,ensure_ascii=False,indent=2))
    c.close()
if __name__=="__main__": main()
