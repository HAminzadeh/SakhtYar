#!/usr/bin/env python3
"""Explicit, per-object PostgreSQL runtime privilege governance; local Docker only."""
import argparse, base64, getpass, hashlib, hmac, json, os, secrets, sys
from pathlib import Path
import migration_lifecycle as m

ROLE='sakhtyar_runtime'
POLICY=Path('scripts/database/runtime_privileges.reviewed.json')
DML=('SELECT','INSERT','UPDATE','DELETE')
TABLE_PRIV=DML+('TRUNCATE','REFERENCES','TRIGGER','MAINTAIN')
SEQ_PRIV=('USAGE','SELECT','UPDATE')
# OID-independent keys. Extension objects are inventoried separately, not granted here.
CATALOG_SQL="""
WITH objs AS (
 SELECT CASE WHEN c.relkind='S' THEN 'sequence' ELSE 'table' END AS kind,
   c.relname AS name, NULL::text AS args, c.oid, 'pg_class'::regclass AS classid,
   pg_get_userbyid(c.relowner) AS owner, c.relrowsecurity AS rls,
   c.relforcerowsecurity AS force_rls, c.relkind::text AS subtype,
   false AS security_definer
 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
 WHERE n.nspname='public' AND c.relkind IN ('r','p','v','m','f','S')
 UNION ALL
 SELECT 'routine',p.proname,pg_get_function_identity_arguments(p.oid),p.oid,'pg_proc'::regclass,
   pg_get_userbyid(p.proowner),false,false,p.prokind::text,p.prosecdef
 FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
 WHERE n.nspname='public' AND p.prokind IN ('f','p','w','a')
), rows AS (
 SELECT kind,name,args,owner,rls,force_rls,subtype,security_definer,
 EXISTS(SELECT 1 FROM pg_depend d WHERE d.classid=objs.classid AND d.objid=objs.oid AND d.deptype='e') AS extension,
 CASE WHEN kind='routine' THEN has_function_privilege('public',oid,'EXECUTE') ELSE false END AS public_execute,
 CASE WHEN kind='routine' THEN false ELSE EXISTS(SELECT 1 FROM pg_trigger t WHERE t.tgrelid=objs.oid AND NOT t.tgisinternal AND t.tgenabled IN ('O','A') AND (t.tgname ~* '(immutable|append.only|revision_guard)')) END AS immutable_hint
 FROM objs
)
SELECT coalesce(jsonb_agg(to_jsonb(rows) ORDER BY kind,name,args),'[]'::jsonb) FROM rows;
"""
# PUBLIC is a pseudo-role, not a role name accepted by has_function_privilege; inspect ACL instead.
CATALOG_SQL=CATALOG_SQL.replace("has_function_privilege('public',oid,'EXECUTE')", "EXISTS(SELECT 1 FROM pg_proc p CROSS JOIN LATERAL aclexplode(coalesce(p.proacl,acldefault('f',p.proowner))) a WHERE p.oid=objs.oid AND a.grantee=0 AND a.privilege_type='EXECUTE')")

def key(o): return o['kind']+':'+o['name']+('('+o['args']+')' if o['kind']=='routine' else '')
def catalog(c,u): return m.query(c,u,CATALOG_SQL)
def digest(data): return hashlib.sha256(json.dumps(data,sort_keys=True,ensure_ascii=False,separators=(',',':')).encode()).hexdigest()
def sqlid(x): return '"'+x.replace('"','""')+'"'
def object_sql(o):
    name='public.'+sqlid(o['name'])
    if o['kind']=='routine':
        # Arguments come exclusively from pg_get_function_identity_arguments, not from policy input.
        return ('PROCEDURE ' if o['subtype']=='p' else 'FUNCTION ')+name+'('+o['args']+')'
    return ('SEQUENCE ' if o['kind']=='sequence' else 'TABLE ')+name

def draft(objects, pending):
    out={}
    for o in objects:
        if o['extension']: continue
        k=key(o); out[k]={'decision':'REVIEW','privileges':[],'reason':''}
        if o['kind']=='routine' and o['public_execute'] and not o['security_definer']:
            out[k]={'decision':'REVIEW','privileges':['EXECUTE'],'reason':'Existing PUBLIC EXECUTE; review actual usage and exposure, not an approved grant'}
        if k=='table:flyway_schema_history': out[k]={'decision':'ALLOW','privileges':['SELECT'],'reason':'Startup history preflight; read only'}
    return {'format':1,'role':ROLE,'reviewed':False,'objects':out,'pending_versions':pending}

def validate(policy, objects, pending_rules, *, allow_future=False):
    m.require(policy.get('format')==1 and policy.get('role')==ROLE and policy.get('reviewed') is True,'Policy is not explicitly reviewed for sakhtyar_runtime')
    rules=policy.get('objects'); m.require(isinstance(rules,dict),'Invalid policy objects')
    existing={key(o):o for o in objects if not o['extension']}
    # Future grants are separate, explicit versioned rules, never a wildcard/default privilege.
    future=set(policy.get('pending_objects',[])) if allow_future else set()
    m.require(future<=set(rules),'Invalid pending object list')
    if allow_future and pending_rules: m.require(not future&set(existing),'Pending objects already exist; regenerate reviewed policy')
    m.require(set(existing)<=set(rules) and set(rules)<=set(existing)|future,'Policy/catalog mismatch; regenerate and review missing or obsolete objects')
    for k, rule in rules.items():
        m.require(isinstance(rule,dict) and rule.get('decision') in ('ALLOW','DENY') and isinstance(rule.get('reason'),str) and bool(rule['reason'].strip()),'Unreviewed object: '+k)
        p=rule.get('privileges'); m.require(isinstance(p,list) and all(isinstance(x,str) for x in p) and len(set(p))==len(p),'Invalid privileges: '+k)
        kind=k.split(':',1)[0]; allowed={'table':DML,'sequence':SEQ_PRIV,'routine':('EXECUTE',)}.get(kind,())
        m.require(set(p)<=set(allowed) and (bool(p)==(rule['decision']=='ALLOW')),'Invalid allow/deny rule: '+k)
        o=existing.get(k)
        if (o and o['immutable_hint']) or k in ('table:contract_revision','table:builder_proposal_revision','table:builder_verification_event'):
            m.require(not set(p)&{'UPDATE','DELETE'},'Append-only privilege rejected: '+k)
        if o and o['kind']=='routine' and o['security_definer']:
            m.require(not p and not o['public_execute'],'Security-definer routine requires separate DBA security review: '+k)
    m.require(rules.get('table:flyway_schema_history',{}).get('privileges')==['SELECT'],'History must be SELECT only')
    for t,rule in pending_rules.items():
        k='table:'+t
        if k in existing or allow_future:
            p=set(rules.get(k,{}).get('privileges',[]))
            m.require(set(rule.get('privileges',[]))<=p and not p&set(rule.get('forbidden',[])),'Missing reviewed migration privileges: '+k)
    return existing

def role_state(c,u):
    return m.query(c,u,"SELECT coalesce((SELECT jsonb_build_object('login',rolcanlogin,'super',rolsuper,'bypass',rolbypassrls,'create_role',rolcreaterole,'create_db',rolcreatedb,'replication',rolreplication,'memberships',EXISTS(SELECT 1 FROM pg_auth_members WHERE member=r.oid OR roleid=r.oid),'owns',EXISTS(SELECT 1 FROM pg_shdepend WHERE refclassid='pg_authid'::regclass AND refobjid=r.oid AND deptype='o')) FROM pg_roles r WHERE rolname='sakhtyar_runtime'),'null'::jsonb);")
def safe_role(state):
    return bool(state and state['login'] and all(state[x] is False for x in ('super','bypass','create_role','create_db','replication','memberships','owns')))
def direct_sql(objects,policy):
    rules=policy['objects']; statements=[]
    for o in objects:
        if o['extension']: continue
        k=key(o); obj=object_sql(o); p=rules[k]['privileges']
        statements.append('REVOKE ALL PRIVILEGES ON '+obj+' FROM '+m.ident(ROLE)+';')
        if p: statements.append('GRANT '+', '.join(p)+' ON '+obj+' TO '+m.ident(ROLE)+';')
    return statements

def effective_sql(o):
    obj=object_sql(o).split(' ',1)[1]
    if o['kind']=='routine':
        # pg_proc identity text is used via to_regprocedure; procedures use same regprocedure lookup.
        fn='has_function_privilege'; priv=('EXECUTE',); target="(SELECT p.oid FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.proname="+m.lit(o['name'])+' AND pg_get_function_identity_arguments(p.oid)='+m.lit(o['args'])+')'
    elif o['kind']=='sequence': fn='has_sequence_privilege'; priv=SEQ_PRIV; target='to_regclass('+m.lit(obj)+')'
    else: fn='has_table_privilege'; priv=TABLE_PRIV; target='to_regclass('+m.lit(obj)+')'
    return "SELECT jsonb_build_object("+','.join(m.lit(p)+','+fn+'('+m.lit(ROLE)+','+target+','+m.lit(p)+')' for p in priv)+');'

COLUMN_ACL_SQL="""EXISTS(SELECT 1 FROM pg_attribute a JOIN pg_class c ON c.oid=a.attrelid JOIN pg_namespace n ON n.oid=c.relnamespace CROSS JOIN LATERAL aclexplode(a.attacl) x WHERE n.nspname='public' AND a.attnum>0 AND NOT a.attisdropped AND (x.grantee=0 OR x.grantee=(SELECT oid FROM pg_roles WHERE rolname='sakhtyar_runtime')))"""

def verify(c,u,objects,policy):
    m.require(safe_role(role_state(c,u)) and m.restricted(m.role_info(c,u,ROLE)),'Runtime role absent, privileged, owner, member, or lacks history SELECT')
    m.require(m.query(c,u,'SELECT to_jsonb('+COLUMN_ACL_SQL+');') is False,'Column-level PUBLIC/runtime grants require separate DBA review')
    # One SQL roundtrip for all object checks; report only names/privileges, never row data.
    statements=[]
    for o in objects:
        if o['extension']: continue
        k=key(o); expr=effective_sql(o).removeprefix('SELECT ').rstrip(';')
        statements.append('SELECT '+m.lit(k)+' AS k, '+expr+' AS p')
    if statements:
        actual=m.query(c,u,"SELECT coalesce(jsonb_object_agg(k,p),'{}'::jsonb) FROM ("+' UNION ALL '.join(statements)+') x;')
        m.require(set(actual)=={key(o) for o in objects if not o['extension']},'Effective privilege inventory incomplete')
        mismatch=[]
        for k,flags in actual.items():
            expected=set(policy['objects'][k]['privileges']); m.require(all(isinstance(v,bool) for v in flags.values()),'Unresolved privilege check: '+k); found={p for p,v in flags.items() if v}
            if found!=expected: mismatch.append({'object':k,'missing':sorted(expected-found),'unexpected':sorted(found-expected)})
        m.require(not mismatch,'Effective privilege mismatch (including PUBLIC): '+json.dumps(mismatch,ensure_ascii=False))
    return True

def password_verifier(password):
    # Limit to printable ASCII to avoid SASLprep ambiguity; PostgreSQL stores this verifier unchanged.
    m.require(len(password)>=24 and all(33<=ord(ch)<=126 for ch in password),'Use at least 24 printable ASCII non-space characters')
    salt=secrets.token_bytes(16); rounds=4096
    salted=hashlib.pbkdf2_hmac('sha256',password.encode('ascii'),salt,rounds)
    client=hmac.digest(salted,b'Client Key','sha256'); stored=hashlib.sha256(client).digest(); server=hmac.digest(salted,b'Server Key','sha256')
    b64=lambda v:base64.b64encode(v).decode('ascii')
    return f'SCRAM-SHA-256${rounds}:{b64(salt)}${b64(stored)}:{b64(server)}'

def provision(c,u,objects,policy,password=None):
    old=role_state(c,u); m.require(old is None or safe_role(old),'Existing role unsafe; never alter privileged or shared roles')
    cmds=['BEGIN;',"SET LOCAL standard_conforming_strings=on;",'SET LOCAL lock_timeout=\'5s\';']
    if old is None:
        m.require(bool(password),'Password required for new role')
        cmds+=['CREATE ROLE '+m.ident(ROLE)+' LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS NOINHERIT PASSWORD '+m.lit(password_verifier(password))+';']
    cmds+=['GRANT CONNECT ON DATABASE '+m.ident(m.DB)+' TO '+m.ident(ROLE)+';', 'GRANT USAGE ON SCHEMA public TO '+m.ident(ROLE)+';', 'REVOKE CREATE ON SCHEMA public FROM '+m.ident(ROLE)+';']
    cmds+=direct_sql(objects,policy)
    # Verify effective privileges inside the same transaction, so any PUBLIC leakage rolls back the changes.
    checks=[]
    for o in objects:
        if o['extension']: continue
        k=key(o); expr=effective_sql(o).removeprefix('SELECT ').rstrip(';'); expected=policy['objects'][k]['privileges']
        checks.append('IF (SELECT coalesce(array_agg(x.key ORDER BY x.key),ARRAY[]::text[]) FROM jsonb_each('+expr+') x WHERE x.value=\'true\'::jsonb) IS DISTINCT FROM '+('ARRAY['+','.join(m.lit(p) for p in sorted(expected))+']::text[]' if expected else 'ARRAY[]::text[]')+' THEN RAISE EXCEPTION '+m.lit('Runtime effective privilege mismatch: '+k)+'; END IF;')
    checks.append('IF '+COLUMN_ACL_SQL+' THEN RAISE EXCEPTION \'Unexpected column ACL\'; END IF;')
    checks.append("IF has_schema_privilege('sakhtyar_runtime','public','CREATE') OR NOT has_schema_privilege('sakhtyar_runtime','public','USAGE') OR NOT has_database_privilege('sakhtyar_runtime',current_database(),'CONNECT') OR NOT has_table_privilege('sakhtyar_runtime','public.flyway_schema_history','SELECT') OR has_table_privilege('sakhtyar_runtime','public.flyway_schema_history','INSERT,UPDATE,DELETE,TRUNCATE') THEN RAISE EXCEPTION 'Runtime schema/history privilege gate'; END IF;")
    cmds+=['DO $role_gate$ BEGIN '+ '\n'.join(checks)+ ' END $role_gate$;','COMMIT;']
    m.psql(c,u,'\n'.join(cmds),readonly=False)
    verify(c,u,objects,policy)

def read_policy(root):
    p=root/POLICY; m.require(p.is_file(),'Reviewed policy absent; run DraftPolicy and review every object')
    m.require(not m.git(root,'status','--porcelain','--untracked-files=all','--',str(POLICY)),'Commit reviewed privilege policy before any apply')
    m.require(bool(m.git(root,'ls-files','--',str(POLICY))),'Reviewed policy must be tracked by Git')
    return json.loads(p.read_text(encoding='utf-8-sig'))

def pending_rules(op): return op.requirements([x['version'] for x in op.pending])

def apply_pending(op,c,u,versions,new_tables):
    """Grant only reviewed new table permissions after Flyway, on clone and approved live target."""
    m.require(op.role==ROLE,'Migration role must be dedicated sakhtyar_runtime')
    policy=read_policy(op.root); m.require(digest(policy)==op.role_policy_digest,'Reviewed role policy changed during operation')
    objects=catalog(c,u); validate(policy,objects,op.requirements(versions))
    m.require(set(new_tables)<=set(op.requirements(versions)),'New tables need migration runtime rules')
    # Do not silently change privileges of pre-existing objects during schema migration.
    new=[o for o in objects if o['kind']=='table' and o['name'] in new_tables and not o['extension']]
    m.require(len(new)==len(new_tables),'New table inventory mismatch')
    future=set(policy.get('pending_objects',[]))
    new_objects=[o for o in objects if key(o) in future and not o['extension']]
    m.require(set(key(o) for o in new)<=future,'New table missing from reviewed pending object list')
    m.require(set(key(o) for o in new_objects)==future,'Reviewed pending catalog differs from migrated catalog')
    if new_objects: provision(c,u,new_objects,policy)
    verify(c,u,objects,policy)

class RoleOperator(m.Operator):
    def execute(self):
        self.source(); self.connect(); self.audit(); self.role=ROLE
        objects=catalog(self.c,self.owner); pending=pending_rules(self)
        (self.work/'catalog.json').write_text(json.dumps(objects,indent=2,ensure_ascii=False),encoding='utf-8')
        self.gate('role_state',role_state(self.c,self.owner)); self.gate('catalog_objects',len(objects))
        acl=m.query(self.c,self.owner,"SELECT coalesce(jsonb_agg(jsonb_build_object('owner',pg_get_userbyid(defaclrole),'schema',defaclnamespace::regnamespace::text,'type',defaclobjtype,'acl',defaclacl::text) ORDER BY defaclrole,defaclnamespace,defaclobjtype),'[]'::jsonb) FROM pg_default_acl;")
        self.report['default_privileges']=acl
        self.report['extension_objects']=sum(o['extension'] for o in objects)
        if self.a.mode=='Audit': return
        if self.a.mode in ('DraftPolicy','DraftClone'):
            current_keys={key(o) for o in objects if not o['extension']}
            if self.a.mode=='DraftClone' and self.pending:
                self.image(); self.fly(self.c,self.password,'validate'); self.backup()
                with self.clone() as (c,u,pw):
                    self.fly(c,pw,'migrate',self.entries[-1]['version']); self.fly(c,pw,'validate')
                    objects=catalog(c,u)
                    (self.work/'clone-migrated-catalog.json').write_text(json.dumps(objects,indent=2,ensure_ascii=False),encoding='utf-8')
            p=draft(objects,[e['version'] for e in self.pending]);
            for t,r in pending.items():
                k='table:'+t
                if k not in p['objects']: p['objects'][k]={'decision':'REVIEW','privileges':r['privileges'],'reason':'V'+str(self.entries[-1]['version'])+' source migration requirements; operator review required'}
                elif p['objects'][k]['decision']=='REVIEW': p['objects'][k]['privileges']=r['privileges']; p['objects'][k]['reason']='Migration source minimum only; review complete runtime requirements'
            p['pending_objects']=sorted(set(p['objects'])-current_keys)
            hints={}
            names={o['name'] for o in objects if not o['extension']}
            for f in (self.root/'backend').rglob('*.java'):
                if 'target' in f.relative_to(self.root).parts or 'src' not in f.parts or 'main' not in f.parts: continue
                try: body=f.read_text(encoding='utf-8')
                except (OSError,UnicodeError): continue
                for n in names.intersection(__import__('re').findall(r'[A-Za-z_][A-Za-z_0-9]*',body)):
                    hints.setdefault(n,[]).append(f.relative_to(self.root).as_posix())
            (self.work/'source-reference-hints.json').write_text(json.dumps(hints,indent=2,ensure_ascii=False),encoding='utf-8')
            path=self.work/'runtime_privileges.DRAFT.json'; path.write_text(json.dumps(p,indent=2,ensure_ascii=False),encoding='utf-8'); self.gate('draft',str(path)); return
        policy=read_policy(self.root); m.require(not self.pending or policy.get('pending_versions')==[e['version'] for e in self.pending],'Reviewed policy pending versions differ'); validate(policy,objects,pending,allow_future=True); state=role_state(self.c,self.owner)
        m.require(state is None or safe_role(state),'Existing runtime role is unsafe')
        token='role:'+ROLE+':'+self.head[:12]+':'+digest(policy)[:16]+':'+digest(objects)[:16]
        self.gate('approval_token',token)
        if self.a.mode=='Plan': return
        if self.a.mode=='Verify': verify(self.c,self.owner,objects,policy); self.gate('effective_privileges','PASS'); return
        if self.a.mode=='ApplyRole': m.require(self.a.approve==token,'Explicit current role approval token required'); m.quiet(self.c,self.owner)
        self.image(); self.fly(self.c,self.password,'validate'); self.backup()
        with self.clone() as (c,u,pw):
            self.fly(c,pw,'validate'); cpw=secrets.token_hex(32)
            provision(c,u,objects,policy,cpw)
            if state is not None: m.psql(c,u,'ALTER ROLE '+m.ident(ROLE)+' PASSWORD '+m.lit(cpw)+';',readonly=False)
            m.require(m.query(c,ROLE,'SELECT to_jsonb(current_user);',password=cpw)==ROLE,'Physical clone login failed')
            self.gate('clone_role','PASS (effective ACLs and TCP login; not password enforcement or application E2E)')
            # Rehearse pending migration and reviewed new grants before permitting role promotion.
            if self.pending:
                self.fly(c,pw,'migrate',self.entries[-1]['version']); self.fly(c,pw,'validate'); self.role_policy_digest=digest(policy)
                new=set(m.tables(c,u))-set(self.before_tables)
                apply_pending(self,c,u,[e['version'] for e in self.pending],new)
                self.check_runtime(c,u,[e['version'] for e in self.pending],True,new)
                self.gate('clone_pending_privileges','PASS')
        if self.a.mode=='DryRun': return
        m.require(self.a.mode=='ApplyRole','Invalid role operation'); m.quiet(self.c,self.owner)
        m.require(m.git(self.root,'rev-parse','HEAD')==self.head and digest(read_policy(self.root))==digest(policy) and m.inspect(m.SOURCE)['Id']==self.c and m.history(self.c,self.owner)==self.before and m.schema_hash(self.c,self.owner)==self.schema_before and m.roles_hash(self.c,self.owner)==self.roles_before and catalog(self.c,self.owner)==objects,'Source/target changed after clone; rerun plan')
        password=None
        if state is None:
            m.require(sys.stdin.isatty(),'Use interactive PowerShell for password input')
            password=getpass.getpass('New runtime password (not echoed): '); confirm=getpass.getpass('Repeat password: ')
            m.require(password==confirm,'Passwords differ'); password_verifier(password)
        m.quiet(self.c,self.owner); provision(self.c,self.owner,objects,policy,password)
        self.gate('role_apply','PASS; configure IntelliJ secret separately and verify real login')

def main():
    p=argparse.ArgumentParser(); p.add_argument('--mode',required=True,choices=['Audit','DraftPolicy','DraftClone','Plan','DryRun','ApplyRole','Verify']); p.add_argument('--root',default=str(m.ROOT)); p.add_argument('--approve'); a=p.parse_args(); a.backup_root=None; a.runtime_role=ROLE
    op=None; lock=None; acquired=False
    try:
        op=RoleOperator(a); lock=op.home/'.lifecycle.lock'; fd=os.open(lock,os.O_CREAT|os.O_EXCL|os.O_WRONLY); os.close(fd); acquired=True
        op.execute(); op.report['status']='PASS'; return 0
    except KeyboardInterrupt:
        if op: op.report.update(status='ABORTED',error='Operator interrupted; inspect state before retry')
        return 130
    except (m.GateError,OSError,ValueError,KeyError,m.subprocess.SubprocessError) as e:
        msg=str(e) if isinstance(e,m.GateError) else type(e).__name__+'; inspect local configuration privately'
        if op: op.report.update(status='BLOCKED',error=msg)
        print('[Role] BLOCKED: '+msg,file=sys.stderr); return 1
    finally:
        if acquired: lock.unlink(missing_ok=True)
        if op: op.save(); print('[Role] Report: '+str(op.file))
if __name__=='__main__': sys.exit(main())
