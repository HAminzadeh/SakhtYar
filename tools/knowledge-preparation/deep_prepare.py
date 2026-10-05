#!/usr/bin/env python3
import argparse, hashlib, json, os, re, sys, uuid, zipfile
from pathlib import Path
from datetime import datetime, timezone
try:
    import psycopg
except ImportError:
    print('Missing psycopg. Run bootstrap-knowledge-preparation.ps1 first.', file=sys.stderr); raise
try:
    import pymupdf
except ImportError:
    pymupdf = None

PIPELINE_VERSION='sakhtyar-preparation-1.0'
SUPPORTED={'.pdf','.txt','.md','.html','.htm','.json','.jsonl','.csv'}
DOMAINS={
 'urban_planning':['Ã˜ÂªÃ˜Â±Ã˜Â§ÃšÂ©Ã™â€¦','ÃšÂ©Ã˜Â§Ã˜Â±Ã˜Â¨Ã˜Â±Ã›Å’','Ã˜Â·Ã˜Â±Ã˜Â­ Ã˜ÂªÃ™ÂÃ˜ÂµÃ›Å’Ã™â€žÃ›Å’','Ã™Â¾Ã™â€¡Ã™â€ Ã™â€¡','Ã˜Â³Ã˜Â·Ã˜Â­ Ã˜Â§Ã˜Â´Ã˜ÂºÃ˜Â§Ã™â€ž','Ã˜Â¹Ã™â€šÃ˜Â¨ Ã™â€ Ã˜Â´Ã›Å’Ã™â€ Ã›Å’','Ã˜Â¨Ã˜Â± Ã˜Â§Ã˜ÂµÃ™â€žÃ˜Â§Ã˜Â­Ã›Å’'],
 'municipal':['Ã˜Â´Ã™â€¡Ã˜Â±Ã˜Â¯Ã˜Â§Ã˜Â±Ã›Å’','Ã™Â¾Ã˜Â±Ã™Ë†Ã˜Â§Ã™â€ Ã™â€¡','Ã™Â¾Ã˜Â§Ã›Å’Ã˜Â§Ã™â€  ÃšÂ©Ã˜Â§Ã˜Â±','Ã˜Â¹Ã™Ë†Ã˜Â§Ã˜Â±Ã˜Â¶','ÃšÂ©Ã™â€¦Ã›Å’Ã˜Â³Ã›Å’Ã™Ë†Ã™â€ '],
 'registration':['Ã˜Â«Ã˜Â¨Ã˜Âª Ã˜Â§Ã˜Â³Ã™â€ Ã˜Â§Ã˜Â¯','Ã˜Â³Ã™â€ Ã˜Â¯ Ã™â€¦Ã˜Â§Ã™â€žÃšÂ©Ã›Å’Ã˜Âª','Ã˜ÂªÃ™ÂÃšÂ©Ã›Å’ÃšÂ©','Ã˜Â§Ã™ÂÃ˜Â±Ã˜Â§Ã˜Â²','Ã™Â¾Ã™â€žÃ˜Â§ÃšÂ© Ã˜Â«Ã˜Â¨Ã˜ÂªÃ›Å’'],
 'construction':['Ã˜Â³Ã˜Â§Ã˜Â®Ã˜ÂªÃ™â€¦Ã˜Â§Ã™â€ ','Ã˜Â³Ã˜Â§Ã˜Â²Ã™â€¡','Ã˜Â¨Ã˜ÂªÃ™â€ ','Ã™ÂÃ™Ë†Ã™â€žÃ˜Â§Ã˜Â¯','Ã™â€¦Ã˜Â¹Ã™â€¦Ã˜Â§Ã˜Â±Ã›Å’','Ã˜ÂªÃ˜Â§Ã˜Â³Ã›Å’Ã˜Â³Ã˜Â§Ã˜Âª','Ã™â€¦Ã˜Â¨Ã˜Â­Ã˜Â«'],
 'fire':['Ã˜Â¢Ã˜ÂªÃ˜Â´ Ã™â€ Ã˜Â´Ã˜Â§Ã™â€ Ã›Å’','Ã˜Â­Ã˜Â±Ã›Å’Ã™â€š','Ã™â€¦Ã™â€šÃ˜Â§Ã™Ë†Ã™â€¦Ã˜Âª Ã˜Â¯Ã˜Â± Ã˜Â¨Ã˜Â±Ã˜Â§Ã˜Â¨Ã˜Â± Ã˜Â¢Ã˜ÂªÃ˜Â´'],
 'safety':['Ã˜Â§Ã›Å’Ã™â€¦Ã™â€ Ã›Å’','Ã˜Â®Ã˜Â·Ã˜Â±','Ã˜Â­Ã˜Â§Ã˜Â¯Ã˜Â«Ã™â€¡'],
 'property':['Ã™â€¦Ã˜Â§Ã™â€žÃšÂ©','Ã™â€¦Ã˜Â§Ã™â€žÃšÂ©Ã›Å’Ã˜Âª','Ã™â€¦Ã™â€žÃšÂ©','Ã˜Â¹Ã˜Â±Ã˜ÂµÃ™â€¡','Ã˜Â§Ã˜Â¹Ã›Å’Ã˜Â§Ã™â€ '],
 'legal':['Ã™â€šÃ˜Â§Ã™â€ Ã™Ë†Ã™â€ ','Ã™â€¦Ã˜Â§Ã˜Â¯Ã™â€¡','Ã˜ÂªÃ˜Â¨Ã˜ÂµÃ˜Â±Ã™â€¡','Ã˜Â¢Ã›Å’Ã›Å’Ã™â€  Ã™â€ Ã˜Â§Ã™â€¦Ã™â€¡','Ã™â€¦Ã˜ÂµÃ™Ë†Ã˜Â¨Ã™â€¡']}
KTYPES={
 'prohibition':['Ã™â€¦Ã™â€¦Ã™â€ Ã™Ë†Ã˜Â¹ Ã˜Â§Ã˜Â³Ã˜Âª','Ã™â€¦Ã˜Â¬Ã˜Â§Ã˜Â² Ã™â€ Ã›Å’Ã˜Â³Ã˜Âª','Ã™â€ Ã˜Â¨Ã˜Â§Ã›Å’Ã˜Â¯'], 'permission':['Ã™â€¦Ã˜Â¬Ã˜Â§Ã˜Â² Ã˜Â§Ã˜Â³Ã˜Âª','Ã™â€¦Ã›Å’ Ã˜ÂªÃ™Ë†Ã˜Â§Ã™â€ Ã˜Â¯','Ã˜Â§Ã˜Â¬Ã˜Â§Ã˜Â²Ã™â€¡'],
 'requirement':['Ã˜Â§Ã™â€žÃ˜Â²Ã˜Â§Ã™â€¦Ã›Å’ Ã˜Â§Ã˜Â³Ã˜Âª','Ã˜Â¨Ã˜Â§Ã›Å’Ã˜Â¯','Ã™â€¦Ã™Ë†Ã˜Â¸Ã™Â Ã˜Â§Ã˜Â³Ã˜Âª','Ã™â€¦ÃšÂ©Ã™â€žÃ™Â Ã˜Â§Ã˜Â³Ã˜Âª'], 'exception':['Ã˜Â§Ã˜Â³Ã˜ÂªÃ˜Â«Ã™â€ Ã˜Â§','Ã˜Â¨Ã™â€¡ Ã˜Â§Ã˜Â³Ã˜ÂªÃ˜Â«Ã™â€ Ã˜Â§Ã›Å’','Ã™â€¦ÃšÂ¯Ã˜Â±'],
 'condition':['Ã˜Â¯Ã˜Â± Ã˜ÂµÃ™Ë†Ã˜Â±Ã˜ÂªÃ›Å’ ÃšÂ©Ã™â€¡','Ã™â€¦Ã˜Â´Ã˜Â±Ã™Ë†Ã˜Â· Ã˜Â¨Ã™â€¡','Ãšâ€ Ã™â€ Ã˜Â§Ã™â€ Ãšâ€ Ã™â€¡'], 'definition':['Ã˜Â¹Ã˜Â¨Ã˜Â§Ã˜Â±Ã˜Âª Ã˜Â§Ã˜Â³Ã˜Âª Ã˜Â§Ã˜Â²','Ã˜ÂªÃ˜Â¹Ã˜Â±Ã›Å’Ã™Â Ã™â€¦Ã›Å’ Ã˜Â´Ã™Ë†Ã˜Â¯','Ã™â€¦Ã™â€ Ã˜Â¸Ã™Ë†Ã˜Â± Ã˜Â§Ã˜Â²'],
 'threshold':['Ã˜Â­Ã˜Â¯Ã˜Â§ÃšÂ©Ã˜Â«Ã˜Â±','Ã˜Â­Ã˜Â¯Ã˜Â§Ã™â€šÃ™â€ž','Ã˜Â¨Ã›Å’Ã˜Â´ Ã˜Â§Ã˜Â²','ÃšÂ©Ã™â€¦Ã˜ÂªÃ˜Â± Ã˜Â§Ã˜Â²'], 'penalty':['Ã˜Â¬Ã˜Â±Ã›Å’Ã™â€¦Ã™â€¡','Ã™â€¦Ã˜Â¬Ã˜Â§Ã˜Â²Ã˜Â§Ã˜Âª'],
 'procedure':['Ã™â€¦Ã˜Â±Ã˜Â§Ã˜Â­Ã™â€ž','Ã™ÂÃ˜Â±Ã˜Â¢Ã›Å’Ã™â€ Ã˜Â¯','Ã˜Â¯Ã˜Â±Ã˜Â®Ã™Ë†Ã˜Â§Ã˜Â³Ã˜Âª','Ã˜Â§Ã˜Â±Ã˜Â§Ã˜Â¦Ã™â€¡ Ã™â€¦Ã˜Â¯Ã˜Â§Ã˜Â±ÃšÂ©'], 'reference':['Ã™â€¦Ã˜Â·Ã˜Â§Ã˜Â¨Ã™â€š Ã™â€¦Ã˜Â§Ã˜Â¯Ã™â€¡','Ã˜Â·Ã˜Â¨Ã™â€š Ã™â€¦Ã˜Â§Ã˜Â¯Ã™â€¡','Ã™â€¦Ã™Ë†Ã˜Â¶Ã™Ë†Ã˜Â¹ Ã™â€¦Ã˜Â§Ã˜Â¯Ã™â€¡']}

def sha256b(b): return hashlib.sha256(b).hexdigest()
def sha256s(s): return sha256b(s.encode('utf-8'))
def norm(s):
    if not s: return ''
    s=s.replace('\u064a','Ã›Å’').replace('\u0649','Ã›Å’').replace('\u0643','ÃšÂ©').replace('\u200f','').replace('\ufeff','')
    s=re.sub(r'[ \t\xa0]+',' ',s); s=re.sub(r'\n{3,}','\n\n',s)
    return s.strip()
def simhash64(text):
    toks=re.findall(r'[\w\u0600-\u06ff]+', text.lower())
    if not toks: return '0'*16
    v=[0]*64
    for t in toks:
        h=int(hashlib.blake2b(t.encode(),digest_size=8).hexdigest(),16)
        for i in range(64): v[i]+=1 if h&(1<<i) else -1
    x=sum((1<<i) for i,n in enumerate(v) if n>=0)
    return f'{x:016x}'
def ham(a,b): return (int(a,16)^int(b,16)).bit_count()
def classify(text, mapping):
    low=text.lower(); scores=[]
    for label,keys in mapping.items():
        hits=sum(low.count(k) for k in keys)
        if hits: scores.append((label,min(.99,.55+.08*hits)))
    return sorted(scores,key=lambda x:x[1],reverse=True)
def refs(text):
    return list(dict.fromkeys(m.group(0) for m in re.finditer(r'(?:Ã™â€¦Ã˜Â§Ã˜Â¯Ã™â€¡|Ã˜ÂªÃ˜Â¨Ã˜ÂµÃ˜Â±Ã™â€¡|Ã˜Â¨Ã™â€ Ã˜Â¯)\s*[Ã›Â°-Ã›Â¹0-9]+(?:\s*[-Ã¢â‚¬â€œ]\s*[Ã›Â°-Ã›Â¹0-9]+)?',text)))[:30]
def entities(text):
    out=[]
    pat=r'([Ã›Â°-Ã›Â¹0-9]+(?:[\.,Ã™Â«][Ã›Â°-Ã›Â¹0-9]+)?)\s*(Ã™â€¦Ã˜ÂªÃ˜Â±Ã™â€¦Ã˜Â±Ã˜Â¨Ã˜Â¹|Ã™â€¦Ã˜ÂªÃ˜Â± Ã™â€¦Ã˜Â±Ã˜Â¨Ã˜Â¹|Ã™â€¦Ã˜ÂªÃ˜Â±|Ã˜Â³Ã˜Â§Ã™â€ Ã˜ÂªÃ›Å’ ?Ã™â€¦Ã˜ÂªÃ˜Â±|Ã˜Â¯Ã˜Â±Ã˜ÂµÃ˜Â¯|Ã˜Â·Ã˜Â¨Ã™â€šÃ™â€¡|Ã˜Â±Ã›Å’Ã˜Â§Ã™â€ž|Ã˜ÂªÃ™Ë†Ã™â€¦Ã˜Â§Ã™â€ )'
    for m in re.finditer(pat,text): out.append(('VALUE',m.group(1),m.group(2),m.start(),m.end()))
    return out[:50]
def heading(s):
    t=s.strip()
    return len(t)<180 and bool(re.match(r'^(Ã™ÂÃ˜ÂµÃ™â€ž|Ã˜Â¨Ã˜Â®Ã˜Â´|ÃšÂ¯Ã™ÂÃ˜ÂªÃ˜Â§Ã˜Â±|Ã™â€¦Ã˜Â¨Ã˜Â­Ã˜Â«|Ã™â€¦Ã˜Â§Ã˜Â¯Ã™â€¡|Ã˜ÂªÃ˜Â¨Ã˜ÂµÃ˜Â±Ã™â€¡|Ã™Â¾Ã›Å’Ã™Ë†Ã˜Â³Ã˜Âª|Ã˜Â¶Ã™â€¦Ã›Å’Ã™â€¦Ã™â€¡)\b',t))
def split_nodes(pages):
    nodes=[]
    for pn,txt in pages:
        blocks=[norm(x) for x in re.split(r'\n\s*\n',txt) if norm(x)]
        for b in blocks:
            if len(b)<20: continue
            # avoid giant blocks while keeping paragraph boundaries primary
            parts=[b] if len(b)<=7000 else [b[i:i+6000] for i in range(0,len(b),6000)]
            for x in parts: nodes.append((pn,'HEADING' if heading(x) else 'PARAGRAPH',x))
    return nodes
def read_file(path):
    ext=path.suffix.lower()
    if ext=='.pdf':
        if pymupdf is None: raise RuntimeError('pymupdf is required for PDF fallback extraction')
        doc=pymupdf.open(path); return [(i+1,norm(p.get_text('text'))) for i,p in enumerate(doc)]
    raw=path.read_text(encoding='utf-8',errors='ignore')
    if ext in {'.html','.htm'}: raw=re.sub(r'<[^>]+>',' ',raw)
    return [(1,norm(raw))]
def connect():
    url=os.getenv('DB_URL','jdbc:postgresql://localhost:5432/sakhtyar').replace('jdbc:','')
    return psycopg.connect(url, user=os.getenv('DB_USERNAME','sakhtyar'), password=(os.getenv('DB_PASSWORD') or (_ for _ in ()).throw(RuntimeError('DB_PASSWORD is required'))))
def ensure_run(c, code=None):
    rid=uuid.uuid4(); wid=uuid.uuid4(); corr=uuid.uuid4(); code=code or 'KP-'+datetime.now().strftime('%Y%m%d-%H%M%S')+'-'+str(rid)[:8]
    c.execute("""insert into knowledge_pipeline_run(id,run_code,mode,status,stage,command_text,runbook_version,started_at,workflow_id,correlation_id,pipeline_version)
      values(%s,%s,'INCREMENTAL','RUNNING','PREPARATION',%s,'knowledge-preparation-v1',now(),%s,%s,%s)""",
      (rid,code,'Local DB-backed Knowledge Preparation',wid,corr,PIPELINE_VERSION))
    return rid,wid

def source_version(c,path,digest,size):
    canonical=str(path.resolve())
    c.execute("select id,correlation_id,document_version,sha256,status from knowledge_source_document where canonical_path=%s order by document_version desc limit 1",(canonical,))
    old=c.fetchone()
    if old and old[3]==digest: return old[0],old[1],old[2],True
    ver=(old[2]+1) if old else 1; corr=old[1] if old else uuid.uuid4(); sid=uuid.uuid4()
    c.execute("""insert into knowledge_source_document(id,correlation_id,canonical_path,filename,extension,sha256,size_bytes,document_version,previous_document_id,status,extraction_version)
      values(%s,%s,%s,%s,%s,%s,%s,%s,%s,'DISCOVERED',%s)""",
      (sid,corr,canonical,path.name,path.suffix.lower(),digest,size,ver,old[0] if old else None,PIPELINE_VERSION))
    return sid,corr,ver,False

def prepare_one(c,run_id,wid,path):
    data=path.read_bytes(); digest=sha256b(data)
    sid,corr,ver,reused=source_version(c,path,digest,len(data))
    c.execute("insert into knowledge_pipeline_run_document(run_id,source_document_id,correlation_id,status,reused) values(%s,%s,%s,%s,%s) on conflict do nothing",(run_id,sid,corr,'REUSED' if reused else 'DISCOVERED',reused))
    c.execute("select 1 from knowledge_document_node where run_id=%s and source_document_id=%s limit 1",(run_id,sid))
    if c.fetchone(): return 'already-in-run'
    pages=read_file(path)
    for pn,txt in pages:
        c.execute("insert into knowledge_source_page(run_id,source_document_id,page_number,raw_text,normalized_text,text_hash,char_count) values(%s,%s,%s,%s,%s,%s,%s) on conflict do nothing",(run_id,sid,pn,txt,txt,sha256s(txt),len(txt)))
    exact={}; near_buckets={}; count=0
    for idx,(pn,typ,text) in enumerate(split_nodes(pages)):
        th=sha256s(text); sh=simhash64(text); nid=uuid.uuid4()
        density=min(1.0,len(set(re.findall(r'[\w\u0600-\u06ff]+',text)))/max(1,len(text.split())))
        importance=min(1.0,.25+.25*bool(refs(text))+.2*bool(entities(text))+.2*min(1,len(text)/1000)+.1*density)
        c.execute("""insert into knowledge_document_node(id,run_id,source_document_id,correlation_id,node_index,node_type,normalized_text,text_hash,simhash64,page_from,page_to,information_density,importance_score)
          values(%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)""",(nid,run_id,sid,corr,idx,typ,text,th,sh,pn,pn,density,importance))
        c.execute("insert into knowledge_lineage_edge(workflow_id,run_id,correlation_id,from_type,from_id,to_type,to_id,relation_type) values(%s,%s,%s,'SOURCE_DOCUMENT',%s,'NODE',%s,'CONTAINS') on conflict do nothing",(wid,run_id,corr,sid,nid))
        for ct,mapping in [('DOMAIN',DOMAINS),('KNOWLEDGE_TYPE',KTYPES)]:
            for label,conf in classify(text,mapping)[:4]:
                c.execute("insert into knowledge_node_classification(run_id,node_id,classification_type,label,confidence,classifier_version) values(%s,%s,%s,%s,%s,%s) on conflict do nothing",(run_id,nid,ct,label,conf,PIPELINE_VERSION))
        for et,val,unit,a,b in entities(text): c.execute("insert into knowledge_node_entity(run_id,node_id,entity_type,value,normalized_value,unit,confidence,start_offset,end_offset) values(%s,%s,%s,%s,%s,%s,.9,%s,%s)",(run_id,nid,et,val,val,unit,a,b))
        lr=refs(text)
        for r in lr: c.execute("insert into knowledge_node_reference(run_id,node_id,reference_type,reference_text,normalized_reference,confidence) values(%s,%s,'LEGAL',%s,%s,.9)",(run_id,nid,r,r))
        domains=classify(text,DOMAINS); kinds=classify(text,KTYPES)
        if importance>=.42 and (domains or kinds or lr):
            ctype=kinds[0][0] if kinds else 'reference'; domain=domains[0][0] if domains else 'other'; cid=uuid.uuid4()
            prov={'source_document_id':str(sid),'source_node_id':str(nid),'page':pn,'correlation_id':str(corr),'source_path':str(path)}
            c.execute("""insert into knowledge_preparation_candidate(id,run_id,source_document_id,node_id,correlation_id,candidate_type,statement,statement_hash,domain,legal_refs,confidence,importance_score,provenance)
              values(%s,%s,%s,%s,%s,%s,%s,%s,%s,%s::jsonb,%s,%s,%s::jsonb)""",(cid,run_id,sid,nid,corr,ctype,text,th,domain,json.dumps(lr,ensure_ascii=False),kinds[0][1] if kinds else .6,importance,json.dumps(prov,ensure_ascii=False)))
            c.execute("insert into knowledge_lineage_edge(workflow_id,run_id,correlation_id,from_type,from_id,to_type,to_id,relation_type) values(%s,%s,%s,'NODE',%s,'PREPARATION_CANDIDATE',%s,'DERIVED_AS') on conflict do nothing",(wid,run_id,corr,nid,cid))
        # exact/near dedup within current run, bucketed by first 4 simhash hex chars
        match=None; sim=0
        if th in exact: match=exact[th]; sim=1.0
        else:
            bucket=sh[:4]
            for osh,onid in near_buckets.get(bucket,[]):
                d=ham(sh,osh)
                if d<=3: match=onid; sim=1-d/64; break
            exact[th]=nid; near_buckets.setdefault(bucket,[]).append((sh,nid))
        if match:
            cluster=uuid.uuid4(); kind='EXACT' if sim==1 else 'NEAR'
            c.execute("insert into knowledge_dedup_cluster(id,run_id,cluster_type,representative_node_id,fingerprint,similarity_threshold) values(%s,%s,%s,%s,%s,%s)",(cluster,run_id,kind,match,th if kind=='EXACT' else sh,.95))
            c.execute("insert into knowledge_dedup_member(cluster_id,node_id,similarity,is_representative) values(%s,%s,1,true),(%s,%s,%s,false)",(cluster,match,cluster,nid,sim))
        count+=1
    c.execute("update knowledge_source_document set page_count=%s,text_chars=%s,status='PREPARED',prepared_at=now() where id=%s",(len(pages),sum(len(x[1]) for x in pages),sid))
    c.execute("update knowledge_pipeline_run_document set status='PREPARED',finished_at=now(),statistics=jsonb_build_object('nodes',%s,'version',%s) where run_id=%s and source_document_id=%s",(count,ver,run_id,sid))
    return f'prepared nodes={count}'

def export_package(c,run_id,wid,outdir):
    outdir.mkdir(parents=True,exist_ok=True); tmp=outdir/f'llm-input-{run_id}'; tmp.mkdir(exist_ok=True)
    files=[]
    def dump(name,sql):
        fp=tmp/name; n=0
        with fp.open('w',encoding='utf-8') as f:
            c.execute(sql,(run_id,))
            cols=[d.name for d in c.description]
            for row in c.fetchall():
                obj={k:(str(v) if isinstance(v,uuid.UUID) else v) for k,v in zip(cols,row)}
                f.write(json.dumps(obj,ensure_ascii=False,default=str)+'\n'); n+=1
        files.append({'path':name,'sha256':sha256b(fp.read_bytes()),'records':n,'size_bytes':fp.stat().st_size})
    dump('source-documents.jsonl',"select d.id,d.correlation_id,d.canonical_path,d.filename,d.sha256,d.document_version,d.page_count,d.text_chars from knowledge_source_document d join knowledge_pipeline_run_document rd on rd.source_document_id=d.id where rd.run_id=%s")
    dump('nodes.jsonl',"select id,source_document_id,correlation_id,node_index,node_type,normalized_text,text_hash,page_from,page_to,information_density,importance_score from knowledge_document_node where run_id=%s order by source_document_id,node_index")
    dump('knowledge-candidates.jsonl',"select id,source_document_id,node_id,correlation_id,candidate_type,statement,statement_hash,domain,topics,applicability,authority,legal_refs,confidence,importance_score,provenance from knowledge_preparation_candidate where run_id=%s order by source_document_id,importance_score desc")
    manifest={'package_type':'SAKHTYAR_LLM_INPUT','schema_version':'1','pipeline_version':PIPELINE_VERSION,'workflow_id':str(wid),'run_id':str(run_id),'generated_at':datetime.now(timezone.utc).isoformat(),'files':files,'result_contract':'Return a SakhtYar V32 dataset package; preserve correlation_id/source_document_id/source_node_id in provenance.'}
    (tmp/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
    cmd='''# SakhtYar Knowledge Extraction Command\nProcess this prepared corpus as evidence-grounded construction/legal knowledge. Never invent rules. Preserve every correlation_id, source_document_id, source node id and page in provenance. Detect contradictions and duplicates. Return a ZIP compatible with SakhtYar V32 Knowledge Dataset Package Import containing manifest.json and the required documents/chunks/evidence/knowledge-candidates/rule-candidates/relations JSONL artifacts. Every assertion/rule must point to evidence. Keep uncertain items PENDING_REVIEW.\n'''
    (tmp/'command.txt').write_text(cmd,encoding='utf-8')
    z=outdir/f'SakhtYar-LLM-Input-{run_id}.zip'
    with zipfile.ZipFile(z,'w',zipfile.ZIP_DEFLATED) as zz:
        for fp in tmp.iterdir(): zz.write(fp,fp.name)
    digest=sha256b(z.read_bytes())
    c.execute("insert into knowledge_llm_export(workflow_id,run_id,export_version,output_path,sha256,size_bytes,manifest,statistics) values(%s,%s,%s,%s,%s,%s,%s::jsonb,%s::jsonb)",(wid,run_id,datetime.now().strftime('%Y%m%d%H%M%S'),str(z),digest,z.stat().st_size,json.dumps(manifest,ensure_ascii=False),json.dumps({'files':len(files)},ensure_ascii=False)))
    return z

def main():
    ap=argparse.ArgumentParser(); ap.add_argument('action',choices=['prepare','export','all']); ap.add_argument('--input'); ap.add_argument('--output',required=True); ap.add_argument('--run-id')
    a=ap.parse_args(); conn=connect()
    with conn:
      with conn.cursor() as c:
        if a.action in ('prepare','all'):
            if not a.input: ap.error('--input required for prepare/all')
            run_id,wid=ensure_run(c); root=Path(a.input); paths=[p for p in root.rglob('*') if p.is_file() and p.suffix.lower() in SUPPORTED]
            print(f'Run={run_id} files={len(paths)}')
            ok=fail=0
            for i,p in enumerate(paths,1):
                try:
                    msg=prepare_one(c,run_id,wid,p); conn.commit(); ok+=1; print(f'[{i}/{len(paths)}] OK {p} {msg}')
                except Exception as e:
                    conn.rollback(); fail+=1; print(f'[{i}/{len(paths)}] FAIL {p}: {e}',file=sys.stderr)
                    with conn.cursor() as ec: ec.execute("insert into knowledge_pipeline_event(run_id,event_type,level,message,details) values(%s,'DOCUMENT_FAILED','ERROR',%s,%s::jsonb)",(run_id,str(e),json.dumps({'path':str(p)},ensure_ascii=False))); conn.commit()
            with conn.cursor() as c2: c2.execute("update knowledge_pipeline_run set status=%s,stage='PREPARED',finished_at=now(),statistics=jsonb_build_object('ok',%s,'failed',%s) where id=%s",('COMPLETED' if fail==0 else 'COMPLETED_WITH_ERRORS',ok,fail,run_id)); conn.commit()
        else:
            run_id=uuid.UUID(a.run_id); c.execute('select workflow_id from knowledge_pipeline_run where id=%s',(run_id,)); row=c.fetchone();
            if not row: raise RuntimeError('run-id not found')
            wid=row[0]
        if a.action in ('export','all'):
            z=export_package(c,run_id,wid,Path(a.output)); conn.commit(); print('EXPORT='+str(z))
if __name__=='__main__': main()
