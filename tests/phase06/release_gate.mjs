#!/usr/bin/env node
/** Strict offline acceptance validator. This is NOT a business workflow executor. */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
const ids=Array.from({length:24},(_,i)=>`E2E-${String(i+1).padStart(2,'0')}`)
function parseEvidence(value){
  if(value && typeof value==='object' && value.type==='jsonb' && typeof value.value==='string'){
    try{return JSON.parse(value.value)}catch{return {}}
  }
  return value && typeof value==='object'?value:{}
}
export function validate(report){
  const reasons=[]
  if(!report || report.state!=='COMPLETED')reasons.push('Run has not COMPLETED')
  if(!/^[0-9a-f]{40}$/i.test(report?.source_commit||''))reasons.push('Missing resolved source SHA')
  const rows=report?.results
  if(!Array.isArray(rows)||rows.length!==24)reasons.push('Expected exactly 24 scenario results')
  const seen=new Set()
  for(const row of Array.isArray(rows)?rows:[]){
    if(!ids.includes(row.scenario_id)||seen.has(row.scenario_id))reasons.push('Unknown or duplicate scenario ID')
    seen.add(row.scenario_id)
    if(row.state!=='PASSED'){
      reasons.push(`${row.scenario_id}: ${row.state} (not PASSED)`)
      continue
    }
    const ev=parseEvidence(row.evidence)
    const a=ev.assertions||{}
    for(const key of ['businessWorkflowExecuted','positiveHttpAssertion','negativeHttpAssertion','dbAssertion','browserAssertion']){
      if(ev[key]!==true && a[key]!==true)reasons.push(`${row.scenario_id}: missing ${key}`)
    }
    // A list of signed, immutable/hashed evidence artifacts should be created by actual runners.
    // A forged JSON object can still bypass this structural validator: CI must independently
    // inspect HTTP/DB/browser artifacts and verify TEST scope with a restricted principal.
    for(const key of ['httpPositive','httpNegative','database','browser']){
      const proof=ev.proofArtifacts?.[key]
      if(!proof || typeof proof.path!=='string'||!proof.path.startsWith('evidence/') ||
         !/^[0-9a-f]{64}$/i.test(proof.sha256||''))reasons.push(`${row.scenario_id}: missing hashed ${key} evidence reference`)
    }
  }
  for(const id of ids)if(!seen.has(id))reasons.push(`Missing scenario ${id}`)
  const pass=Array.isArray(rows)?rows.filter(x=>x.state==='PASSED').length:0
  if(report?.businessScenarioPassedCount!==24 || pass!==24)reasons.push('Business pass count must be 24/24')
  return {accepted:reasons.length===0,passed:pass,total:24,reasons,sourceCommit:report?.source_commit||'unknown',runId:report?.id||null}
}
function escapeHtml(s){return String(s).replace(/[&<>"']/g,x=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[x]))}
if(process.argv[1]&&path.resolve(process.argv[1])===fileURLToPath(import.meta.url)){
  const reportPath=process.argv[2];const inventory=process.argv.includes('--inventory')
  if(!reportPath || !fs.existsSync(reportPath)){
    process.stderr.write('BLOCKED: report JSON is required. No release accepted.\n');process.exitCode=2
  }else{
    try{
      const summary=validate(JSON.parse(fs.readFileSync(reportPath,'utf8')))
      const output=path.resolve(process.env.SAKHTYAR_PHASE06_REPORT_DIR||'phase06-report')
      fs.mkdirSync(output,{recursive:true})
      fs.writeFileSync(path.join(output,'release-summary.json'),JSON.stringify({...summary,mode:inventory?'INVENTORY_ONLY':'STRICT'},null,2))
      const html=`<!doctype html><html lang="fa" dir="rtl"><meta charset="utf-8"><title>SakhtYar Phase 06 Gate</title><body><h1>${summary.accepted?'ساختار شواهد تأیید شد':'انتشار مسدود است'}</h1><p>سناریوهای PASSED: ${summary.passed} / ${summary.total}</p><p>این گزارش تنها اعتبارسنجی ساختاری است و به‌تنهایی اجرای واقعی را اثبات نمی‌کند.</p><pre>${escapeHtml(summary.reasons.join('\n'))}</pre></body></html>`
      fs.writeFileSync(path.join(output,'release-summary.html'),html)
      process.stdout.write(`STRUCTURAL_RELEASE_GATE=${summary.accepted?'PASS':'BLOCKED'} PASSED=${summary.passed}/24 MODE=${inventory?'INVENTORY_ONLY':'STRICT'}\n`)
      for(const x of summary.reasons.slice(0,14))process.stdout.write(`- ${x}\n`)
      if(!inventory && !summary.accepted)process.exitCode=2
    }catch(e){process.stderr.write('BLOCKED: invalid report JSON: '+e.name+'\n');process.exitCode=2}
  }
}
