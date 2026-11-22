#!/usr/bin/env python3
"""Phase07 source-only inventory. NO network, DB, Docker, credentials or source writes.

The resulting OpenAPI is an explicitly INCOMPLETE source-derived inventory. It
MUST NOT be mistaken for a validated runtime OpenAPI contract or security proof.
"""
from __future__ import annotations
import argparse
import json
import pathlib
import re
from collections import Counter
from typing import Iterable

METHOD = {'GetMapping':'GET','PostMapping':'POST','PutMapping':'PUT',
          'PatchMapping':'PATCH','DeleteMapping':'DELETE'}
API_PREFIX = '/api/'


def quoted(s: str) -> list[str]:
    return re.findall(r'"((?:\\.|[^"\\])*)"', s)


def annotation_args(source: str, start: int):
    """Get balanced annotation parentheses, ignoring parentheses in quoted strings."""
    i = start
    while i < len(source) and source[i].isspace(): i += 1
    if i >= len(source) or source[i] != '(':
        return '', i
    i += 1
    begin = i
    depth = 1
    in_quote = False
    escaped = False
    while i < len(source):
        c = source[i]
        if in_quote:
            if escaped: escaped = False
            elif c == '\\': escaped = True
            elif c == '"': in_quote = False
        elif c == '"': in_quote = True
        elif c == '(': depth += 1
        elif c == ')':
            depth -= 1
            if depth == 0:
                return source[begin:i], i + 1
        i += 1
    return None, i


def norm(path: str) -> str:
    if not path:
        return '/'
    path = path.strip()
    path = path.split('?', 1)[0]
    path = re.sub(r'\$\{[^}]+\}', '{param}', path)
    path = re.sub(r'/+', '/', '/' + path.lstrip('/'))
    return path.rstrip('/') or '/'


def combine(base: str, path: str) -> str:
    return norm(base.rstrip('/') + '/' + path.lstrip('/'))


def js_paths(frontend_root: pathlib.Path) -> list[dict]:
    """Extract literal and template requests; dynamic expressions are explicitly uncertain."""
    entries = []
    for file in sorted(frontend_root.rglob('*')):
        if not file.is_file() or file.suffix not in ('.ts','.tsx','.js','.jsx'):
            continue
        text = file.read_text('utf-8-sig')
        # Broad match captures api<Generic>('path', { method: ... }), possibly multiline.
        regex = re.compile(r'\b(?:api(?:<[^;]*?>)?|fetch)\s*\(\s*([\'"`])(/api/.*?)\1', re.S)
        for m in regex.finditer(text):
            raw = m.group(2)
            if '\n' in raw or len(raw) > 350:
                continue
            # Only claim a method if the immediate options object contains a literal method.
            after = text[m.end():m.end()+230]
            method_match = re.search(r'\bmethod\s*:\s*[\'"`](GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)[\'"`]',after,re.I)
            method = method_match.group(1).upper() if method_match else 'GET'
            # Multipart or wrapper dynamic method calls may need a human review.
            uncertain = ('${' in raw and bool(re.search(r'\$\{[^}]*\([^}]*\}',raw))) or '...' in after[:40]
            entries.append({'method':method,'path':norm(raw),'source':file.relative_to(frontend_root.parent.parent).as_posix(),
                            'line':text[:m.start()].count('\n')+1,'confidence':'REVIEW' if uncertain else 'STATIC_LITERAL'})
    return entries


def java_routes(backend_root: pathlib.Path, repo_root: pathlib.Path | None = None) -> tuple[list[dict],list[dict]]:
    routes, unresolved = [], []
    if repo_root is None: repo_root = backend_root.parent.parent
    for file in sorted(backend_root.rglob('*.java')):
        if 'src/test/' in file.as_posix() or '/target/' in file.as_posix():
            continue
        source = file.read_text('utf-8-sig')
        if '@RestController' not in source:
            continue
        # Class-level @RequestMapping is scoped before the first class declaration.
        class_pos = re.search(r'\bclass\s+\w+',source)
        if not class_pos:
            continue
        header = source[:class_pos.start()]
        match = list(re.finditer(r'@RequestMapping\b',header))
        base = ''
        if match:
            args,_ = annotation_args(source,match[-1].end())
            if args is None:
                unresolved.append({'source':file.as_posix(),'reason':'unbalanced_class_mapping'})
                continue
            candidates = quoted(args)
            base = candidates[0] if candidates else ''
        for m in re.finditer(r'@(GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping|RequestMapping)\b',source[class_pos.end():]):
            name=m.group(1)
            pos=class_pos.end()+m.start()
            args,end=annotation_args(source,pos+len(name)+1)
            if args is None:
                unresolved.append({'source':file.as_posix(),'reason':'unbalanced_method_mapping','line':source[:pos].count('\n')+1})
                continue
            if name=='RequestMapping':
                meth_match=re.search(r'RequestMethod\.(GET|POST|PUT|PATCH|DELETE)',args)
                if not meth_match:
                    unresolved.append({'source':file.as_posix(),'reason':'request_mapping_without_literal_method','line':source[:pos].count('\n')+1})
                    continue
                method=meth_match.group(1)
            else: method=METHOD[name]
            candidates = quoted(args) if args else []
            # Response 'produces' strings must not be used as mapping paths.
            if candidates and not (candidates[0].startswith('/') or 'path' in args or 'value' in args):
                candidates=[]
            path=candidates[0] if candidates else ''
            combined=combine(base,path)
            if not combined.startswith(API_PREFIX):
                continue
            routes.append({'method':method,'path':combined,'source':file.relative_to(repo_root).as_posix(),
                           'line':source[:pos].count('\n')+1,'confidence':'SOURCE_ANNOTATION'})
    return routes,unresolved


def route_pattern(route: str) -> re.Pattern:
    # A backend path template matches exactly one segment, plus Ant-style '*'/'**'.
    parts=[]
    for segment in norm(route).strip('/').split('/'):
        if segment=='**': parts.append('(?:.*)')
        elif segment=='*' or segment.startswith('{') and segment.endswith('}'):
            parts.append('[^/]+')
        else:
            parts.append(re.escape(segment).replace(r'\*','[^/]*'))
    return re.compile(r'^/'+'/'.join(parts)+'/?$')


def route_matches(front: dict,backend: list[dict]) -> bool:
    return any(x['method']==front['method'] and route_pattern(x['path']).fullmatch(front['path']) for x in backend)


def enum_java(text: str, enum: str) -> set[str]:
    match=re.search(r'\benum\s+'+re.escape(enum)+r'\s*\{',text)
    if not match: raise ValueError('Java enum not recognized: '+enum)
    source=text[match.end():]
    constants=[]
    depth=0; token=''
    for c in source:
        if c == '(':
            depth+=1
        elif c == ')':
            depth=max(0,depth-1)
        elif c in ',;}' and depth==0:
            name=re.search(r'^\s*([A-Z][A-Z0-9_]+)\b',token)
            if name: constants.append(name.group(1))
            token=''
            if c in ';}': break
            continue
        if depth==0: token+=c
    return set(constants)



def enum_typescript(text: str,name: str) -> set[str]:
    match=re.search(r'export\s+type\s+'+re.escape(name)+r'\s*=\s*(.*?)(?:\n\s*\n|\n\s*export\s+type)',text,re.S)
    if not match: raise ValueError('TypeScript union not found: '+name)
    return set(re.findall(r"'([A-Z][A-Z0-9_]+)'",match.group(1)))


def verify_enums(root: pathlib.Path) -> list[dict]:
    specs=[('Permission','identity/domain/Permission.java'),('UserRole','identity/domain/UserRole.java'),
           ('UserStatus','identity/domain/UserStatus.java'),('CaseStatus','casefile/domain/CaseStatus.java')]
    ts=(root/'frontend/src/api/types.ts').read_text('utf-8-sig')
    checks=[]
    for enum,sub in specs:
        p=root/'backend/modules'/sub.split('/')[0]/'src/main/java/com/sakhtyar'/sub
        if not p.exists():
            # CaseStatus and identity have separate package roots but the declared subpaths are correct.
            raise FileNotFoundError(str(p))
        java=enum_java(p.read_text('utf-8-sig'),enum)
        types=enum_typescript(ts,enum)
        checks.append({'enum':enum,'java':sorted(java),'typescript':sorted(types),'match':java==types})
    return checks


# High-confidence, observed from the pinned GitHub v2 files.
GOLDEN=[
    ('POST','/api/v1/auth/login'),('POST','/api/v1/auth/refresh'),
    ('GET','/api/v1/auth/me'),('GET','/api/v1/auth/csrf'),
    ('GET','/api/v1/cases'),('POST','/api/v1/cases'),
    ('GET','/api/v1/cases/{id}'),('GET','/api/v1/cases/{caseId}/property'),
    ('PUT','/api/v1/cases/{caseId}/property'),('GET','/api/v1/cases/{caseId}/owners'),
    ('POST','/api/v1/cases/{caseId}/owners'),
    ('GET','/api/v1/geo/geocode'),('GET','/api/v1/geo/reverse'),
    ('GET','/api/v1/admin/audit/events'),
    ('GET','/api/v1/admin/quality/catalog')
]


def openapi_draft(routes: list[dict],base: str) -> dict:
    paths={}
    for r in routes:
        key=r['path']; method=r['method'].lower()
        obj=paths.setdefault(key,{})
        if method in obj: continue
        obj[method]={
            'summary':'SOURCE-ONLY annotation inventory; NOT verified API behavior',
            'operationId': 'inventory_'+str(len(paths))+'_'+method,
            'x-origin-source':r['source'],
            'x-contract-status':'UNVERIFIED_RUNTIME',
            'responses':{'default':{'description':'Requires authenticated runtime contract validation'}}
        }
    return {'openapi':'3.1.0','info':{'title':'SakhtYar API SOURCE INVENTORY — not authoritative',
              'version':'phase07-source-only'},'paths':paths,
            'x-reviewed-baseline':base,'x-not-proven':['Runtime mappings','Request DTO schemas',
              'Response bodies','Authorization','OpenAPI compatibility','REAL/TEST isolation']}


def audit(root: pathlib.Path,base: str='unknown') -> dict:
    root=root.resolve()
    backend=root/'backend/modules'
    frontend=root/'frontend/src'
    if not backend.is_dir() or not frontend.is_dir():
        raise ValueError('Expected backend/modules and frontend/src directories')
    routes,unknown=java_routes(backend,root)
    # App controllers are part of the executable source inventory too.
    app_routes,app_unknown=java_routes(root/'backend/app/src/main/java',root)
    routes+=app_routes;unknown+=app_unknown
    callers=js_paths(frontend)
    golden=[{'method':m,'path':p,'found':route_matches({'method':m,'path':p},routes)} for m,p in GOLDEN]
    enums=verify_enums(root)
    missing=[f for f in callers if not route_matches(f,routes) and f['confidence']=='STATIC_LITERAL']
    unclear=[f for f in callers if not route_matches(f,routes) and f['confidence']!='STATIC_LITERAL']
    duplicates=[{'method':m,'path':p,'count':n} for (m,p),n in Counter((r['method'],r['path']) for r in routes).items() if n>1]
    failed=[f'MISSING_GOLDEN:{x["method"]} {x["path"]}' for x in golden if not x['found']]
    failed += ['ENUM_DRIFT:'+e['enum'] for e in enums if not e['match']]
    return {'status':'PASS_SOURCE_GATES_RUNTIME_UNVERIFIED' if not failed else 'BLOCKED_SOURCE_DRIFT',
            'baseline':base,'backend_routes_detected':len(routes),'frontend_calls_detected':len(callers),
            'confirmed_golden':sum(x['found'] for x in golden),'golden_total':len(golden),
            'golden':golden,'enum_contracts':enums,'possible_unmatched_calls':missing[:300],
            'ambiguous_frontend_calls':unclear[:300], 'duplicate_route_candidates':duplicates[:300],
            'unresolved_java_annotations':unknown[:300], 'failed':failed,
            'openapi_source_inventory_only':True,'runtime_api_verified':False,
            'http_integration_verified':False,'postgresql_isolation_verified':False}


def main() -> int:
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--root',required=True)
    p.add_argument('--base',default='unknown')
    p.add_argument('--report-dir')
    a=p.parse_args()
    try:
        root=pathlib.Path(a.root)
        result=audit(root,a.base)
        if a.report_dir:
            out=pathlib.Path(a.report_dir)
            out.mkdir(parents=True,exist_ok=True)
            (out/'phase07-api-audit.json').write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
            routes,_=java_routes(root/'backend/modules',root)
            app_routes,_=java_routes(root/'backend/app/src/main/java',root)
            spec=openapi_draft(routes+app_routes,a.base)
            (out/'phase07-openapi-source-draft.json').write_text(json.dumps(spec,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
            print('Report directory: '+str(out),flush=True)
        summary={k:v for k,v in result.items() if k not in ('golden','enum_contracts','possible_unmatched_calls',
                  'ambiguous_frontend_calls','duplicate_route_candidates','unresolved_java_annotations')}
        summary['possible_unmatched_count']=len(result['possible_unmatched_calls'])
        summary['unresolved_annotations_count']=len(result['unresolved_java_annotations'])
        print(json.dumps(summary,indent=2,ensure_ascii=False),flush=True)
        return 0 if not result['failed'] else 2
    except (ValueError, OSError) as ex:
        print(json.dumps({'status':'BLOCKED','reason':str(ex)}),flush=True)
        return 2


if __name__=='__main__':
    raise SystemExit(main())
