#!/usr/bin/env python3
"""Local-only SakhtYar operator-controlled Flyway lifecycle. Python 3.9+; no third-party modules."""
from __future__ import annotations
import argparse, hashlib, json, os, re, secrets, subprocess, sys, time, uuid
from pathlib import Path
from datetime import datetime, timezone
from contextlib import contextmanager
from urllib.request import build_opener, ProxyHandler

ROOT = Path(__file__).resolve().parents[2]
MIG = Path('backend/app/src/main/resources/db/migration')
IMAGE = 'flyway/flyway:11.15.0'  # same pinned CLI as repository CI
SOURCE = 'sakhtyar-local-db-1'
DB = 'sakhtyar'
POLICY = Path('scripts/database/migration_runtime_policy.json')
class GateError(RuntimeError): pass

def require(ok, message):
    if not ok: raise GateError(message)
def sha(path):
    h=hashlib.sha256()
    with open(path,'rb') as f:
        for chunk in iter(lambda:f.read(1024*1024),b''): h.update(chunk)
    return h.hexdigest()
def ident(v):
    require(bool(re.fullmatch(r'[A-Za-z_][A-Za-z0-9_]{0,62}',v)), 'Unsupported role or identifier; DBA review required')
    return '"'+v+'"'
def lit(v): return "'"+v.replace("'","''")+"'"
def run(args, *, data=None, input_file=None, env=None, target=None, label=None, timeout=3600):
    # Never print argv or raw stderr: SQL errors can include private data.
    with (open(target,'wb') if target else _null()) as out, (open(input_file,'rb') if input_file else _null()) as inp:
        p=subprocess.run([str(x) for x in args],input=data,stdin=inp,stdout=out or subprocess.PIPE,
                         stderr=subprocess.PIPE,env=env,timeout=timeout)
    if p.returncode and target:
        Path(str(target)+'.stderr').write_bytes(p.stderr)
    if p.returncode:
        raise GateError(f'{label or args[0]} failed (exit {p.returncode}); no automatic repair. '
                        'Inspect local service logs privately; do not share secrets.')
    return p.stdout or b''
@contextmanager
def _null(): yield None

def git(root,*args): return run(['git','-C',root,*args],label='Git').decode('utf-8').strip()
def inventory(folder):
    entries={}
    for p in folder.iterdir():
        if not p.is_file() or p.is_symlink(): raise GateError('Unexpected migration directory entry: '+p.name)
        m=re.fullmatch(r'V([1-9][0-9]*)__[A-Za-z0-9_]+\.sql',p.name)
        require(m is not None,'Unsupported migration filename: '+p.name)
        n=int(m[1]); require(n not in entries,'Duplicate migration version')
        require(bool(p.read_text(encoding='utf-8-sig').strip()),'Empty migration: '+p.name)
        entries[n]={'version':n,'name':p.name,'sha256':sha(p)}
    require(bool(entries) and sorted(entries)==list(range(1,max(entries)+1)),'Non-contiguous migration versions')
    return [entries[n] for n in sorted(entries)]
def history_gate(rows, entries):
    require(isinstance(rows,list) and bool(rows),'Missing/empty history; bootstrap not supported')
    require(all(r.get('success') is True and r.get('type')=='SQL' for r in rows),'Failed or unsupported history row; DBA review required')
    versions=[str(r.get('version')) for r in rows]
    require(versions==[str(i) for i in range(1,len(rows)+1)],'History is not contiguous in installation order')
    require(len(rows)<=len(entries),'Database ahead of source')
    for r,e in zip(rows,entries):
        require(r.get('script')==e['name'] and isinstance(r.get('checksum'),int),'History filename/checksum mismatch or absent checksum')
    return entries[len(rows):]

def docker(*args,**kwargs): return run(['docker',*args],**kwargs)
def inspect(container): return json.loads(docker('inspect',container,label='Container inspect'))[0]
def psql(container, user, sql, db=DB, password=None, readonly=True):
    env=dict(os.environ); opts=[]; host=[]
    if password is not None:
        env['PGPASSWORD']=password; opts=['-e','PGPASSWORD']; host=['-h','127.0.0.1']
    statement=('BEGIN READ ONLY;\n'+sql+'\nCOMMIT;\n') if readonly else sql
    return docker('exec','-i',*opts,container,'psql','-X','-q','-A','-t','-w','-v','ON_ERROR_STOP=1',*host,'-U',user,'-d',db,
                  data=statement.encode('utf-8'),env=env,label='PostgreSQL query').decode('utf-8').strip()
def query(container,user,sql,**kw): return json.loads(psql(container,user,sql,**kw))
HISTORY_SQL="""SELECT coalesce(jsonb_agg(to_jsonb(h) ORDER BY installed_rank),'[]'::jsonb) FROM (SELECT installed_rank,version,script,type,checksum,success,installed_by FROM public.flyway_schema_history) h;"""
TABLE_SQL="""SELECT coalesce(jsonb_agg(c.relname ORDER BY c.relname),'[]'::jsonb) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind IN ('r','p');"""
def history(c,u): return query(c,u,HISTORY_SQL)
def tables(c,u): return query(c,u,TABLE_SQL)
def role_info(c,u,role):
    ident(role)
    return query(c,u,f"""SELECT coalesce((SELECT jsonb_build_object('login',r.rolcanlogin,'superuser',r.rolsuper,'bypassrls',r.rolbypassrls,'createrole',r.rolcreaterole,'createdb',r.rolcreatedb,'replication',r.rolreplication,'elevated_membership',EXISTS(SELECT 1 FROM pg_roles x WHERE (x.rolsuper OR x.rolbypassrls OR x.rolcreaterole OR x.rolcreatedb OR x.rolreplication) AND pg_has_role(r.oid,x.oid,'MEMBER')),'owns_public_tables',EXISTS(SELECT 1 FROM pg_class t JOIN pg_namespace n ON n.oid=t.relnamespace WHERE n.nspname='public' AND t.relkind IN ('r','p') AND pg_has_role(r.oid,t.relowner,'MEMBER')),'owns_database',pg_has_role(r.oid,(SELECT datdba FROM pg_database WHERE datname=current_database()),'MEMBER'),'owns_public_schema',pg_has_role(r.oid,(SELECT nspowner FROM pg_namespace WHERE nspname='public'),'MEMBER'),'schema_create',has_schema_privilege(r.oid,'public','CREATE'),'schema_usage',has_schema_privilege(r.oid,'public','USAGE'),'db_connect',has_database_privilege(r.oid,current_database(),'CONNECT'),'history_read',has_table_privilege(r.oid,'public.flyway_schema_history','SELECT'),'history_write',has_table_privilege(r.oid,'public.flyway_schema_history','INSERT') OR has_table_privilege(r.oid,'public.flyway_schema_history','UPDATE') OR has_table_privilege(r.oid,'public.flyway_schema_history','DELETE') OR has_table_privilege(r.oid,'public.flyway_schema_history','TRUNCATE')) FROM pg_roles r WHERE rolname={lit(role)}),'null'::jsonb);""")
def restricted(info):
    return bool(info and all(info.get(k) is True for k in ('login','schema_usage','db_connect','history_read')) and all(info.get(k) is False for k in ('superuser','bypassrls','createrole','createdb','replication','elevated_membership','owns_public_tables','owns_database','owns_public_schema','schema_create','history_write')))

def normalized_dump_sha(b):
    # PostgreSQL emits a random \restrict key in newer releases.
    b=b'\n'.join(x for x in b.splitlines() if not x.startswith((b'\\restrict ',b'\\unrestrict ')))
    return hashlib.sha256(b).hexdigest()
def schema_hash(c,u):
    return normalized_dump_sha(docker('exec',c,'pg_dump','-w','-U',u,'-d',DB,'--schema-only',label='Schema fingerprint'))
def roles_hash(c,u):
    return normalized_dump_sha(docker('exec',c,'pg_dumpall','-w','-U',u,'-l','postgres','--roles-only','--no-role-passwords',label='Role fingerprint'))
def quiet(c,u):
    n=query(c,u,"SELECT to_jsonb(count(*)) FROM pg_stat_activity WHERE datname=current_database() AND pid<>pg_backend_pid() AND backend_type='client backend';")
    require(n==0,'Other clients connected to sakhtyar; stop IDE/backend/workers and close DB consoles first')

class Operator:
    def __init__(self,a):
        self.a=a; self.root=Path(a.root).resolve(); self.report={'mode':a.mode,'utc':datetime.now(timezone.utc).isoformat(),'status':'RUNNING','gates':{}}
        self.home=Path(a.backup_root).resolve() if a.backup_root else self.root.parent/'DatabaseBackups'/'MigrationLifecycle'
        require(self.root not in self.home.parents and self.home!=self.root,'Backups must be outside repository')
        self.home.mkdir(parents=True,exist_ok=True,mode=0o700)
        self.work=self.home/(datetime.now().strftime('%Y%m%d-%H%M%S')+'-'+uuid.uuid4().hex[:8]); self.work.mkdir(mode=0o700)
        self.file=self.work/'report.json'; self.role=a.runtime_role or os.environ.get('SAKHTYAR_RUNTIME_DB_USER') or os.environ.get('SPRING_DATASOURCE_USERNAME')
        self.report['runtime_role']=self.role
    def save(self): self.file.write_text(json.dumps(self.report,indent=2,ensure_ascii=False),encoding='utf-8')
    def gate(self,k,v): self.report['gates'][k]=v; self.save(); print(f'[Migration] {k}: {v}',flush=True)
    def source(self):
        require(git(self.root,'branch','--show-current')=='v2','Expected branch v2')
        require(not git(self.root,'status','--porcelain','--untracked-files=all','--',str(MIG)),'Uncommitted migrations; commit/review before DB use')
        self.head=git(self.root,'rev-parse','HEAD'); self.entries=inventory(self.root/MIG)
        tracked=set(git(self.root,'ls-files','--',str(MIG)).splitlines())
        require(tracked=={(MIG/e['name']).as_posix() for e in self.entries},'Migration inventory differs from tracked Git files')
        self.report.update(head=self.head, migrations=self.entries, target=self.entries[-1]['version'], approval_token=f'{DB}:V{self.entries[-1]["version"]}:{self.head[:12]}')
        self.policy=json.loads((self.root/POLICY).read_text(encoding='utf-8')); self.policy_sha=sha(self.root/POLICY); self.report['runtime_policy_sha256']=self.policy_sha
        # Stage exact bytes, never mount mutable worktree into Flyway.
        self.stage=self.work/'sql'; self.stage.mkdir()
        for e in self.entries:
            src=self.root/MIG/e['name']; dst=self.stage/e['name']; dst.write_bytes(src.read_bytes()); require(sha(dst)==e['sha256'],'Migration changed while staging')
        self.gate('source_inventory','PASS')
    def connect(self):
        context=json.loads(docker('context','inspect',label='Docker context'))[0]
        endpoint=context['Endpoints']['docker']['Host']
        require(endpoint.startswith(('npipe://','unix://')) and not os.environ.get('DOCKER_HOST') and not os.environ.get('DOCKER_CONTEXT'),'Only default local Docker socket/context allowed; unset overrides')
        d=inspect(SOURCE); labels=d['Config']['Labels'] or {}
        require(d['State']['Running'] and labels.get('com.docker.compose.project')=='sakhtyar-local' and labels.get('com.docker.compose.service')=='db','Unexpected local PostgreSQL container')
        self.c=d['Id']; self.pg_image=d['Image']; self.source_mounts={m['Source'] for m in d.get('Mounts',[])}; self.report.update(container_id=self.c,postgres_image=self.pg_image)
        cfg=dict(x.split('=',1) for x in d['Config']['Env'] if '=' in x)
        self.owner=cfg.get('POSTGRES_USER','postgres'); ident(self.owner)
        self.user=os.environ.get('SAKHTYAR_FLYWAY_USER') or self.owner; ident(self.user)
        self.password=os.environ.get('SAKHTYAR_FLYWAY_PASSWORD') or (cfg.get('POSTGRES_PASSWORD') if self.user==self.owner else None)
        require(cfg.get('POSTGRES_DB',DB)==DB,'Unexpected database name')
        require(query(self.c,self.owner,"SELECT jsonb_build_object('db',current_database(),'schema',current_schema());")=={'db':DB,'schema':'public'},'Unexpected database/schema')
        self.gate('target_identity','PASS')
    def image(self):
        # Never silently pull or substitute a different tag.
        self.fly_image=docker('image','inspect',IMAGE,'--format','{{.Id}}',label='Pinned Flyway image (pull explicitly if missing)').decode().strip()
        require(self.fly_image.startswith('sha256:'),'Invalid image identity'); self.report['flyway_image']=self.fly_image
    def fly(self,c,password,action,target=None):
        require(bool(password),'Missing migration credential; set SAKHTYAR_FLYWAY_PASSWORD securely')
        require(action in ('validate','migrate'),'Unsupported Flyway operation')
        require(all(sha(self.stage/e['name'])==e['sha256'] for e in self.entries),'Staged migration changed')
        env=dict(os.environ,FLYWAY_PASSWORD=password)
        args=['run','--rm','--network','container:'+c,'-e','FLYWAY_PASSWORD','--mount',f'type=bind,source={self.stage},target=/flyway/sql,readonly',self.fly_image,
              '-url=jdbc:postgresql://127.0.0.1:5432/'+DB,'-user='+self.user,'-schemas=public','-defaultSchema=public','-table=flyway_schema_history','-locations=filesystem:/flyway/sql','-cleanDisabled=true','-baselineOnMigrate=false','-outOfOrder=false','-validateOnMigrate=true','-validateMigrationNaming=true','-ignoreMigrationPatterns=*:pending','-connectRetries=3']
        if target is not None: args+=['-target='+str(target)]
        # Keep detailed Flyway stdout private in the operator backup directory.
        logfile=self.work/(action+'-'+uuid.uuid4().hex[:8]+'.log')
        docker(*args,action,env=env,target=logfile,label='Flyway '+action+' (private log: '+logfile.name+')')
    def audit(self):
        self.before=history(self.c,self.owner); self.pending=history_gate(self.before,self.entries); self.before_tables=tables(self.c,self.owner)
        self.report.update(history=self.before,pending=self.pending)
        self.gate('pending_versions',[x['version'] for x in self.pending])
        self.gate('runtime_role_audit',role_info(self.c,self.owner,self.role) if self.role else 'UNSPECIFIED')
        if not self.role:
            self.gate('restricted_login_candidates',query(self.c,self.owner,"SELECT coalesce(jsonb_agg(rolname ORDER BY rolname),'[]'::jsonb) FROM pg_roles WHERE rolcanlogin AND NOT rolsuper AND NOT rolbypassrls AND NOT rolcreaterole AND NOT rolcreatedb AND NOT rolreplication;"))
        # SQL read-only checks, no role grants or schema mutation.
        if any(e['version']==68 for e in self.pending):
            facts=query(self.c,self.owner,"""SELECT jsonb_build_object('prerequisites',to_regclass('public.builder_profile') IS NOT NULL AND to_regclass('public.construction_case') IS NOT NULL AND to_regclass('public.app_user') IS NOT NULL AND to_regclass('public.project_builder') IS NOT NULL AND to_regprocedure('public.sakhtyar_core_scope_ok(character varying,uuid)') IS NOT NULL,'duplicate_groups',(SELECT count(*) FROM (SELECT lower(national_identifier) FROM public.builder_profile WHERE national_identifier IS NOT NULL GROUP BY 1 HAVING count(*)>1) d));""")
            self.gate('v68_preflight',facts); require(facts=={'prerequisites':True,'duplicate_groups':0},'V68 preconditions fail')
    def backup(self):
        self.dump=self.work/'database.dump'; self.roles=self.work/'roles-no-passwords.sql'
        self.schema_before=schema_hash(self.c,self.owner); self.roles_before=roles_hash(self.c,self.owner)
        docker('exec',self.c,'pg_dump','-w','-U',self.owner,'-d',DB,'-Fc','--create',target=self.dump,label='Database backup')
        docker('exec',self.c,'pg_dumpall','-w','-U',self.owner,'-l','postgres','--roles-only','--no-role-passwords',target=self.roles,label='Role metadata backup')
        require(self.dump.stat().st_size>0 and self.roles.stat().st_size>0,'Empty backup')
        docker('exec','-i',self.c,'pg_restore','--list',input_file=self.dump,target=self.work/'archive-list.txt',label='Backup archive list')
        self.report['backup']={p.name:{'sha256':sha(p),'bytes':p.stat().st_size} for p in (self.dump,self.roles)}
        require(history(self.c,self.owner)==self.before and schema_hash(self.c,self.owner)==self.schema_before and roles_hash(self.c,self.owner)==self.roles_before and normalized_dump_sha(self.roles.read_bytes())==self.roles_before,'Source history/schema/roles changed during backup')
        self.gate('backup_archive','PASS (restore not yet proven)')
    @contextmanager
    def clone(self):
        name='sakhtyar-migration-clone-'+uuid.uuid4().hex[:12]; admin='clone_admin_'+uuid.uuid4().hex[:12]; pw=secrets.token_hex(24)
        env=dict(os.environ,POSTGRES_PASSWORD=pw)
        clone_id=None
        try:
            # network none = no published ports or external egress, fresh anonymous image volume.
            clone_id=docker('run','-d','--name',name,'--network','none','--label','sakhtyar.migration.clone=true','-e','POSTGRES_USER='+admin,'-e','POSTGRES_DB=postgres','-e','POSTGRES_PASSWORD',self.pg_image,env=env,label='Start isolated clone').decode().strip()
            d=inspect(clone_id); require(d['HostConfig']['NetworkMode']=='none' and not d['HostConfig']['PortBindings'] and not self.source_mounts.intersection(m['Source'] for m in d.get('Mounts',[])),'Clone isolation failed')
            deadline=time.monotonic()+120
            while True:
                try: docker('exec',clone_id,'pg_isready','-h','127.0.0.1','-U',admin,'-d','postgres',timeout=10,label='Clone readiness'); break
                except GateError:
                    require(time.monotonic()<deadline,'Clone readiness timeout'); time.sleep(2)
            require(all(sha(p)==self.report['backup'][p.name]['sha256'] for p in (self.dump,self.roles)),'Backup hash mismatch')
            docker('exec','-i',clone_id,'psql','-X','-q','-w','-v','ON_ERROR_STOP=1','-U',admin,'-d','postgres',data=self.roles.read_bytes(),label='Restore clone role metadata')
            docker('cp',self.dump,f'{clone_id}:/tmp/sakhtyar-lifecycle.dump',label='Copy archive to clone')
            docker('exec',clone_id,'pg_restore','-w','--exit-on-error','--create','-U',admin,'-d','postgres','/tmp/sakhtyar-lifecycle.dump',label='Restore cloned database')
            require(history(clone_id,admin)==self.before and tables(clone_id,admin)==self.before_tables,'Clone history/table inventory differs from source')
            self.gate('restore_drill','PASS (archive restored and history/table inventory matched)')
            self.report['clone_schema_matches_source']=schema_hash(clone_id,self.owner)==self.schema_before
            require(self.report['clone_schema_matches_source'],'Restored schema fingerprint differs; review restore or dump variance before proceeding')
            cpw=secrets.token_hex(24)
            psql(clone_id,admin,f'ALTER ROLE {ident(self.user)} PASSWORD {lit(cpw)};',readonly=False)
            yield clone_id,admin,cpw
        finally:
            if clone_id:
                # Only remove the exact created ID; never delete original volumes or named volumes.
                d=inspect(clone_id)
                require(d['Name']=='/'+name and d['Config']['Labels'].get('sakhtyar.migration.clone')=='true','Clone cleanup identity mismatch')
                docker('rm','-f','-v',clone_id,label='Remove disposable clone and anonymous volumes'); self.gate('clone_cleanup','PASS')
    def requirements(self,versions):
        result={}
        for v in versions:
            item=self.policy.get(str(v)); require(isinstance(item,dict) and isinstance(item.get('tables'),dict),'Missing reviewed runtime policy for V'+str(v))
            result.update(item['tables'])
        return result
    def check_runtime(self,c,u,versions,physical=False,new_tables=None):
        require(bool(self.role),'Runtime role unspecified; use -RuntimeRole with actual restricted application login')
        info=role_info(c,u,self.role); self.gate('runtime_role_'+('clone' if physical else 'database'),info)
        require(restricted(info),'Runtime role is not restricted or lacks read-only history access; DBA review required')
        req=self.requirements(versions)
        if new_tables is not None: require(set(new_tables)<=set(req),'New public tables lack reviewed runtime policy: '+','.join(sorted(set(new_tables)-set(req))))
        for table,rule in req.items():
            ident(table); require(isinstance(rule,dict),'Invalid table policy')
            privs=rule.get('privileges',[]); require(all(x in ('SELECT','INSERT','UPDATE','DELETE') for x in privs),'Invalid privilege policy')
            tests=[f"has_table_privilege({lit(self.role)},{lit('public.'+table)},{lit(p)})" for p in privs]
            for p in rule.get('forbidden',[]): require(p in ('SELECT','INSERT','UPDATE','DELETE','TRUNCATE','REFERENCES','TRIGGER'),'Invalid forbidden privilege')
            tests+= [f"NOT has_table_privilege({lit(self.role)},{lit('public.'+table)},{lit(p)})" for p in rule.get('forbidden',[])]
            tests+= [f"(SELECT relrowsecurity AND relforcerowsecurity FROM pg_class WHERE oid=to_regclass({lit('public.'+table)}))"] if rule.get('rls') else []
            for trig in rule.get('triggers',[]):
                tests += [f"EXISTS(SELECT 1 FROM pg_trigger WHERE tgrelid=to_regclass({lit('public.'+table)}) AND tgname={lit(trig)} AND tgenabled IN ('O','A') AND NOT tgisinternal)"]
            for pol in rule.get('policies',[]):
                tests += [f"EXISTS(SELECT 1 FROM pg_policy WHERE polrelid=to_regclass({lit('public.'+table)}) AND polname={lit(pol)})"]
            sql='SELECT to_jsonb(coalesce(to_regclass('+lit('public.'+table)+') IS NOT NULL'+(' AND '+' AND '.join(tests) if tests else '')+',false));'
            require(query(c,u,sql) is True,'Runtime privilege/schema policy failed: '+table)
        if physical:
            pw=secrets.token_hex(24); psql(c,u,f'ALTER ROLE {ident(self.role)} PASSWORD {lit(pw)};',readonly=False)
            require(query(c,self.role,'SELECT to_jsonb(current_user);',password=pw)==self.role,'Physical clone login mismatch')
            if 'builder_invitation' in req:
                res=query(c,self.role,"""SET LOCAL app.sakhtyar_scope='TEST'; SET LOCAL app.sakhtyar_workspace='00000000-0000-0000-0000-000000000001'; SELECT jsonb_build_array((SELECT count(*) FROM public.builder_invitation),(SELECT count(*) FROM public.builder_proposal),(SELECT count(*) FROM public.builder_proposal_revision));""",password=pw)
                require(res==[0,0,0],'Builder workflow exposed rows under TEST context')
                # Positive identity and scope function proof; no business writes or outbound services.
                require(query(c,self.role,"""SELECT jsonb_build_array(public.sakhtyar_core_scope_ok('REAL',NULL),public.sakhtyar_core_scope_ok('TEST','00000000-0000-0000-0000-000000000001'));""",password=pw)==[True,False],'REAL default scope contract failed')
                # A negative write is permitted only in the disposable clone, inside a rolled-back transaction.
                negative = """BEGIN; SET LOCAL app.sakhtyar_scope='TEST'; SET LOCAL app.sakhtyar_workspace='00000000-0000-0000-0000-000000000001'; INSERT INTO public.builder_invitation(id,case_id,builder_id,status,expires_at,created_by,created_at,updated_at) VALUES (gen_random_uuid(),gen_random_uuid(),gen_random_uuid(),'PENDING',now()+interval '1 day','migration-clone-probe',now(),now()); ROLLBACK;"""
                proc=subprocess.run(['docker','exec','-i','-e','PGPASSWORD',c,'psql','-X','-q','-w','-v','ON_ERROR_STOP=1','-v','VERBOSITY=verbose','-h','127.0.0.1','-U',self.role,'-d',DB],input=negative.encode(),stdout=subprocess.PIPE,stderr=subprocess.PIPE,env=dict(os.environ,PGPASSWORD=pw),timeout=30)
                require(proc.returncode!=0 and b'42501' in proc.stderr and b'Builder workflow REAL case scope mismatch' in proc.stderr,'Clone negative TEST insert did not prove expected guard')
                self.gate('v68_negative_test_insert','PASS (rolled-back disposable clone only)')
            self.gate('physical_clone_runtime','PASS (login, grants and limited scope probes; not full E2E)')
        self.gate('runtime_policy','PASS')
    def clone_preflight(self, migrate):
        with self.clone() as (c,u,pw):
            self.fly(c,pw,'validate')
            self.gate('flyway_clone_checksum','PASS')
            if migrate:
                self.fly(c,pw,'migrate',self.entries[-1]['version']); self.fly(c,pw,'validate')
                require(not history_gate(history(c,u),self.entries),'Clone still has pending migrations')
                new=set(tables(c,u))-set(self.before_tables); self.report['new_tables']=sorted(new)
                versions=[e['version'] for e in self.pending]
                self.check_runtime(c,u,versions,True,new)
                self.report['clone_migrated_schema_sha256']=schema_hash(c,self.owner)
                self.gate('clone_migration','PASS')
    def execute(self):
        self.source(); self.connect(); self.audit()
        mode=self.a.mode
        if mode=='Audit': return
        if mode=='Backup': self.backup(); return
        self.image(); self.fly(self.c,self.password,'validate'); self.gate('live_flyway_checksum','PASS')
        if mode=='Plan':
            print('[Migration] Approval token (not a password): '+self.report['approval_token']); return
        if mode=='Verify':
            require(not self.pending,'Pending migrations remain')
            self.check_runtime(self.c,self.owner,[int(v) for v in self.policy if int(v)<=self.entries[-1]['version']]); self.gate('schema_history','CURRENT')
            if self.a.check_backend:
                with build_opener(ProxyHandler({})).open('http://127.0.0.1:8080/actuator/health/readiness',timeout=10) as response:
                    health=json.load(response)
                require(health.get('status')=='UP','Backend readiness is not UP'); self.gate('backend_readiness','UP (not business E2E)')
            return
        if mode=='ApplyDatabase':
            require(self.a.approve==f'{DB}:V{self.entries[-1]["version"]}:{self.head[:12]}','Explicit approval token required for current target and Git HEAD')
            require(bool(self.pending),'No pending migrations; use Verify')
            quiet(self.c,self.owner)
        self.backup(); self.clone_preflight(mode!='Clone')
        if mode in ('Clone','DryRun'): return
        require(mode=='ApplyDatabase','Unknown execution mode')
        require(git(self.root,'rev-parse','HEAD')==self.head and inventory(self.root/MIG)==self.entries and not git(self.root,'status','--porcelain','--untracked-files=all','--',str(MIG)),'Source changed during preflight')
        require(sha(self.root/POLICY)==self.policy_sha,'Runtime policy changed during preflight')
        require(inspect(SOURCE)['Id']==self.c,'Target container changed')
        require(history(self.c,self.owner)==self.before and schema_hash(self.c,self.owner)==self.schema_before and roles_hash(self.c,self.owner)==self.roles_before,'Live schema/history/role drift since backup')
        quiet(self.c,self.owner)
        self.gate('live_apply','STARTED; failure requires DBA review, never automatic restore')
        self.fly(self.c,self.password,'migrate',self.entries[-1]['version']); self.fly(self.c,self.password,'validate')
        require(not history_gate(history(self.c,self.owner),self.entries),'Live migration history incomplete')
        require(set(tables(self.c,self.owner))-set(self.before_tables)==set(self.report['new_tables']),'Live new table inventory differs from clone')
        require(schema_hash(self.c,self.owner)==self.report['clone_migrated_schema_sha256'],'Live schema fingerprint differs from migrated clone; do not rerun automatically')
        self.check_runtime(self.c,self.owner,[e['version'] for e in self.pending],False,self.report['new_tables']); self.gate('live_apply','PASS; backend restart and UI tests still required')

def main():
    p=argparse.ArgumentParser(); p.add_argument('--mode',required=True,choices=['Audit','Plan','Backup','Clone','DryRun','ApplyDatabase','Verify','Report']); p.add_argument('--root',default=str(ROOT)); p.add_argument('--backup-root'); p.add_argument('--runtime-role'); p.add_argument('--approve'); p.add_argument('--check-backend',action='store_true'); a=p.parse_args()
    op=None; lock=None
    try:
        if a.mode=='Report':
            home=Path(a.backup_root) if a.backup_root else Path(a.root).resolve().parent/'DatabaseBackups'/'MigrationLifecycle'
            reports=sorted(home.glob('*/report.json')); require(bool(reports),'No reports'); print(reports[-1].read_text(encoding='utf-8')); return 0
        op=Operator(a); lock=op.home/'.lifecycle.lock'; fd=os.open(lock,os.O_CREAT|os.O_EXCL|os.O_WRONLY); os.close(fd)
        try:
            op.execute(); op.report['status']='PASS'; return 0
        finally: lock.unlink(missing_ok=True)
    except KeyboardInterrupt:
        if op: op.report.update(status='ABORTED',error='Operator interrupted; inspect state before retry')
        return 130
    except (GateError,OSError,ValueError,KeyError,subprocess.SubprocessError) as e:
        msg=str(e) if isinstance(e,GateError) else f'{type(e).__name__}: execution stopped; inspect local configuration privately'
        if op: op.report.update(status='BLOCKED',error=msg)
        print('[Migration] BLOCKED: '+msg,file=sys.stderr); return 1
    finally:
        if op: op.save(); print('[Migration] Report: '+str(op.file),flush=True)
if __name__=='__main__': sys.exit(main())
