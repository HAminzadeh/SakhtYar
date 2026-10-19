import {useEffect, useMemo, useRef, useState} from 'react'
import {Alert, Button, Divider, Progress, Space, Table, Tag, Typography} from 'antd'
import {FolderOpenOutlined, PlayCircleOutlined, StopOutlined} from '@ant-design/icons'
import {api} from '../api/client'

const ROOT = '/api/v1/knowledge/admin/ai-import'
type UploadResult = {id:string; status:string; documents:number; rules:number; segments:number}
type State = {total:number; scanned:number; uploaded:number; skipped:number; failed:number; current:string}
type Failure = {path:string; reason:string}

function relativeName(file: File) {
  return file.webkitRelativePath || file.name
}
async function fingerprint(file: File): Promise<string | null> {
  if (typeof crypto === 'undefined' || !crypto.subtle) return null
  const digest = await crypto.subtle.digest('SHA-256', await file.arrayBuffer())
  return Array.from(new Uint8Array(digest), b=>b.toString(16).padStart(2,'0')).join('')
}

export function KnowledgeArchiveFolderImport({onImported}: {onImported: (batchId:string)=>void}) {
  const input = useRef<HTMLInputElement>(null)
  const stop = useRef(false)
  const [archives,setArchives] = useState<File[]>([])
  const [running,setRunning] = useState(false)
  const [state,setState] = useState<State>({total:0,scanned:0,uploaded:0,skipped:0,failed:0,current:''})
  const [failures,setFailures] = useState<Failure[]>([])
  const [finished,setFinished] = useState(false)
  // SAKHTYAR_V533_CSV: local report with exact file and failure explanation.
  const downloadFailureReport=()=>{
    const escape=(value:string)=>'"'+value.replace(/"/g,'""')+'"'
    const csv=['file,reason,suggestion',...failures.map(f=>[
      escape(f.path),escape(f.reason),
      escape(f.reason.includes('Evidence quote')?'Repair evidence_quote to be an exact substring of full_text':
      f.reason.includes('LAW documents')?'Keep educational content as EDUCATION/TECHNICAL and rules.jsonl empty':
      'Review manifest and JSONL records; re-upload a corrected package')].join(','))].join('\r\n')
    const blob=new Blob(['\uFEFF'+csv],{type:'text/csv;charset=utf-8'})
    const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download='sakhtyar-import-errors.csv';a.click();URL.revokeObjectURL(url)
  }

  const fileRows = useMemo(()=>archives.slice(0,30).map((f,i)=>({key:i,path:relativeName(f),size:f.size})),[archives])

  useEffect(()=>{
    // Supported by Chromium browsers (Chrome/Edge). The browser only shares selected files, not its filesystem path.
    input.current?.setAttribute('webkitdirectory','')
    input.current?.setAttribute('directory','')
  },[])

  const selectFolder = () => {
    if(running) return
    if(input.current) {input.current.value='';input.current.click()}
  }
  const run = async () => {
    if(running || archives.length===0) return
    stop.current=false;setRunning(true);setFinished(false);setFailures([])
    const progress:State={total:archives.length,scanned:0,uploaded:0,skipped:0,failed:0,current:''}
    const problems:Failure[]=[]
    const hashes = new Map<File,string|null>()
    const locallySeen=new Set<string>()
    const existing=new Set<string>()
    const candidates:File[]=[]
    const tick=()=>setState({...progress})
    try {
      // Hash each selected archive individually, never expand it in the browser.
      for (const file of archives) {
        if(stop.current) break
        progress.current=`بررسی: ${relativeName(file)}`
        if(file.size===0 || file.size>24*1024*1024) {
          problems.push({path:relativeName(file),reason:'حجم صفر یا بیش از ۲۴ مگابایت'})
          progress.failed++;progress.scanned++;tick();continue
        }
        try {
          const sha=await fingerprint(file)
          hashes.set(file,sha)
          if(sha && locallySeen.has(sha)) progress.skipped++
          else {if(sha)locallySeen.add(sha);candidates.push(file)}
        } catch(err) {
          // If browser hashing fails, backend still protects against duplicates before archive extraction.
          hashes.set(file,null);candidates.push(file)
        }
        progress.scanned++;tick()
      }
      const uniqueHashes=candidates.map(f=>hashes.get(f)).filter((x):x is string=>!!x)
      for(let i=0;i<uniqueHashes.length && !stop.current;i+=200) {
        const response=await api<{hashes:string[]}>(`${ROOT}/packages/known-hashes`,{
          method:'POST',body:JSON.stringify({hashes:uniqueHashes.slice(i,i+200)})
        })
        response.hashes.forEach(h=>existing.add(h))
      }
      for(const file of candidates) {
        if(stop.current) break
        progress.current=`بارگذاری: ${relativeName(file)}`;tick()
        const sha=hashes.get(file)
        if(sha && existing.has(sha)) {progress.skipped++;tick();continue}
        try {
          const body=new FormData();body.append('file',file,file.name)
          const result=await api<UploadResult>(`${ROOT}/packages`,{method:'POST',body})
          if(result.status==='SKIPPED_DUPLICATE') progress.skipped++
          else {progress.uploaded++;onImported(result.id)}
        } catch(err) {
          problems.push({path:relativeName(file),reason:err instanceof Error?err.message:String(err)})
          progress.failed++
        }
        tick()
      }
    } catch(err) {
      problems.push({path:'بررسی گروهی',reason:err instanceof Error?err.message:String(err)})
      progress.failed++
    } finally {
      progress.current=stop.current?'پردازش به درخواست شما متوقف شد.':'پردازش انتخاب‌های مجاز پایان یافت.'
      setFailures(problems);setState({...progress});setRunning(false);setFinished(true)
    }
  }
  return <div style={{marginTop:18}}>
    <Divider>بارگذاری گروهی پوشه و زیرپوشه‌ها</Divider>
    <Alert type="info" showIcon style={{marginBottom:12}}
      message="فقط آرشیوهای ZIP و RAR داخل پوشه انتخاب‌شده و تمامی زیرشاخه‌های آن بررسی می‌شوند."
      description="فایل‌ها با SHA-256 مقایسه می‌شوند؛ بسته‌های واردشده قبلی از دیتابیس تشخیص داده و رد می‌شوند. در صورت تغییر محتوای آرشیو، آن نسخه به‌عنوان ورودی جدید بررسی می‌شود. هر آرشیو باید چهار فایل JSON/JSONL استاندارد را داشته باشد."/>
    <input ref={input} type="file" multiple hidden onChange={e=>{
      const all=Array.from(e.target.files??[])
      const picked=all.filter(f=>/\.(zip|rar)$/i.test(f.name)).sort((a,b)=>relativeName(a).localeCompare(relativeName(b)))
      setArchives(picked);setFinished(false);setFailures([])
      setState({total:picked.length,scanned:0,uploaded:0,skipped:0,failed:0,current:''})
    }}/>
    <Space wrap>
      <Button icon={<FolderOpenOutlined/>} disabled={running} onClick={selectFolder}>انتخاب پوشه ریشه</Button>
      <Button type="primary" icon={<PlayCircleOutlined/>} loading={running} disabled={running || archives.length===0} onClick={()=>void run()}>
        بررسی و ورود {archives.length.toLocaleString('fa-IR')} آرشیو
      </Button>
      {running && <Button danger icon={<StopOutlined/>} onClick={()=>{stop.current=true}}>توقف پس از فایل جاری</Button>}
      <Tag>{archives.length.toLocaleString('fa-IR')} آرشیو انتخاب‌شده</Tag>
    </Space>
    {archives.length>0 && <>
      <Typography.Paragraph style={{marginTop:12}} type="secondary">
        پیش‌نمایش مسیرهای نسبی؛ نمایش {fileRows.length.toLocaleString('fa-IR')} مورد اول از {archives.length.toLocaleString('fa-IR')}
      </Typography.Paragraph>
      <Table size="small" pagination={false} dataSource={fileRows} scroll={{y:180}}
        columns={[{title:'مسیر در پوشه',dataIndex:'path',ellipsis:true},
          {title:'حجم',dataIndex:'size',width:125,render:v=>`${(Number(v)/1024/1024).toFixed(2)} MB`}]}/>
    </>}
    {(running||finished)&&<div style={{marginTop:12}}>
      <Progress percent={Math.round(100*(state.uploaded+state.skipped+state.failed)/Math.max(1,state.total))}
        status={state.failed>0?'exception':running?'active':'normal'}/>
      <Space wrap><Tag color="green">جدید: {state.uploaded}</Tag><Tag>تکراری: {state.skipped}</Tag>
        <Tag color={state.failed?'red':'default'}>ناموفق: {state.failed}</Tag><Tag>هش‌شده: {state.scanned}/{state.total}</Tag></Space>
      <Typography.Paragraph type="secondary">{state.current}</Typography.Paragraph>
    </div>}
    {failures.length>0 && <Button onClick={downloadFailureReport}>دریافت گزارش CSV خطاها</Button>}
    {failures.length>0 && <Alert type="warning" showIcon style={{marginTop:10}}
      message={`${failures.length} مورد نیازمند بررسی مجدد`}
      description={<div style={{maxHeight:180,overflow:'auto'}}>{failures.map((f,i)=><div key={i}><strong>{f.path}</strong>: {f.reason}</div>)}</div>}/>} 
  </div>
}
