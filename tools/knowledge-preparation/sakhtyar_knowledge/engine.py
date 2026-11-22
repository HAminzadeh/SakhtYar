from __future__ import annotations
import json
import re
import uuid
from pathlib import Path
from psycopg.types.json import Jsonb
from .config import PIPELINE_VERSION, SUPPORTED
from .extract import read_pages, split_nodes
from .rules import DOMAINS, KNOWLEDGE_TYPES, classify, legal_refs, measurements
from .text import sha256_bytes, sha256_text, simhash64, hamming, suspect_count, persian_ratio

class PreparationEngine:
    def __init__(self, db, cfg):
        self.db, self.cfg = db, cfg
        self.exact = {}
        self.near = {}
        self.stats = {"documents":0,"nodes":0,"classifications":0,"references":0,"entities":0,
                      "candidates":0,"repaired_pages":0,"suspect_chars_after":0}

    def run(self):
        root = self.cfg.input_root
        paths = [root] if root.is_file() else [p for p in root.rglob("*") if p.is_file() and p.suffix.lower() in SUPPORTED]
        if not paths:
            raise RuntimeError(f"No supported documents found under {root}")
        for index, path in enumerate(paths, 1):
            self._document(path, index, len(paths))
        self._quality_gate()
        return self.stats

    def _document(self, path: Path, index: int, total: int):
        data = path.read_bytes()
        sid, corr, version, reused = self.db.source_version(path, sha256_bytes(data), len(data))
        with self.db.conn.cursor() as c:
            c.execute("""insert into knowledge_pipeline_run_document(run_id,source_document_id,correlation_id,status,reused)
                         values(%s,%s,%s,'DISCOVERED',%s) on conflict(run_id,source_document_id) do nothing""",
                      (self.cfg.run_id,sid,corr,reused))
            c.execute("select count(*) from knowledge_document_node where run_id=%s and source_document_id=%s",
                      (self.cfg.run_id,sid))
            existing = c.fetchone()[0]
        self.db.conn.commit()
        if existing:
            self.db.event(self.cfg.run_id, "DOCUMENT_SKIPPED", f"{path}: already prepared in this run",
                          details={"nodes":existing}, source_document_id=sid)
            return

        step = self.db.step_start(self.cfg.run_id, "EXTRACT_ENRICH", sid)
        try:
            pages = read_pages(path)
            repaired_pages = sum(1 for x in pages if x[3])
            with self.db.conn.cursor() as c:
                for pn, raw, clean, repaired in pages:
                    c.execute("""insert into knowledge_source_page
                                 (run_id,source_document_id,page_number,raw_text,normalized_text,text_hash,char_count,metadata)
                                 values(%s,%s,%s,%s,%s,%s,%s,%s)
                                 on conflict(run_id,source_document_id,page_number) do update
                                 set raw_text=excluded.raw_text,normalized_text=excluded.normalized_text,
                                     text_hash=excluded.text_hash,char_count=excluded.char_count,metadata=excluded.metadata""",
                              (self.cfg.run_id,sid,pn,raw,clean,sha256_text(clean),len(clean),
                               Jsonb({"encoding_repaired":repaired,"persian_ratio":persian_ratio(clean),
                                      "suspect_chars":suspect_count(clean)})))
            self.db.conn.commit()

            node_count = 0
            for node_index, (pn, typ, text) in enumerate(split_nodes(pages)):
                self._node(sid, corr, node_index, pn, typ, text)
                node_count += 1

            with self.db.conn.cursor() as c:
                c.execute("""update knowledge_source_document
                             set page_count=%s,text_chars=%s,status='PREPARED',prepared_at=now(),extraction_version=%s
                             where id=%s""",
                          (len(pages),sum(len(p[2]) for p in pages),PIPELINE_VERSION,sid))
                c.execute("""update knowledge_pipeline_run_document
                             set status='PREPARED',finished_at=now(),statistics=%s
                             where run_id=%s and source_document_id=%s""",
                          (Jsonb({"nodes":node_count,"version":version,"repaired_pages":repaired_pages}),
                           self.cfg.run_id,sid))
            self.db.conn.commit()
            self.stats["documents"] += 1
            self.stats["nodes"] += node_count
            self.stats["repaired_pages"] += repaired_pages
            self.stats["suspect_chars_after"] += sum(suspect_count(p[2]) for p in pages)
            self.db.step_finish(step, stats={"pages":len(pages),"nodes":node_count,"repaired_pages":repaired_pages})
            self.db.event(self.cfg.run_id, "DOCUMENT_PREPARED", f"[{index}/{total}] {path.name}",
                          details={"nodes":node_count,"pages":len(pages)}, source_document_id=sid)
        except Exception as exc:
            self.db.conn.rollback()
            self.db.step_finish(step, "FAILED", error=str(exc))
            self.db.event(self.cfg.run_id, "DOCUMENT_FAILED", str(exc), "ERROR", {"path":str(path)}, sid)
            raise

    def _node(self, sid, corr, idx, pn, typ, text):
        th, sh, nid = sha256_text(text), simhash64(text), uuid.uuid4()
        words = re.findall(r"[\w\u0600-\u06ff]+", text)
        density = min(1.0, len(set(words))/max(1,len(words)))
        refs = legal_refs(text)
        ents = measurements(text)
        domains = classify(text, DOMAINS)
        kinds = classify(text, KNOWLEDGE_TYPES)
        importance = min(1.0,.25+.25*bool(refs)+.20*bool(ents)+.15*bool(domains or kinds)+.15*min(1,len(text)/1000))
        with self.db.conn.cursor() as c:
            c.execute("""insert into knowledge_document_node
                         (id,run_id,source_document_id,correlation_id,node_index,node_type,normalized_text,text_hash,
                          simhash64,page_from,page_to,information_density,importance_score,metadata)
                         values(%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)""",
                      (nid,self.cfg.run_id,sid,corr,idx,typ,text,th,sh,pn,pn,density,importance,
                       Jsonb({"persian_ratio":persian_ratio(text),"suspect_chars":suspect_count(text)})))
            c.execute("""insert into knowledge_lineage_edge
                         (workflow_id,run_id,correlation_id,from_type,from_id,to_type,to_id,relation_type)
                         values(%s,%s,%s,'SOURCE_DOCUMENT',%s,'NODE',%s,'CONTAINS') on conflict do nothing""",
                      (self.cfg.workflow_id,self.cfg.run_id,corr,sid,nid))
            for ct, values in (("DOMAIN",domains),("KNOWLEDGE_TYPE",kinds)):
                for label, confidence in values[:4]:
                    c.execute("""insert into knowledge_node_classification
                                 (run_id,node_id,classification_type,label,confidence,classifier_version)
                                 values(%s,%s,%s,%s,%s,%s) on conflict do nothing""",
                              (self.cfg.run_id,nid,ct,label,confidence,PIPELINE_VERSION))
                    self.stats["classifications"] += 1
            for ref in refs:
                c.execute("""insert into knowledge_node_reference
                             (run_id,node_id,reference_type,reference_text,normalized_reference,confidence)
                             values(%s,%s,'LEGAL',%s,%s,.9)""",(self.cfg.run_id,nid,ref,ref))
                self.stats["references"] += 1
            for value, unit, start, end in ents:
                c.execute("""insert into knowledge_node_entity
                             (run_id,node_id,entity_type,value,normalized_value,unit,confidence,start_offset,end_offset)
                             values(%s,%s,'MEASUREMENT',%s,%s,%s,.9,%s,%s)""",
                          (self.cfg.run_id,nid,value,value,unit,start,end))
                self.stats["entities"] += 1
            if importance >= .42 and (domains or kinds or refs or ents):
                ctype = kinds[0][0] if kinds else "reference"
                domain = domains[0][0] if domains else "other"
                cid = uuid.uuid4()
                provenance = {"source_document_id":str(sid),"source_node_id":str(nid),"page":pn,
                              "correlation_id":str(corr)}
                c.execute("""insert into knowledge_preparation_candidate
                             (id,run_id,source_document_id,node_id,correlation_id,candidate_type,statement,
                              statement_hash,domain,legal_refs,confidence,importance_score,provenance)
                             values(%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)""",
                          (cid,self.cfg.run_id,sid,nid,corr,ctype,text,th,domain,Jsonb(refs),
                           kinds[0][1] if kinds else .6,importance,Jsonb(provenance)))
                c.execute("""insert into knowledge_lineage_edge
                             (workflow_id,run_id,correlation_id,from_type,from_id,to_type,to_id,relation_type)
                             values(%s,%s,%s,'NODE',%s,'PREPARATION_CANDIDATE',%s,'DERIVED_AS') on conflict do nothing""",
                          (self.cfg.workflow_id,self.cfg.run_id,corr,nid,cid))
                self.stats["candidates"] += 1
            self._dedup(c, nid, th, sh)
        self.db.conn.commit()

    def _dedup(self, c, nid, th, sh):
        match, similarity = None, 0.0
        if th in self.exact:
            match, similarity = self.exact[th], 1.0
        else:
            for old_sh, old_id in self.near.get(sh[:4], []):
                distance = hamming(sh, old_sh)
                if distance <= 3:
                    match, similarity = old_id, 1 - distance/64
                    break
        self.exact.setdefault(th, nid)
        self.near.setdefault(sh[:4], []).append((sh,nid))
        if match:
            cluster = uuid.uuid4()
            kind = "EXACT" if similarity == 1 else "NEAR"
            c.execute("""insert into knowledge_dedup_cluster
                         (id,run_id,cluster_type,representative_node_id,fingerprint,similarity_threshold)
                         values(%s,%s,%s,%s,%s,.95)""",
                      (cluster,self.cfg.run_id,kind,match,th if kind=="EXACT" else sh))
            c.execute("""insert into knowledge_dedup_member(cluster_id,node_id,similarity,is_representative)
                         values(%s,%s,1,true),(%s,%s,%s,false)""",
                      (cluster,match,cluster,nid,similarity))

    def _quality_gate(self):
        if self.stats["nodes"] >= 20:
            if self.stats["classifications"] == 0:
                raise RuntimeError("Quality gate failed: zero classifications")
            if self.stats["candidates"] == 0:
                raise RuntimeError("Quality gate failed: zero candidates")
        if self.stats["suspect_chars_after"] > max(50, self.stats["nodes"] * 3):
            raise RuntimeError(f"Quality gate failed: excessive mojibake remains ({self.stats['suspect_chars_after']})")
