#!/usr/bin/env python3
"""Secret-safe deployment .env validation: never prints values or touches infrastructure."""
from __future__ import annotations
import argparse, json, re
from pathlib import Path

class PreflightFailure(ValueError):pass
FIELDS=('POSTGRES_DB','POSTGRES_USER','POSTGRES_PASSWORD',
  'SAKHTYAR_RUNTIME_DB_USER','SAKHTYAR_RUNTIME_DB_PASSWORD','REDIS_PASSWORD',
  'MINIO_ROOT_USER','MINIO_ROOT_PASSWORD','APP_BOOTSTRAP_ADMIN_PASSWORD',
  'APP_JWT_SECRET','GRAFANA_ADMIN_PASSWORD','SAKHTYAR_DOMAIN','ACME_EMAIL')

def parse_env(p:Path)->dict[str,str]:
    if not p.is_file():raise PreflightFailure('.env missing')
    entries={}
    for line in p.read_text(encoding='utf-8-sig').splitlines():
        line=line.strip()
        if not line or line.startswith('#'):continue
        if not re.fullmatch(r'[A-Za-z_][A-Za-z_0-9]*\s*=.*',line):raise PreflightFailure('Invalid .env syntax')
        key,value=line.split('=',1);key=key.strip();value=value.strip()
        if key in entries:raise PreflightFailure('Duplicate .env key: '+key)
        if (value.startswith('"') and value.endswith('"')) or (value.startswith("'") and value.endswith("'")):value=value[1:-1]
        entries[key]=value
    return entries

def validate(entries:dict[str,str])->dict:
    missing=[k for k in FIELDS if not entries.get(k)]
    if missing:raise PreflightFailure('Missing required .env keys: '+', '.join(missing))
    for k in FIELDS:
        v=entries[k]
        if re.search(r'(?i)(CHANGE.?ME|PLACEHOLDER|EXAMPLE|1234567890|localhost|sample)',v):
            raise PreflightFailure('Placeholder value rejected for key: '+k)
    if entries['POSTGRES_USER']==entries['SAKHTYAR_RUNTIME_DB_USER']:
        raise PreflightFailure('App runtime role equals PostgreSQL bootstrap/owner role')
    if entries['POSTGRES_PASSWORD']==entries['SAKHTYAR_RUNTIME_DB_PASSWORD']:
        raise PreflightFailure('App runtime password equals PostgreSQL bootstrap secret')
    for k in ('POSTGRES_PASSWORD','SAKHTYAR_RUNTIME_DB_PASSWORD','APP_BOOTSTRAP_ADMIN_PASSWORD','MINIO_ROOT_PASSWORD','APP_JWT_SECRET','REDIS_PASSWORD','GRAFANA_ADMIN_PASSWORD'):
        if len(entries[k])<24:raise PreflightFailure('Credential too short: '+k)
    if len(entries['APP_JWT_SECRET'])<48:raise PreflightFailure('APP_JWT_SECRET shorter than 48 characters')
    if entries['SAKHTYAR_RUNTIME_DB_USER'].lower() in ('postgres','root','sakhtyar','admin'):
        raise PreflightFailure('Forbidden runtime DB username')
    if entries['SAKHTYAR_DOMAIN'].endswith(('.invalid','.example','.test','.localhost')):
        raise PreflightFailure('Non-production domain')
    if not re.fullmatch(r'[a-z][a-z0-9_-]{2,62}',entries['SAKHTYAR_RUNTIME_DB_USER']):
        raise PreflightFailure('Invalid runtime database username')
    return {'status':'ENV_SOURCE_PREFLIGHT_PASSED','secrets_printed':False,
            'database_principal_verified':False,'database_changed':False,
            'deploy_performed':False}

def main():
    p=argparse.ArgumentParser();p.add_argument('--env-file',type=Path,required=True);a=p.parse_args()
    try:r=validate(parse_env(a.env_file));code=0
    except (OSError,PreflightFailure) as ex:r={'status':'BLOCKED','reason':str(ex),'secrets_printed':False};code=2
    print(json.dumps(r,indent=2));return code
if __name__=='__main__':raise SystemExit(main())
