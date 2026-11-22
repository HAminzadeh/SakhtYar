#!/usr/bin/env python3
"""Independent Python Decimal oracle for Phase05; no Java production-method invocation.

Usage: python tests/phase05/verify_phase05_oracle.py
This test NEVER reads credentials, starts services, or writes to a database.
"""
import json
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path

GOLDEN = Path(__file__).parent / 'golden' / 'synthetic_business_finance_oracle_v2.json'
D = Decimal

def round_to(value, places=2):
    return value.quantize(D('1').scaleb(-places), rounding=ROUND_HALF_UP)

def compute(g):
    b = g['baseline']
    money = lambda k: D(b[k])
    revenue = round_to(money('sellableAreaM2') * money('expectedSalePricePerM2'))
    cost = round_to(sum((money(k) for k in ('baseConstructionCost','additionalCost','financingCost','taxesAndFees')), D('0')))
    profit = round_to(revenue-cost)
    roi = round_to(profit * 100 / cost, 6) if cost else None
    margin = round_to(profit * 100 / revenue, 6) if revenue else None
    participants = []
    spent_revenue = D('0')
    spent_cost = D('0')
    people = sorted(g['finance']['participants'], key=lambda p: (D(p['valueSharePercent']), D(p['costSharePercent']), p['key'].strip()))
    for index,p in enumerate(people):
        last = index == len(people)-1
        alloc_revenue = (revenue - spent_revenue) if last else min(round_to(revenue * D(p['valueSharePercent'])/100),revenue-spent_revenue)
        alloc_cost = (cost - spent_cost) if last else min(round_to(cost * D(p['costSharePercent'])/100),cost-spent_cost)
        spent_revenue += alloc_revenue
        spent_cost += alloc_cost
        participants.append((p['key'],alloc_revenue,alloc_cost,round_to(alloc_revenue-alloc_cost)))
    return revenue,cost,profit,roi,margin,participants

def verify(g):
    assert g['origin'] == 'SYNTHETIC_NUMERIC_GOLDEN_NOT_LEGAL_OR_MARKET_DATA'
    assert g['schemaVersion'] == 2
    assert g['versionPin']['createdAt'] == '2026-01-01T00:00:00Z'
    assert g['fixtures']['dbWritesAllowed'] is False
    assert g['baseline']['currency'] == 'IRR'
    assert g['rounding']['mode'] == 'HALF_UP'
    a = compute(g)
    f = g['finance']
    for v, key in zip(a[:5],('grossRevenue','totalProjectCost','projectedProfit','roiPercent','profitMarginPercent')):
        assert v == D(f[key]), (key, str(v), f[key])
    assert sum((D(p['valueSharePercent']) for p in f['participants']),D('0')) == 100
    assert sum((D(p['costSharePercent']) for p in f['participants']),D('0')) == 100
    for name,rev,cost,net in a[5]:
        row = next(p for p in f['participants'] if p['key'] == name)
        for v,k in ((rev,'allocatedRevenue'),(cost,'allocatedCost'),(net,'projectedNetValue')):
            assert v == D(row[k]), (name,k)
    assert sum((row[1] for row in a[5]),D('0')) == a[0]
    assert sum((row[2] for row in a[5]),D('0')) == a[1]
    assert sum((row[3] for row in a[5]),D('0')) == a[2]
    s = g['sensitivity']
    shocked_revenue = round_to(D(g['baseline']['sellableAreaM2']) * (D('1')+D(s['sellableAreaChangePercent'])/100) *
                                round_to(D(g['baseline']['expectedSalePricePerM2'])*(D('1')+D(s['salePriceChangePercent'])/100)))
    shocked_cost = round_to(round_to(D(g['baseline']['baseConstructionCost'])*(D('1')+D(s['constructionCostChangePercent'])/100))+
                            sum((D(g['baseline'][x]) for x in ('additionalCost','financingCost','taxesAndFees')),D('0')))
    assert shocked_revenue == D(s['grossRevenue'])
    assert shocked_cost == D(s['totalProjectCost'])
    assert shocked_revenue-shocked_cost == D(s['projectedProfit'])
    assembly = g['assembly']
    effective = round_to(D(assembly['componentQuantity'])*(1+D(assembly['wasteFactor']))*D(assembly['requestedQuantity']),6)
    assert effective == D(assembly['effectiveQuantity'])
    assert round_to(effective*D(assembly['unitPrice'])) == D(assembly['lineTotal'])
    urban = g['urban']
    assert round_to(D(urban['totalBuiltAreaM2'])/D(urban['landAreaM2']),6) == D(urban['expectedFAR'])
    assert D(urban['expectedFAR']) <= D(urban['maxFAR'])

if __name__=='__main__':
    import copy
    data=json.loads(GOLDEN.read_text(encoding='utf-8'))
    verify(data)
    altered=copy.deepcopy(data)
    altered['finance']['projectedProfit']='2000000000.01'
    try:
        verify(altered)
        raise AssertionError('Mutation of Golden profit was not detected')
    except AssertionError as e:
        if str(e)=='Mutation of Golden profit was not detected': raise
    print('PASS Phase05 independent Decimal Oracle: finance/60-40/conservation/ROI/margin/sensitivity/waste/FAR; mutation rejected')
