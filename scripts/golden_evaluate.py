#!/usr/bin/env python3
"""Evaluate expert-verified ground truth only. No invented facts or synthetic gold labels."""
import argparse,json,sys
from pathlib import Path

def evaluate(dataset,predictions=None):
    cases=dataset.get('cases',[])
    ids=[c.get('id') for c in cases]
    if not cases or len(ids)!=len(set(ids)) or any(not c.get('question') for c in cases):
        raise ValueError('Nonempty cases with unique IDs and questions required')
    verified=[c for c in cases if c.get('expertVerified') is True]
    draft=len(cases)-len(verified)
    if predictions is None:
        return {'caseCount':len(cases),'expertVerified':len(verified),'draftQuestions':draft,'status':'DRAFT_NOT_SCORED' if not verified else 'READY_TO_SCORE'}
    if not verified:raise ValueError('No expert-verified Golden Dataset items; scoring forbidden')
    outputs={p.get('id'):p for p in predictions.get('predictions',[])}
    if len(outputs)!=len(predictions.get('predictions',[])):raise ValueError('Duplicate prediction IDs')
    tp=fp=fn=correct_abstentions=required_abstentions=0
    details=[]
    for c in verified:
        pred=outputs.get(c['id'],{})
        truth=c.get('expected')
        if not isinstance(truth,dict) or not c.get('expertName') or not c.get('sourceSha256') or not isinstance(c.get('sourcePage'),int):
            raise ValueError(f"Missing independent expert annotation, document hash, and source page: {c['id']}")
        should_abstain=bool(truth.get('mustAbstain'))
        abstained=bool(pred.get('abstained',False))
        valid_citation=bool(pred.get('sourceSha256')==c['sourceSha256'] and pred.get('sourcePage')==c['sourcePage'])
        correct_value=pred.get('value')==truth.get('value') and pred.get('operator')==truth.get('operator') and pred.get('unit')==truth.get('unit')
        match=should_abstain and abstained or (not should_abstain and not abstained and correct_value and valid_citation)
        if should_abstain:
            required_abstentions+=1
            if abstained:correct_abstentions+=1
            else:fp+=1
        elif match:tp+=1
        elif abstained:fn+=1
        else:fp+=1;fn+=1
        details.append({'id':c['id'],'correct':bool(match),'citationValid':valid_citation,'abstained':abstained})
    precision=tp/(tp+fp) if tp+fp else None
    recall=tp/(tp+fn) if tp+fn else None
    return {'status':'SCORED_EXPERT_VERIFIED_ONLY','scored':len(verified),'precision':precision,'recall':recall,'abstentionAccuracy':correct_abstentions/required_abstentions if required_abstentions else None,'truePositive':tp,'falsePositive':fp,'falseNegative':fn,'details':details}

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--dataset',required=True);p.add_argument('--predictions');p.add_argument('--validate-only',action='store_true');args=p.parse_args()
    d=json.loads(Path(args.dataset).read_text(encoding='utf-8'))
    try:r=evaluate(d,None if args.validate_only or not args.predictions else json.loads(Path(args.predictions).read_text(encoding='utf-8')))
    except (ValueError,KeyError) as e:print('FAIL:',e,file=sys.stderr);sys.exit(2)
    print(json.dumps(r,ensure_ascii=False,indent=2))
