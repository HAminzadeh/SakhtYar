import {useEffect,useMemo,useState} from 'react'
import {Alert,App,Button,Card,Checkbox,Col,Descriptions,Input,Modal,Row,Select,Space,Statistic,Table,Tag,Typography} from 'antd'
import {CheckCircleOutlined,CloseCircleOutlined,QuestionCircleOutlined,ReloadOutlined,RollbackOutlined} from '@ant-design/icons'
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query'
import {api} from '../api/client'

const ROOT='/api/v1/knowledge/admin/ai-workspace'
const sizes=['10','20','50','100','200','500','1000']
export type WorkspaceMode='rules'|'education'|'categories'|'segments'|'review'
type Package={id:string;archive_name:string;source_mode?:string;documents:number;law_documents:number;education_documents:number;rules:number;legal_segments:number;educational_segments:number}
type Listing<T>={items:T[];total:number;page:number;size:number}
type RowItem={id:string;batch_id:string;document_id:string;archive_name:string;document_title:string;category:string;item_key:string;record_type:string;segment_type:string;article_label?:string;full_text:string;evidence_quote:string;page_from:number;page_to:number;source_reference:string;review_status:string}
type Document={id:string;title:string;category:string;archive_name:string;source_reference:string;authority?:string;edition_label?:string;item_count:number}
const cat:{[name:string]:string}={LAW:'قانون و مقررات',EDUCATION:'آموزشی',TECHNICAL:'فنی و مهندسی',REFERENCE:'مرجع',OTHER:'سایر',UNCLASSIFIED:'نیازمند تعیین نوع'}
const statusNames:{[name:string]:string}={PENDING_REVIEW:'در انتظار',APPROVED:'پذیرفته برای مرحله بعد',REJECTED:'ردشده'}
const help:{[key in WorkspaceMode]:string}={
 rules:'قوانین پیشنهادی همه یا چند بسته را ببین. متن و منبع را قبل از پذیرش بررسی کن؛ این پذیرش به معنی معتبرشدن یا انتشار قانون نیست.',
 education:'مطالب آموزشی، فنی و مرجع جدا از قوانین نمایش داده می‌شوند. وجود شماره ماده در جزوه، آن را قانون لازم‌الاجرا نمی‌کند.',
 categories:'برای هر سند نوع منبع، نام بسته، نام فایل و تعداد رکوردها را ببین. اصلاح طبقه‌بندی و شناسنامه نسخه از بخش مدیریت جامع دانش انجام می‌شود.',
 segments:'تمام ماده‌ها، بندها و قطعات استخراج‌شده را با فیلتر بسته و دسته بررسی کن؛ بازبینی تکی با سه تصمیم مستقیم و نمایش خودکار مورد بعدی انجام می‌شود.',
 review:'موارد در انتظار بازبینی در همه بسته‌های انتخاب‌شده نمایش داده می‌شوند. تصمیم سریع متن آزاد اجباری ندارد ولی در تاریخچه ثبت می‌شود.'
}
const qs=(values:Record<string,string|number|undefined>)=>{const p=new URLSearchParams();Object.entries(values).forEach(([k,v])=>{if(v!==undefined&&v!=='')p.set(k,String(v))});return '?'+p.toString()}
const errorMessage=(error:unknown)=>error instanceof Error?error.message:'عملیات انجام نشد'
const num=(n:number)=>n.toLocaleString('fa-IR')
function usePackages(){return useQuery({queryKey:['knowledge-workspace','selector'],queryFn:()=>api<Package[]>(ROOT+'/selector'),staleTime:20000})}
function scopeInfo(value:string[],packages:Package[]){
 const all=value.length===0||value.includes('ALL')||value.includes('LAW_ALL')&&value.includes('EDU_ALL')
 const lawAll=value.includes('LAW_ALL'),eduAll=value.includes('EDU_ALL')
 const lawIds=lawAll?packages.filter(p=>p.law_documents>0).map(p=>p.id):value.filter(v=>v.startsWith('LAW:')).map(v=>v.slice(4))
 const eduIds=eduAll?packages.filter(p=>p.education_documents>0).map(p=>p.id):value.filter(v=>v.startsWith('EDU:')).map(v=>v.slice(4))
 const selectedIds=Array.from(new Set([...lawIds,...eduIds]))
 return {all,lawIds,eduIds,selectedIds}
}
export function KnowledgePackageScopePicker({value,onChange}:{value:string[];onChange:(value:string[])=>void}){
 const packs=usePackages()
 const groups=useMemo(()=>{
  const p=packs.data??[]
  return [{label:'همه بسته‌ها',value:'ALL'},
   {label:'قوانین و مقررات',options:[{label:'همه بسته‌های قوانین',value:'LAW_ALL'},...p.filter(v=>v.law_documents>0).map(v=>({label:v.archive_name,value:'LAW:'+v.id}))]},
   {label:'مطالب آموزشی و فنی',options:[{label:'همه بسته‌های آموزشی و فنی',value:'EDU_ALL'},...p.filter(v=>v.education_documents>0).map(v=>({label:v.archive_name,value:'EDU:'+v.id}))]}]
 },[packs.data])
 return <Select mode="multiple" maxTagCount="responsive" showSearch optionFilterProp="label" value={value} options={groups} loading={packs.isLoading}
  placeholder="همه بسته‌ها / قوانین / آموزشی — انتخاب چندگانه" style={{width:'min(640px, 100%)'}}
  onChange={v=>{const values=v as string[];if(values.includes('ALL'))onChange(value.includes('ALL')&&values.length>1?values.filter(x=>x!=='ALL'):['ALL']);else onChange(values.length?values:['ALL'])}}
  onClear={()=>onChange(['ALL'])} allowClear/>
}
export function KnowledgeScopeSummary({value}:{value:string[]}){
 const p=usePackages();const scope=useMemo(()=>scopeInfo(value,p.data??[]),[value,p.data])
 const counted=scope.all?(p.data??[]):(p.data??[]).filter(x=>scope.selectedIds.includes(x.id))
 const laws=new Set(scope.lawIds),edu=new Set(scope.eduIds)
 const stats=[['بسته‌ها',counted.length],['اسناد',counted.reduce((s,p)=>s+(scope.all?p.documents:(laws.has(p.id)?p.law_documents:0)+(edu.has(p.id)?p.education_documents:0)),0)],['قوانین',counted.reduce((s,p)=>s+(scope.all||laws.has(p.id)?p.rules:0),0)],['مواد و بندها',counted.reduce((s,p)=>s+(scope.all?p.legal_segments+p.educational_segments:(laws.has(p.id)?p.legal_segments:0)+(edu.has(p.id)?p.educational_segments:0)),0)]] as const
 return <Row gutter={[10,8]} style={{marginTop:10}}>{stats.map(([name,v])=><Col xs={12} md={6} key={name}><Statistic title={name} value={v} loading={p.isLoading}/></Col>)}</Row>
}
export function KnowledgeReviewWorkspace({mode,scope}:{mode:WorkspaceMode;scope:string[]}){
 const {message}=App.useApp();const client=useQueryClient();const packs=usePackages()
 const [page,setPage]=useState(1),[size,setSize]=useState(20)
 const [draft,setDraft]=useState(''),[search,setSearch]=useState(''),[status,setStatus]=useState('ALL')
 const [helpOpen,setHelpOpen]=useState(false),[focused,setFocused]=useState<RowItem|null>(null)
  const [autoNext,setAutoNext]=useState(true),[advance,setAdvance]=useState(false)
  const [archivingId,setArchivingId]=useState<string|null>(null)
 const info=useMemo(()=>scopeInfo(scope,packs.data??[]),[scope,packs.data])
 const selectedIds=mode==='rules'?info.lawIds:mode==='education'?info.eduIds:info.selectedIds
 const selectionEmpty=!info.all&&!selectedIds.length
 const selectedKey=info.all?'ALL':selectedIds.join(',')
 const recordType=mode==='rules'?'RULE':mode==='education'||mode==='segments'?'SEGMENT':'ALL'
 const scopeCategory=info.all?'ALL':info.lawIds.length&&info.eduIds.length?'ALL':info.lawIds.length?'LAW':'NONLAW'
 const category=mode==='rules'?'LAW':mode==='education'?'NONLAW':scopeCategory
 const effectiveStatus=mode==='review'?'PENDING_REVIEW':status
 useEffect(()=>{setPage(1);setFocused(null);setAdvance(false)},[mode,selectedKey])
 const params=qs({page,size,category,recordType,reviewStatus:effectiveStatus,search,batchIds:info.all?undefined:selectedKey})
 const rows=useQuery({queryKey:['knowledge-workspace','rows',params,mode],enabled:!selectionEmpty&&mode!=='categories'&&(!packs.isLoading),queryFn:()=>api<Listing<RowItem>>(ROOT+'/items'+params)})
 const docsParams=qs({page,size,search,category:scopeCategory,batchIds:info.all?undefined:selectedKey})
 const documents=useQuery({queryKey:['knowledge-workspace','documents',docsParams],enabled:mode==='categories'&&!selectionEmpty&&!packs.isLoading,queryFn:()=>api<Listing<Document>>(ROOT+'/documents'+docsParams)})
 const items=rows.data?.items??[]
 useEffect(()=>{if(advance&&!rows.isFetching&&items.length){setFocused(items[0]);setAdvance(false)}},[advance,rows.isFetching,items])
 const decision=useMutation({mutationFn:({id,action}:{id:string;action:'APPROVED'|'REJECTED'|'PENDING_REVIEW'})=>api(ROOT+'/decision',{method:'POST',body:JSON.stringify({itemId:id,decision:action})}),
 onError:e=>message.error(errorMessage(e))})
 const decide=async(action:'APPROVED'|'REJECTED'|'PENDING_REVIEW')=>{
  if(!focused)return
  const previous=focused
  try{
   await decision.mutateAsync({id:previous.id,action})
   message.success('تصمیم ثبت شد؛ انتشار قانون انجام نشده است')
   void client.invalidateQueries({queryKey:['knowledge-workspace','rows']})
   void client.invalidateQueries({queryKey:['ai-import-rules']})
   void client.invalidateQueries({queryKey:['ai-import-segments']})
   void client.invalidateQueries({queryKey:['ai-import-batches']})
   void client.invalidateQueries({queryKey:['ai-lifecycle']})
   if(!autoNext){setFocused(null);return}
   const idx=items.findIndex(x=>x.id===previous.id)
   const next=items.slice(idx+1).find(x=>x.id!==previous.id&&x.review_status!==action)
   if(next){setFocused(next);return}
   if(mode==='review'||effectiveStatus==='PENDING_REVIEW'){
     const latest=await api<Listing<RowItem>>(ROOT+'/items'+params)
     setFocused(latest.items.find(x=>x.id!==previous.id)??null)
   }
   else if((rows.data?.total??0)>page*size){setFocused(null);setAdvance(true);setPage(p=>p+1)}
   else {setFocused(null);message.info('پایان فهرست فعلی')}
  }catch{/* onError handles errors; keep current item open */}
 }
 const archiveOne=async(r:RowItem)=>{
  if(archivingId)return
  try{
   const selection={type:'ITEM',ids:[r.id]}
   const impact=await api<{items:number}>('/api/v1/knowledge/admin/ai-lifecycle/preview',{method:'POST',body:JSON.stringify(selection)})
   if(impact.items!==1){message.warning('این رکورد دیگر فعال نیست؛ فهرست را تازه‌سازی کن.');void client.invalidateQueries({queryKey:['knowledge-workspace']});return}
   Modal.confirm({title:'آرشیو همین رکورد؟',content:<Space direction="vertical"><Typography.Text>{r.document_title} — {r.article_label||r.item_key}</Typography.Text><Typography.Text type="secondary">آرشیو قابل‌بازیابی است و رکورد از تب‌های فعال پنهان می‌شود. برای بازیابی به «مدیریت جامع دانش ← مدیریت حذف و بازیابی» برو.</Typography.Text></Space>,
     okText:'آرشیو همین رکورد',okButtonProps:{danger:true},cancelText:'انصراف',
     onOk:async()=>{setArchivingId(r.id);try{
       await api('/api/v1/knowledge/admin/ai-lifecycle/archive',{method:'POST',body:JSON.stringify({selection,reason:'آرشیو تک‌رکوردی از صفحه بازبینی دانش'})})
       setFocused(v=>v?.id===r.id?null:v);message.success('رکورد آرشیو شد؛ امکان بازیابی وجود دارد')
       await Promise.all([client.invalidateQueries({queryKey:['knowledge-workspace']}),client.invalidateQueries({queryKey:['ai-lifecycle']}),client.invalidateQueries({queryKey:['ai-lifecycle-cleanup-items']}),client.invalidateQueries({queryKey:['ai-import-batches']})])
     }catch(e){message.error(errorMessage(e));throw e}finally{setArchivingId(null)}}
   })
  }catch(e){message.error(errorMessage(e))}
 }
 const changePage=(nextPage:number,nextSize:number)=>{setPage(nextPage);setSize(nextSize);setFocused(null)}
 const pagination={current:page,pageSize:size,total:mode==='categories'?(documents.data?.total??0):(rows.data?.total??0),
  showSizeChanger:true,pageSizeOptions:sizes,showTotal:(total:number)=>`${num(total)} مورد`,onChange:changePage}
 const docColumns=[{title:'عنوان سند',dataIndex:'title',width:240},{title:'نوع',dataIndex:'category',width:110,render:(v:string)=><Tag>{cat[v]??v}</Tag>},
  {title:'منبع اصلی',dataIndex:'source_reference',width:260,ellipsis:true},{title:'بسته',dataIndex:'archive_name',width:180,ellipsis:true},
  {title:'رکوردها',dataIndex:'item_count',width:100},{title:'نسخه',dataIndex:'edition_label',width:120}]
 return <div dir="rtl" className="sakhtyar-quick-review">
  <Space wrap style={{width:'100%',justifyContent:'space-between',marginBottom:10}}>
   <Space wrap><Button size="small" icon={<QuestionCircleOutlined/>} onClick={()=>setHelpOpen(x=>!x)}>راهنمای این بخش</Button>
     <Button size="small" icon={<ReloadOutlined/>} onClick={()=>{void rows.refetch();void documents.refetch()}}>تازه‌سازی</Button></Space>
   <Tag color="blue">{info.all?'همه بسته‌ها':`${num(selectedIds.length)} بسته انتخاب‌شده`}</Tag>
  </Space>
  {helpOpen&&<Alert type="info" showIcon closable onClose={()=>setHelpOpen(false)} message="راهنمای کار با این بخش" description={help[mode]} style={{marginBottom:12}}/>}
  {selectionEmpty?<Alert type="warning" showIcon message="برای این تب هیچ بسته مرتبطی انتخاب نشده است" description="از انتخاب‌گر بالای صفحه، همه بسته‌ها یا زیرگروه متناسب را انتخاب کن."/>:<>
    <Space wrap style={{marginBottom:10}}>
     <Input.Search placeholder="جست‌وجو در عنوان، متن یا منبع" value={draft} onChange={e=>setDraft(e.target.value)} onSearch={s=>{setSearch(s);setPage(1)}} allowClear style={{width:300,maxWidth:'95vw'}}/>
     {mode!=='categories'&&mode!=='review'&&<Select value={status} style={{minWidth:185}} onChange={v=>{setStatus(v);setPage(1)}} options={[
       {label:'همه وضعیت‌ها',value:'ALL'},{label:'در انتظار بررسی',value:'PENDING_REVIEW'},
       {label:'پذیرفته‌شده',value:'APPROVED'},{label:'ردشده',value:'REJECTED'}]}/>}
    </Space>
    {mode==='categories'?
      <Table<Document> size="small" rowKey="id" columns={docColumns} dataSource={documents.data?.items??[]} loading={documents.isFetching} pagination={pagination} scroll={{x:1000,y:510}}/>:
      <Table<RowItem> size="small" rowKey="id" loading={rows.isFetching} dataSource={items} pagination={pagination} scroll={{x:1050,y:510}}
       columns={[{title:'سند',dataIndex:'document_title',width:230,ellipsis:true},
        {title:'موضوع / شماره',width:140,render:(_,r)=>r.article_label||r.item_key},
        {title:'نوع',width:110,render:(_,r)=><Tag>{r.category==='LAW'?'قانون':cat[r.category]??'آموزشی'}</Tag>},
        {title:'صفحه',width:80,render:(_,r)=>r.page_from===r.page_to?r.page_from:`${r.page_from}–${r.page_to}`},
        {title:'متن',dataIndex:'full_text',width:310,ellipsis:true},
        {title:'وضعیت',dataIndex:'review_status',width:170,render:v=><Tag color={v==='APPROVED'?'green':v==='REJECTED'?'red':'gold'}>{statusNames[String(v)]??String(v)}</Tag>},
        {title:'عملیات',fixed:'left' as const,width:245,render:(_,r)=><Space size={4}><Button size="small" onClick={()=>setFocused(r)}>مشاهده و تصمیم</Button>
          <Button size="small" danger loading={archivingId===r.id} onClick={()=>void archiveOne(r)}>آرشیو رکورد</Button></Space>}]} />}
  </>}
  <Modal open={!!focused} title="بازبینی سریع — سه تصمیم مستقیم" width={950} onCancel={()=>{if(!decision.isPending){setFocused(null);setAdvance(false)}}}
   footer={focused?<Row gutter={[8,8]} align="middle"><Col xs={24} md={9}>
     <Checkbox checked={autoNext} onChange={e=>setAutoNext(e.target.checked)}>پس از تصمیم، مورد بعدی را نمایش بده</Checkbox>
    </Col><Col xs={24} md={15}><Space wrap>
     <Button loading={decision.isPending} icon={<RollbackOutlined/>} onClick={()=>void decide('PENDING_REVIEW')} disabled={focused.review_status==='PENDING_REVIEW'}>بازگرداندن به بازبینی</Button>
     <Button danger loading={decision.isPending} icon={<CloseCircleOutlined/>} onClick={()=>void decide('REJECTED')} disabled={focused.review_status==='REJECTED'}>رد به‌دلیل کیفیت/استناد</Button>
     <Button type="primary" loading={decision.isPending} icon={<CheckCircleOutlined/>} onClick={()=>void decide('APPROVED')} disabled={focused.review_status==='APPROVED'}>تأیید برای مرحله بعد</Button>
    </Space></Col></Row>:null}>
   {focused&&<Space direction="vertical" style={{width:'100%'}} size="middle">
     <Descriptions bordered size="small" column={2} items={[
       {key:'doc',label:'سند',children:focused.document_title,span:2},
       {key:'category',label:'دسته',children:cat[focused.category]??focused.category},
       {key:'page',label:'صفحات',children:`${focused.page_from} تا ${focused.page_to}`},
       {key:'bundle',label:'بسته',children:focused.archive_name,span:2},
       {key:'source',label:'مرجع ادعاشده',children:focused.source_reference,span:2}
     ]}/>
     <Row gutter={[12,12]}><Col xs={24} md={15}><Card size="small" title="متن دقیق استخراج‌شده" styles={{body:{maxHeight:230,overflowY:'auto',whiteSpace:'pre-wrap',lineHeight:2}}}>{focused.full_text}</Card></Col>
       <Col xs={24} md={9}><Card size="small" title="شاهد ارائه‌شده" styles={{body:{maxHeight:230,overflowY:'auto',whiteSpace:'pre-wrap',lineHeight:2}}}>{focused.evidence_quote}</Card></Col></Row>
     {focused.category==='LAW'&&/drmostafanajafi|جزوه|آموزش|training|tutorial|handout/i.test(focused.source_reference)?
       <Alert type="error" showIcon message="هشدار: مرجع این قانون ظاهراً آموزشی است" description="این متن را قبل از تأیید با نسخه رسمی قانون مطابقت بده. طبقه‌بندی خودکارِ این منبع می‌تواند اشتباه باشد."/>:
       <Alert type="warning" showIcon message="منبع استخراج‌شده هنوز با سند رسمی تطبیق داده نشده است" description="قبل از پذیرش، عنوان و مرجع را بررسی کن. این تصمیم فقط در مرحله بازبینی ذخیره می‌شود و قانون منتشرشده ایجاد نمی‌کند."/>}
   </Space>}
  </Modal>
 </div>
}
