import {Alert,App as AntdApp,Button,Card,Col,Input,Progress,Row,Space,Statistic,Steps,Table,Tag,Typography} from 'antd'
import {DatabaseOutlined,PauseCircleOutlined,PlayCircleOutlined,ReloadOutlined,RedoOutlined} from '@ant-design/icons'
import {useMutation,useQuery} from '@tanstack/react-query'
import {useEffect,useMemo,useState} from 'react'
import type {Key} from 'react'
import {api} from '../api/client'
type CP={stage_code:string;status:string;attempt:number;processed_count:number;failed_count:number;skipped_count:number;error_message?:string}
type Doc={relative_path:string;sha256?:string;size_bytes:number;page_count:number;native_chars:number;intake_status:string;reason?:string;selected_by_user:boolean}
type State={id:string;run_id:string;status:string;current_stage:string;total_documents:number;completed_documents:number;failed_documents:number;source_root:string;error_message?:string;checkpoints:CP[];inventory:Doc[]}
type Recent={id:string;run_id:string;status:string;current_stage:string;total_documents:number;source_root:string}
const stages=['DISCOVER_SOURCES','HASH_AND_DEDUP','CLASSIFY_DOCUMENTS','USER_SELECTION','NATIVE_EXTRACTION','PAGE_QUALITY_GATE','OCR_REQUIRED_PAGES','PERSIAN_NORMALIZATION','KNOWLEDGE_EXTRACTION','QUALITY_GATE','DATASET_BUILD','PUBLISH']
const titles=['کشف اسناد','Hash و Dedup','طبقه‌بندی','انتخاب کاربر','استخراج Native','Page Gate','OCR','نرمال‌سازی فارسی','استخراج دانش','کنترل کیفیت','ساخت Dataset','انتشار']
export function KnowledgeAdminPage(){
 const {message}=AntdApp.useApp();const [source,setSource]=useState('DocumentationOfLawsAndRegulations');const [id,setId]=useState<string|null>(localStorage.getItem('knowledgeExecutionId'));const [selected,setSelected]=useState<Key[]>([])
 const recent=useQuery({queryKey:['kp-recent'],queryFn:()=>api<Recent[]>('/api/v1/knowledge/admin/pipeline/recent')})
 const state=useQuery({queryKey:['kp-state',id],enabled:!!id,queryFn:()=>api<State>(`/api/v1/knowledge/admin/pipeline/${id}`),refetchInterval:q=>['RUNNING','STOPPING'].includes((q.state.data as State|undefined)?.status??'')?1200:false})
 useEffect(()=>{if(state.data?.inventory)setSelected(state.data.inventory.filter(x=>x.selected_by_user).map(x=>x.relative_path))},[state.data?.inventory])
 const call=useMutation({mutationFn:(x:{url:string;body?:unknown})=>api<State>(x.url,{method:'POST',body:x.body?JSON.stringify(x.body):undefined}),onSuccess:r=>{setId(r.id);localStorage.setItem('knowledgeExecutionId',r.id);void state.refetch();void recent.refetch()},onError:e=>message.error(e instanceof Error?e.message:'عملیات ناموفق')})
 const start=()=>call.mutate({url:'/api/v1/knowledge/admin/pipeline/start',body:{sourceRoot:source}})
 const docs=state.data?.inventory??[];const selectable=useMemo(()=>docs.filter(d=>d.intake_status!=='SKIP_DUPLICATE_EXACT'),[docs])
 const current=Math.max(0,stages.indexOf(state.data?.current_stage??'DISCOVER_SOURCES'));const completed=state.data?.checkpoints?.filter(x=>x.status==='COMPLETED').length??0
 return <div className="sakhtyar-page-stack" dir="rtl">
  <Card><Space><DatabaseOutlined style={{fontSize:28}}/><div><Typography.Title level={2} style={{margin:0}}>دستیار پایدار آماده‌سازی دانش ساخت‌یار</Typography.Title><Typography.Text type="secondary">Checkpoint + Resume + Safe Stop؛ هر اجرای تکمیل‌شده دوباره پردازش نمی‌شود.</Typography.Text></div></Space></Card>
  {!id?<>
   <Card title="شروع اجرای جدید"><Space.Compact style={{width:'100%'}}><Input value={source} onChange={e=>setSource(e.target.value)} placeholder="مسیر پوشه منابع"/><Button type="primary" icon={<PlayCircleOutlined/>} loading={call.isPending} onClick={start}>شروع و ساخت خودکار لیست</Button></Space.Compact></Card>
   <Card title="اجراهای اخیر"><Table rowKey="id" dataSource={recent.data??[]} pagination={false} columns={[
    {title:'وضعیت',dataIndex:'status',render:v=><Tag>{v}</Tag>},{title:'مرحله',dataIndex:'current_stage'},{title:'اسناد',dataIndex:'total_documents'},{title:'منبع',dataIndex:'source_root',ellipsis:true},
    {title:'',render:(_,r:Recent)=><Button onClick={()=>{setId(r.id);localStorage.setItem('knowledgeExecutionId',r.id)}}>باز کردن / ادامه</Button>}
   ]}/></Card>
  </>:<>
   <Card><Space wrap><Button icon={<ReloadOutlined/>} onClick={()=>state.refetch()}>تازه‌سازی</Button><Button icon={<PauseCircleOutlined/>} disabled={!['RUNNING','STOPPING'].includes(state.data?.status??'')} onClick={()=>call.mutate({url:`/api/v1/knowledge/admin/pipeline/${id}/stop`})}>توقف امن</Button><Button type="primary" icon={<PlayCircleOutlined/>} disabled={!['PAUSED','FAILED'].includes(state.data?.status??'')} onClick={()=>call.mutate({url:`/api/v1/knowledge/admin/pipeline/${id}/resume`})}>ادامه</Button><Button icon={<RedoOutlined/>} disabled={state.data?.status!=='FAILED'} onClick={()=>call.mutate({url:`/api/v1/knowledge/admin/pipeline/${id}/retry`})}>Retry خطا</Button><Button onClick={()=>{setId(null);localStorage.removeItem('knowledgeExecutionId')}}>اجرای دیگر</Button><Tag color="blue">{state.data?.status}</Tag><Typography.Text code>{id}</Typography.Text></Space></Card>
   <Card><Steps current={current} responsive items={titles.map((title,i)=>({title,status:state.data?.checkpoints?.[i]?.status==='FAILED'?'error':state.data?.checkpoints?.[i]?.status==='COMPLETED'?'finish':i===current?'process':'wait'}))}/></Card>
   <Row gutter={[12,12]}><Col xs={12} md={6}><Card><Statistic title="کل اسناد" value={state.data?.total_documents??0}/></Card></Col><Col xs={12} md={6}><Card><Statistic title="Checkpoint تکمیل" value={completed} suffix={`/ ${stages.length}`}/></Card></Col><Col xs={12} md={6}><Card><Statistic title="مرحله فعلی" value={titles[current]}/></Card></Col><Col xs={12} md={6}><Card><Statistic title="وضعیت" value={state.data?.status??'—'}/></Card></Col></Row>
   <Card title="Checkpointها"><Table rowKey="stage_code" size="small" pagination={false} dataSource={state.data?.checkpoints??[]} columns={[
    {title:'مرحله',dataIndex:'stage_code'},{title:'وضعیت',dataIndex:'status',render:v=><Tag color={v==='COMPLETED'?'green':v==='FAILED'?'red':v==='RUNNING'?'blue':'default'}>{v}</Tag>},{title:'Attempt',dataIndex:'attempt'},{title:'پردازش',dataIndex:'processed_count'},{title:'Skip',dataIndex:'skipped_count'},{title:'خطا',dataIndex:'error_message',ellipsis:true}
   ]}/></Card>
   {state.data?.status==='WAITING_FOR_USER'&&<Card title="انتخاب اسناد"><Alert showIcon type="info" message="Pipeline در Checkpoint انتخاب کاربر متوقف شده است. Duplicateهای دقیق را انتخاب نکنید؛ پس از تأیید، ادامه مراحل از همین Run انجام می‌شود."/><Table rowKey="relative_path" size="small" dataSource={docs} pagination={{pageSize:20}} rowSelection={{selectedRowKeys:selected,onChange:setSelected,getCheckboxProps:r=>({disabled:r.intake_status==='SKIP_DUPLICATE_EXACT'})}} columns={[
    {title:'فایل',dataIndex:'relative_path',ellipsis:true},{title:'وضعیت',dataIndex:'intake_status',render:v=><Tag>{v}</Tag>},{title:'صفحات',dataIndex:'page_count'},{title:'حجم',dataIndex:'size_bytes',render:v=>`${(Number(v)/1024/1024).toFixed(1)} MB`},{title:'علت',dataIndex:'reason',ellipsis:true}
   ]}/><Button type="primary" disabled={!selected.length} loading={call.isPending} onClick={()=>call.mutate({url:`/api/v1/knowledge/admin/pipeline/${id}/selection`,body:{relativePaths:selected}})}>تأیید {selected.length} سند و ادامه</Button></Card>}
   {state.data?.error_message&&<Alert type="error" showIcon message="Execution متوقف شده" description={state.data.error_message}/>}
   <Card><Progress percent={Math.round(completed/stages.length*100)} status={state.data?.status==='FAILED'?'exception':state.data?.status==='COMPLETED'?'success':'active'}/></Card>
  </>}
 </div>
}