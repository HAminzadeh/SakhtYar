#!/usr/bin/env python3
"""Read-only or explicitly authorized end-to-end Tehran Case API workflow.

Never fabricates municipal rules, case IDs, owner/builder IDs, currency prices or
finance assumptions. A write run requires --execute and all exact inputs.
"""
import argparse, json, os, sys, urllib.request, urllib.error, urllib.parse
from http.cookies import SimpleCookie
from pathlib import Path

class Api:
    def __init__(self,base):
        self.base=base.rstrip('/')
        self.cookie=os.getenv('SAKHTYAR_SESSION_COOKIE','')
        self.token=os.getenv('SAKHTYAR_BEARER_TOKEN','')
        if not (self.cookie or self.token):raise ValueError('Set SAKHTYAR_SESSION_COOKIE or SAKHTYAR_BEARER_TOKEN')
        self.csrf=None
    def req(self,path,payload=None):
        headers={'Accept':'application/json'}
        if self.cookie:headers['Cookie']=self.cookie
        if self.token:headers['Authorization']='Bearer '+self.token
        data=None
        if payload is not None:
            data=json.dumps(payload,ensure_ascii=False).encode('utf-8')
            headers['Content-Type']='application/json'
            if not self.csrf:self.prepare_csrf()
            if self.csrf:headers[self.csrf[0]]=self.csrf[1]
        req=urllib.request.Request(self.base+path,data=data,headers=headers,method='POST' if data is not None else 'GET')
        try:
            with urllib.request.urlopen(req,timeout=30) as r:return json.load(r)
        except urllib.error.HTTPError as e:
            raise ValueError(f'API {e.code} {path}: {e.read(300).decode("utf-8","replace")}') from e
    def prepare_csrf(self):
        headers={'Accept':'application/json'}
        if self.cookie:headers['Cookie']=self.cookie
        if self.token:headers['Authorization']='Bearer '+self.token
        req=urllib.request.Request(self.base+'/api/v1/auth/csrf',headers=headers)
        with urllib.request.urlopen(req,timeout=15) as r:
            body=json.load(r);cookies=r.headers.get_all('Set-Cookie',[])
        for line in cookies:
            c=SimpleCookie()
            try:c.load(line)
            except Exception:continue
            if 'XSRF-TOKEN' in c:
                tok=urllib.parse.unquote(c['XSRF-TOKEN'].value)
                self.csrf=(body.get('headerName','X-XSRF-TOKEN'),tok)
                self.cookie=(self.cookie+'; ' if self.cookie else '')+'XSRF-TOKEN='+urllib.parse.quote(tok)
                break
        if not self.csrf:raise ValueError('CSRF cookie unavailable. Use a valid browser cookie session.')

def run(args):
    api=Api(args.base)
    case_id=args.case_id
    report={'caseId':case_id,'readOnly':not args.execute,'stages':[]}
    prop=api.req(f'/api/v1/cases/{case_id}/property')
    if not prop or not prop.get('id'):raise ValueError('Property for case is missing')
    if prop.get('city') not in {'تهران','Tehran'}:raise ValueError('Only existing Tehran cases are allowed in this exercise')
    report['stages'].append({'stage':'property','status':'FOUND','propertyId':prop['id'],'city':prop['city'],'landAreaM2':prop.get('landAreaM2')})
    evidence=api.req(f'/api/v1/regulations/cases/{case_id}/verified-evidence')
    report['stages'].append({'stage':'verified_regulation_evidence','status':'FOUND' if evidence else 'INSUFFICIENT_DATA','count':len(evidence)})
    if args.execute:
        missing=[p for p in ('scenario_id','cost_snapshot_id','sellable_area_m2','sale_price_per_m2','owner_id','builder_id') if getattr(args,p,None) in (None,'')]
        if missing:raise ValueError('Explicit --execute inputs missing: '+', '.join(missing))
        if not evidence:report['status']='INSUFFICIENT_DATA';return report
        urban=api.req('/api/v1/regulations/evaluate',{'propertyId':prop['id'],'scenarioId':args.scenario_id})
        report['stages'].append({'stage':'urban_evaluation','status':urban['status'],'ruleCount':urban.get('ruleCount'),'id':urban.get('id')})
        if urban['status']!='COMPLIANT' or urban.get('ruleCount',0)<1:
            report['status']='INSUFFICIENT_DATA';return report
        feasibility=api.req('/api/v1/feasibility/assessments',{'caseId':case_id,'scenarioId':args.scenario_id,'scenarioCostSnapshotId':args.cost_snapshot_id,'urbanEvaluationId':urban['id']})
        report['stages'].append({'stage':'feasibility','status':feasibility['status'],'id':feasibility.get('id')})
        if feasibility['status']!='FEASIBLE':report['status']='INSUFFICIENT_DATA';return report
        if args.owner_percent<0 or args.owner_percent>100:raise ValueError('Owner share must be 0..100')
        financial=api.req('/api/v1/finance/analyses',{'feasibilityAssessmentId':feasibility['id'],
            'sellableAreaM2':args.sellable_area_m2,'expectedSalePricePerM2':args.sale_price_per_m2,
            'currencyCode':args.currency,'otherRevenue':0,'additionalCost':args.additional_cost,
            'financingCost':args.financing_cost,'taxesAndFees':args.taxes_and_fees,
            'participants':[{'participantType':'OWNER','participantRefId':args.owner_id,'participantLabel':'مالک','valueSharePercent':args.owner_percent,'costSharePercent':0,'metadata':{'source':'EXPLICIT_EXPERT_INPUT'}},
                            {'participantType':'BUILDER','participantRefId':args.builder_id,'participantLabel':'سازنده','valueSharePercent':100-args.owner_percent,'costSharePercent':100,'metadata':{'source':'EXPLICIT_EXPERT_INPUT'}}]})
        report['stages'].append({'stage':'financial','status':'SCENARIO_COMPUTED_NOT_A_CONTRACT','id':financial.get('id'),'currencyCode':financial.get('currencyCode'),'roiPercent':financial.get('roiPercent')})
        report['status']='READY_FOR_EXPERT_REVIEW'
        return report
    evaluations=api.req(f'/api/v1/regulations/properties/{prop["id"]}/evaluations')
    compliant=[e for e in evaluations if e.get('status')=='COMPLIANT' and (e.get('ruleCount') or 0)>0]
    report['stages'].append({'stage':'urban_evaluation','status':'FOUND' if compliant else 'INSUFFICIENT_DATA','count':len(compliant)})
    assessments=api.req(f'/api/v1/feasibility/cases/{case_id}/assessments')
    feasible=[a for a in assessments if a.get('status')=='FEASIBLE' and any(e.get('id')==a.get('urbanEvaluationId') for e in compliant)]
    report['stages'].append({'stage':'feasibility','status':'FOUND' if feasible else 'INSUFFICIENT_DATA','count':len(feasible)})
    finance=api.req(f'/api/v1/finance/cases/{case_id}/analyses')
    usable=[f for f in finance if any(a.get('id')==f.get('feasibilityAssessmentId') for a in feasible)]
    report['stages'].append({'stage':'financial_scenarios','status':'FOUND' if usable else 'INSUFFICIENT_DATA','count':len(usable)})
    report['status']='READY_FOR_EXPERT_REVIEW' if evidence and compliant and feasible and usable else 'INSUFFICIENT_DATA'
    return report

if __name__=='__main__':
    ap=argparse.ArgumentParser(description='Fail-closed Tehran integration check. Writes ONLY with --execute.')
    ap.add_argument('--base',default='http://localhost:8080');ap.add_argument('--case-id',required=True)
    ap.add_argument('--report',default='.local/reports/phase515-tehran.json');ap.add_argument('--execute',action='store_true')
    ap.add_argument('--scenario-id');ap.add_argument('--cost-snapshot-id');ap.add_argument('--sellable-area-m2',type=float)
    ap.add_argument('--sale-price-per-m2',type=float);ap.add_argument('--owner-id');ap.add_argument('--builder-id')
    ap.add_argument('--owner-percent',type=float,default=50);ap.add_argument('--additional-cost',type=float,default=0)
    ap.add_argument('--financing-cost',type=float,default=0);ap.add_argument('--taxes-and-fees',type=float,default=0)
    ap.add_argument('--currency',default='IRR');args=ap.parse_args()
    try:
        if args.execute and (args.sellable_area_m2 is not None and args.sellable_area_m2<=0 or args.sale_price_per_m2 is not None and args.sale_price_per_m2<0):
            raise ValueError('Area must be positive and sale price nonnegative')
        result=run(args)
    except (ValueError,urllib.error.URLError,KeyError) as e:print('E2E BLOCKED:',e,file=sys.stderr);sys.exit(2)
    p=Path(args.report);p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps(result,ensure_ascii=False,indent=2))
    sys.exit(0 if result['status']=='READY_FOR_EXPERT_REVIEW' else 3)
