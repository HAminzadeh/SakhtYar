#!/usr/bin/env python3
"""Offline read-only inventory; NEVER prints tokens, credentials or matched source text."""
import argparse
import json
import pathlib
import re
import subprocess
from collections import Counter

CHECKS = {
    'DEVELOPMENT_SECRET_DEFAULT': re.compile(
        r'\$\{(?:APP_JWT_SECRET|APP_BOOTSTRAP_ADMIN_PASSWORD|POSTGRES_PASSWORD|MINIO_SECRET_KEY):[^}\n]+\}', re.I),
    'DEBUG_PRINCIPAL_LOG_PATTERN': re.compile(r'(?i)log\.(?:info|debug|warn|error)\s*\([^\n]*principal='),
    'POTENTIAL_UNBOUNDED_SQL_STRING': re.compile(r'(?i)(?:jdbc|template|entityManager)\s*\.\s*'
                                                   r'(?:execute|query|queryForList|createNativeQuery)\s*\(\s*"[^"\n]*"\s*\+'),
    'SENSITIVE_QUERY_LOG_PATTERN': re.compile(r'(?i)log\.(?:info|debug|warn|error)\s*\([^\n]*'
                                               r'(?:Authorization|requestURI|refreshToken|accessToken)'),
}
TRACKED_EXT = {'.java', '.py', '.sql', '.properties', '.yml', '.yaml', '.json', '.ts', '.tsx', '.js', '.mjs', '.env'}
SKIP_DIRS = {'node_modules', 'target', 'dist', 'build', '.git', '.venv'}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--root', required=True)
    args = parser.parse_args()
    root = pathlib.Path(args.root).resolve()
    tracked = subprocess.run(['git', 'ls-files', '-z'], cwd=root, stdout=subprocess.PIPE,
                             stderr=subprocess.PIPE, check=True).stdout.split(b'\0')
    findings = []
    scanned = 0
    for item in tracked:
        if not item:
            continue
        relative = item.decode('utf-8', errors='replace')
        p = pathlib.PurePosixPath(relative)
        if any(segment in SKIP_DIRS for segment in p.parts) or p.suffix.lower() not in TRACKED_EXT:
            continue
        source = root.joinpath(*p.parts)
        if source.is_symlink() or not source.is_file() or source.stat().st_size > 512 * 1024:
            continue
        scanned += 1
        lines = source.read_text(encoding='utf-8', errors='replace').splitlines()
        for number, line in enumerate(lines, 1):
            for code, check in CHECKS.items():
                if check.search(line):
                    findings.append({'path': relative, 'line': number, 'category': code})
    result = {'mode': 'OFFLINE_READ_ONLY_SOURCE_INVENTORY', 'scanned_files': scanned,
              'findings_count': len(findings), 'categories': dict(Counter(f['category'] for f in findings)),
              'findings': findings[:300], 'truncated': len(findings) > 300,
              'secret_values_emitted': False, 'dependency_CVE_verification': 'NOT_RUN_NO_NETWORK',
              'note': 'Heuristic inventory, not proof of vulnerabilities or complete absence of secrets.'}
    print(json.dumps(result, ensure_ascii=False, indent=2))

if __name__ == '__main__':
    main()
