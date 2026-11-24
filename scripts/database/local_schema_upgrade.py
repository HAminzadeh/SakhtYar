#!/usr/bin/env python3
"""Explicit local schema-only promotion. Does not provision runtime roles or certify application security."""
from __future__ import annotations
import argparse, hashlib, json, os, sys, subprocess
from pathlib import Path
import migration_lifecycle as m

# Each future schema-only release requires separate code review and an exact SQL digest.
REVIEWED = {68: ('V68__builder_invitation_proposal_workflow.sql',
                 '4075d10927a094975cd5d25d1bc51374752c0bb87eaa0189e493ff80ecc809ef')}
NEW_TABLES = {'builder_account_binding','builder_verification_event','builder_invitation',
              'builder_proposal','builder_proposal_revision'}
ROUTINES = ('sakhtyar_builder_workflow_guard','sakhtyar_builder_review_immutable')
DEPENDENCY_BLOB = '69d09cbb7d340f0367140cf5a368e015ec235201'


def approved_inventory(entries, pending, verify=False):
    m.require(len(entries)==68 and entries[-1]['version']==68,
              'Schema-only promotion is reviewed only for V68; use normal lifecycle for future releases')
    e=entries[-1]
    m.require((e['name'],e['sha256'])==REVIEWED[68], 'V68 SQL bytes differ from reviewed migration')
    m.require([x['version'] for x in pending]==([] if verify else [68]),
              'Expected exactly pending V68 (or current V68 in Verify); no baseline/repair allowed')


def approval(head, container, history, schema, roles, entries):
    payload=json.dumps([head,container,history,schema,roles,entries],sort_keys=True,separators=(',',':')).encode()
    return 'local-schema-only:V68:'+hashlib.sha256(payload).hexdigest()[:24]


def assert_structure(c,u,req):
    m.require(NEW_TABLES<=set(req),'Missing V68 structural policy')
    for table,rule in req.items():
        reg=m.lit('public.'+table)
        tests=[f'to_regclass({reg}) IS NOT NULL']
        if rule.get('rls'):
            tests.append(f'(SELECT relrowsecurity AND relforcerowsecurity FROM pg_class WHERE oid=to_regclass({reg}))')
        for trig in rule.get('triggers',[]):
            tests.append(f"EXISTS(SELECT 1 FROM pg_trigger WHERE tgrelid=to_regclass({reg}) AND tgname={m.lit(trig)} AND tgenabled IN ('O','A') AND NOT tgisinternal)")
        for policy in rule.get('policies',[]):
            tests.append(f'EXISTS(SELECT 1 FROM pg_policy WHERE polrelid=to_regclass({reg}) AND polname={m.lit(policy)})')
        if table in NEW_TABLES:
            tests.append(f"NOT EXISTS(SELECT 1 FROM pg_class x CROSS JOIN LATERAL aclexplode(coalesce(x.relacl,acldefault('r',x.relowner))) a WHERE x.oid=to_regclass({reg}) AND a.grantee=0)")
        m.require(m.query(c,u,'SELECT to_jsonb(coalesce('+' AND '.join(tests)+',false));') is True,
                  'Schema structural/ACL check failed: '+table)
    for routine in ROUTINES:
        reg=m.lit('public.'+routine+'()')
        sql=f"""SELECT to_jsonb(to_regprocedure({reg}) IS NOT NULL AND NOT EXISTS(
        SELECT 1 FROM pg_proc p CROSS JOIN LATERAL aclexplode(coalesce(p.proacl,acldefault('f',p.proowner))) a
        WHERE p.oid=to_regprocedure({reg}) AND a.grantee=0));"""
        m.require(m.query(c,u,sql) is True,'Missing routine or PUBLIC grant: '+routine)


class LocalSchemaOperator(m.Operator):
    def check_source_dependency(self):
        p='scripts/database/migration_lifecycle.py'
        raw=(self.root/p).read_bytes().replace(b'\r\n',b'\n')
        blob=hashlib.sha1(b'blob '+str(len(raw)).encode()+b'\0'+raw).hexdigest()
        m.require(blob==DEPENDENCY_BLOB and m.git(self.root,'rev-parse','HEAD:'+p)==DEPENDENCY_BLOB,
                  'Migration lifecycle dependency differs from reviewed source')
        m.require(not m.git(self.root,'status','--porcelain','--',str(m.POLICY)), 'Migration runtime policy has local changes')

    def snapshot(self):
        return m.history(self.c,self.owner),m.schema_hash(self.c,self.owner),m.roles_hash(self.c,self.owner)

    def unchanged(self, snapshot):
        m.require(m.git(self.root,'rev-parse','HEAD')==self.head and m.inventory(self.root/m.MIG)==self.entries and
                  not m.git(self.root,'status','--porcelain','--untracked-files=all','--',str(m.MIG)) and
                  m.sha(self.root/m.POLICY)==self.policy_sha and m.inspect(m.SOURCE)['Id']==self.c,
                  'Source or target identity changed; stop and replan')
        self.check_source_dependency()
        m.require(self.snapshot()==snapshot,'Live history/schema/roles changed; stop and replan')

    def execute(self):
        self.check_source_dependency(); self.source(); self.connect(); self.audit()
        mode=self.a.mode
        approved_inventory(self.entries,self.pending,mode=='Verify')
        self.report.update(scope='local schema only; NOT runtime authorization or production release',runtime_approved=False)
        m.require(self.owner==self.user,'Schema-only path requires existing migration owner, not runtime credentials')
        self.image(); self.fly(self.c,self.password,'validate'); self.gate('live_flyway_checksum','PASS')
        req=self.requirements([68])
        if mode=='Verify':
            assert_structure(self.c,self.owner,req)
            self.gate('schema_history','CURRENT V68; runtime approval remains separate')
            return
        # This path is specifically for the current local installation before runtime cutover.
        # Never silently strand an already provisioned application login without new grants.
        m.require(m.query(self.c,self.owner,"SELECT to_jsonb(NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='sakhtyar_runtime'));" ) is True,
                  'Runtime already exists; use standard reviewed Role/Migration lifecycle')
        m.require(not self.role or self.role==self.owner,
                  'Restricted/alternate runtime configured; use standard reviewed lifecycle')
        m.require(not (NEW_TABLES & set(self.before_tables)),'Unexpected V68 tables already present')
        m.quiet(self.c,self.owner)
        snap=self.snapshot(); m.require(snap[0]==self.before,'History changed during plan')
        token=approval(self.head,self.c,*snap,self.entries)
        self.report['approval_token']=token
        self.gate('approval_token',token)
        if mode=='Plan': return
        m.require(mode=='Apply' and self.a.approve==token,'Explicit current schema-only approval token required')
        self.backup()
        m.require((self.before,self.schema_before,self.roles_before)==snap,'Database changed before backup')
        with self.clone() as (c,u,pw):
            self.fly(c,pw,'validate'); self.fly(c,pw,'migrate',68); self.fly(c,pw,'validate')
            m.require(not m.history_gate(m.history(c,u),self.entries),'Clone history is not V68')
            m.require(set(m.tables(c,u))-set(self.before_tables)==NEW_TABLES,'Unexpected new table inventory on clone')
            assert_structure(c,u,req)
            migrated=m.schema_bytes(c,self.owner)
            (self.work/'clone-migrated-schema.private.sql').write_bytes(migrated)
            self.gate('clone_schema_only','PASS; no runtime grant applied')
        self.unchanged(snap); m.quiet(self.c,self.owner)
        self.gate('live_apply','STARTED; if interrupted inspect state; never auto-restore')
        self.fly(self.c,self.password,'migrate',68); self.fly(self.c,self.password,'validate')
        m.require(not m.history_gate(m.history(self.c,self.owner),self.entries),'Live history not V68')
        m.require(set(m.tables(self.c,self.owner))-set(self.before_tables)==NEW_TABLES,'Unexpected live new tables')
        assert_structure(self.c,self.owner,req)
        post=self.work/'post-migration-schema'; post.mkdir()
        result=m.compare_schema(post,migrated,m.schema_bytes(self.c,self.owner))
        self.gate('post_migration_schema_comparison',result)
        m.require(result['matches'],'Live schema differs from migrated clone; stop for DBA review')
        m.require(m.roles_hash(self.c,self.owner)==snap[2],'Role metadata changed; investigate')
        self.gate('live_roles_unchanged',True)
        self.gate('live_apply','PASS; schema V68 only; runtime/production approval still pending')


def main():
    p=argparse.ArgumentParser(); p.add_argument('--mode',choices=['Plan','Apply','Verify'],required=True)
    p.add_argument('--root',default=str(m.ROOT)); p.add_argument('--approve'); a=p.parse_args()
    a.backup_root=None; a.runtime_role=None
    op=None; acquired=False
    try:
        op=LocalSchemaOperator(a); lock=op.home/'.lifecycle.lock'
        fd=os.open(lock,os.O_CREAT|os.O_EXCL|os.O_WRONLY); os.close(fd); acquired=True
        op.execute(); op.report['status']='PASS'; return 0
    except KeyboardInterrupt:
        if op: op.report.update(status='ABORTED',error='Interrupted; inspect state before retry')
        return 130
    except (m.GateError,OSError,ValueError,KeyError,subprocess.SubprocessError) as e:
        msg=str(e) if isinstance(e,m.GateError) else type(e).__name__+'; inspect local configuration privately'
        if op: op.report.update(status='BLOCKED',error=msg)
        print('[LocalSchema] BLOCKED: '+msg,file=sys.stderr); return 1
    finally:
        if acquired: lock.unlink(missing_ok=True)
        if op: op.save(); print('[LocalSchema] Report: '+str(op.file),flush=True)

if __name__=='__main__': sys.exit(main())
