from pathlib import Path
import argparse, json
from sakhtyar_persian.intake import DocumentIntakeEngine
from sakhtyar_persian.versioned_output import VersionedFileOutputStore

def main():
 p=argparse.ArgumentParser(); p.add_argument('--root',default='DocumentationOfLawsAndRegulations'); a=p.parse_args(); repo=Path(__file__).resolve().parents[2]; base=(repo/a.root).resolve(); eng=DocumentIntakeEngine(repo)
 files=sorted(base.rglob('*.pdf')); out=[]
 for i,f in enumerate(files,1):
  d=eng.inspect_pdf(f); out.append(d.__dict__); print(f'[{i}/{len(files)}] {d.status:22} pages={d.page_count:4} suspicious={len(d.suspicious_pages or []):4} {f.name}')
 report=repo/'.local/persian-intake/dry-run-v07.json'; report.write_text(json.dumps(out,ensure_ascii=False,indent=2),encoding='utf-8'); VersionedFileOutputStore(repo).write_json('INTAKE_DRY_RUN','documentation-laws',out,{'report':str(report)})
 from collections import Counter
 c=Counter(x['status'] for x in out); print('\nSUMMARY'); print('documents=',len(out)); [print(k,'=',v) for k,v in sorted(c.items())]; print('report=',report)
if __name__=='__main__':main()
