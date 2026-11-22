#!/usr/bin/env python3
"""Read-only SakhtYar Shared Kernel contract/dependency audit. No database or network access."""
from __future__ import annotations
import argparse
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path

BASE='8fb8b5ad6fa1c7ec1cbb64b478914473378bf8d0'
N={'m':'http://maven.apache.org/POM/4.0.0'}
REQUEST_FIELDS={'schemaVersion','requestId','correlationId','intent','context','data','metadata','createdAt'}
RESPONSE_FIELDS={'schemaVersion','requestId','correlationId','status','data','warnings','missingFields','confidence','metadata','completedAt'}

class ContractViolation(RuntimeError): pass

def load_maven_graph(backend:Path)->dict[str,set[str]]:
    parent=backend/'pom.xml'
    if not parent.is_file(): raise ContractViolation('Backend parent POM missing')
    try:base=ET.parse(parent).getroot()
    except ET.ParseError as e: raise ContractViolation('Invalid parent POM') from e
    project_version=base.findtext('m:version',namespaces=N)
    if not project_version: raise ContractViolation('Parent version is missing')
    modules=[x.text for x in base.findall('./m:modules/m:module',N)]
    if not modules or len(set(modules))!=len(modules):raise ContractViolation('Missing or duplicate Maven modules')
    graph={}
    manifests={}
    for mod in modules:
        if not re.fullmatch(r'(?:modules/[a-z][a-z0-9-]*|app)',mod or ''):
            raise ContractViolation('Unexpected module path: '+str(mod))
        dest=backend/mod/'pom.xml'
        if not dest.is_file(): raise ContractViolation('Module POM missing: '+mod)
        try:root=ET.parse(dest).getroot()
        except ET.ParseError as e: raise ContractViolation('Invalid POM: '+mod) from e
        artifact=root.findtext('m:artifactId',namespaces=N)
        parent_version=root.findtext('m:parent/m:version',namespaces=N)
        if not artifact or artifact in manifests:
            raise ContractViolation('Missing or duplicate Maven artifact: '+str(artifact))
        if parent_version!=project_version:
            raise ContractViolation('Module parent version drift: '+artifact)
        manifests[artifact]=root
    for artifact,root in manifests.items():
        refs=set()
        for dep in root.findall('./m:dependencies/m:dependency',N):
            group=dep.findtext('m:groupId',namespaces=N)
            key=dep.findtext('m:artifactId',namespaces=N)
            version=dep.findtext('m:version',namespaces=N)
            if group=='com.sakhtyar' and key in manifests:
                if version is not None and version not in ('${project.version}', '${revision}',project_version):
                    raise ContractViolation('Internal dependency version drift: '+artifact+' -> '+key)
                refs.add(key)
        graph[artifact]=refs
    if 'sakhtyar-shared-kernel' not in graph: raise ContractViolation('Shared kernel is absent')
    if graph['sakhtyar-shared-kernel']:raise ContractViolation('Shared kernel cannot depend on business modules')
    return graph

def cycles(graph:dict[str,set[str]])->list[str]:
    visiting=set();done=set();order=[]
    def visit(x):
        if x in visiting:raise ContractViolation('Maven dependency cycle detected: '+' -> '.join(order+[x]))
        if x in done:return
        visiting.add(x);order.append(x)
        for child in sorted(graph[x]):
            if child not in graph:raise ContractViolation('Unknown internal Maven dependency: '+child)
            visit(child)
        order.pop();visiting.remove(x);done.add(x)
    for node in sorted(graph):visit(node)
    return sorted(done)

def schema_contract(doc:dict,required:set[str],name:str):
    if doc.get('type')!='object' or doc.get('additionalProperties') is not False:
        raise ContractViolation(name+': object envelope not strict')
    props=doc.get('properties')
    if not isinstance(props,dict) or not required.issubset(props):
        raise ContractViolation(name+': missing JSON fields')
    if set(doc.get('required',[]))!=required:
        raise ContractViolation(name+': changed JSON required fields')
    if props['schemaVersion'].get('const')!='2.0':
        raise ContractViolation(name+': schema version drift')
    for f in ('createdAt' if name=='request' else 'completedAt',):
        if props[f].get('format')!='date-time':raise ContractViolation(name+': datetime contract drift')

def audit(root:Path)->dict:
    backend=root/'backend'
    graph=load_maven_graph(backend)
    order=cycles(graph)
    base=backend/'modules/shared-kernel/src/main'
    schema=base/'resources/contracts'
    for n,required,kind in [('agent-request-v2.schema.json',REQUEST_FIELDS,'request'),
                            ('agent-response-v2.schema.json',RESPONSE_FIELDS,'response')]:
        file=schema/n
        if not file.is_file():raise ContractViolation(kind+' schema missing')
        try:doc=json.loads(file.read_text(encoding='utf-8-sig'))
        except (ValueError,UnicodeDecodeError) as e:raise ContractViolation('Invalid '+kind+' schema') from e
        schema_contract(doc,required,kind)
    for f,patterns in {
        'contract/CanonicalAgentResponse.java':['Double.isFinite(confidence)','Math.max(0.0d, Math.min(1.0d, confidence))'],
        'xml/XmlContractValidator.java':['MAX_XML_CHARACTERS','ACCESS_EXTERNAL_DTD','ACCESS_EXTERNAL_SCHEMA','XMLConstants.FEATURE_SECURE_PROCESSING'],
        'api/ApiExceptionHandler.java':['INVALID_ARGUMENT','VALIDATION_FAILED','fieldErrors']
    }.items():
        src=base/'java/com/sakhtyar/shared'/f
        if not src.is_file():raise ContractViolation('Java source missing: '+f)
        contents=src.read_text(encoding='utf-8-sig')
        for pattern in patterns:
            if pattern not in contents:raise ContractViolation('Contract source marker missing: '+f+':'+pattern)
    return {'status':'PASS_SOURCE_CONTRACTS_ONLY','module_count':len(order),
            'internal_dependencies':sum(map(len,graph.values())),
            'acyclic':True,'json_schemas':['agent-request-v2','agent-response-v2'],
            'database_changed':False,'production_runtime_tested':False,
            'cross_module_integration_tested':False}

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--root',default='.')
    args=parser.parse_args()
    try: print(json.dumps(audit(Path(args.root).resolve()),indent=2,ensure_ascii=False))
    except ContractViolation as e:
        print(json.dumps({'status':'BLOCKED','reason':str(e)},ensure_ascii=False))
        return 2
    return 0
if __name__=='__main__':raise SystemExit(main())
