import {useState} from 'react'
import {Alert,App,Button,Card,Checkbox,Input,Modal,Select,Space,Table,Tag,Typography} from 'antd'
import {useQuery,useQueryClient} from '@tanstack/react-query'
import {api} from '../api/client'

const ROOT='/api/v1/knowledge/admin/ai-curation'
const SIZE_OPTIONS=['10','20','50','100','200','500','1000']
type Listing<T>={items:T[];total:number;page:number;size:number}
type PublishedItem={id:string;document_id:string;document_title:string;article_label?:string;full_text:string;source_reference:string;archive_name:string;category:string;published_at?:string;published_by?:string}
type Duplicate={content_sha256:string;occurrences:number;batches:number;example_title:string;cleanable:boolean;protection_reason:string}
type Scope={type:string;ids:string[];category?:string}
const pdfFilename=(source:string)=>{
 for(const fragment of String(source??'').split('|')){
  const end=fragment.toLowerCase().indexOf('.pdf')
  if(end<0)continue
  const raw=fragment.slice(0,end+4).trim().replace(/\\/g,'/')
  const name=raw.split('/').pop()?.trim()??''
  if(name.toLowerCase().endsWith('.pdf')&&!name.includes('..'))return name
 }
 return null
}
const err=(e:unknown)=>e instanceof Error?e.message:'عملیات انجام نشد'
const post=<T,>(path:string,body:unknown)=>api<T>(ROOT+path,{method:'POST',body:JSON.stringify(body)})
function ask(title:string,description:string,execute:()=>Promise<void>,danger=false){
 Modal.confirm({title,content:description,okText:'تأیید و اجرا',cancelText:'انصراف',okButtonProps:{danger},onOk:execute})
}

export function KnowledgePublicationPanel(){
 const {message}=App.useApp(),client=useQueryClient()
 const [kind,setKind]=useState<'LAW'|'NONLAW'>('LAW')
 const [published,setPublished]=useState(false)
 const [page,setPage]=useState(1),[size,setSize]=useState(20),[search,setSearch]=useState('')
 const [selected,setSelected]=useState<string[]>([]),[busy,setBusy]=useState(false)
 const [ack,setAck]=useState(false)
 const [note,setNote]=useState('منبع و متن با مسئولیت مدیر بررسی و برای کاتالوگ کارشناسی ساخت‌یار انتخاب شده‌اند؛ این اقدام اعتبار رسمی قانون ایجاد نمی‌کند.')
 const params=new URLSearchParams({category:kind,published:String(published),page:String(page),size:String(size),search})
 const list=useQuery({queryKey:['ai-curation-publications',kind,published,page,size,search],queryFn:()=>api<Listing<PublishedItem>>(ROOT+'/publish/items?'+params)})
 const visible=list.data?.items??[]
 const action=async(mode:'SELECTED'|'PAGE'|'ALL')=>{
  if(!ack){message.warning('ابتدا تأیید مسئولیت تطبیق متن و منبع را فعال کن.');return}
  const ids=mode==='SELECTED'?selected:mode==='PAGE'?visible.map(r=>r.id):[]
  if(mode!=='ALL'&&!ids.length){message.warning('ابتدا یک یا چند ردیف انتخاب کن.');return}
  const body={category:kind,scope:mode,ids,expectedCount:0,confirmed:true,note}
  try{
   setBusy(true)
   const preview=await post<{count:number}>('/publish/preview',body)
   if(!preview.count){message.info('موردی برای انتشار در این دامنه وجود ندارد.');return}
   ask('انتشار در کاتالوگ کارشناسی',`انتشار ${preview.count.toLocaleString('fa-IR')} مورد. تأیید کارشناسی اعتبار رسمی قانون ایجاد نمی‌کند.`,async()=>{
    try{await post('/publish/apply',{...body,expectedCount:preview.count});message.success('انتشار در کاتالوگ ثبت شد');setSelected([]);void client.invalidateQueries({queryKey:['ai-curation-publications']});void client.invalidateQueries({queryKey:['ai-lifecycle']})}
    catch(e){message.error(err(e))}
   })
  }catch(e){message.error(err(e))}finally{setBusy(false)}
 }
 const revoke=async()=>{
  if(!selected.length){message.warning('ابتدا رکوردها را انتخاب کن.');return}
  ask('لغو انتشار',`انتشار ${selected.length} رکورد انتخابی لغو شود؟`,async()=>{
   try{await post('/publish/revoke',{ids:selected,confirmation:'REVOKE PUBLICATION'});message.success('انتشار لغو شد');setSelected([]);void client.invalidateQueries({queryKey:['ai-curation-publications']})}
   catch(e){message.error(err(e))}
  },true)
 }
 return <Card title="انتشار مستقل قوانین و مطالب آموزشی" size="small" styles={{body:{padding:16}}}>
  <Alert showIcon type="info" message="تأیید بازبینی با انتشار متفاوت است"
   description="در این تب فقط رکوردهای پذیرفته‌شده به کاتالوگ کارشناسی ساخت‌یار وارد می‌شوند. انتشار، صحت رسمی قوانین را تضمین نمی‌کند و اسناد تاریخی همچنان مستقل‌اند." style={{marginBottom:12}}/>
  <Space wrap style={{marginBottom:12}}>
   <Select value={kind} style={{width:220}} onChange={v=>{setKind(v);setSelected([]);setPage(1)}} options={[{value:'LAW',label:'قوانین و مقررات'},{value:'NONLAW',label:'مطالب آموزشی و فنی'}]}/>
   <Select value={published?'published':'pending'} style={{width:210}} onChange={v=>{setPublished(v==='published');setSelected([]);setPage(1)}} options={[{value:'pending',label:'آماده انتشار'},{value:'published',label:'منتشرشده'}]}/>
   <Input.Search placeholder="جست‌وجو در متن و عنوان" style={{width:250}} allowClear onSearch={v=>{setSearch(v);setPage(1)}} />
  </Space>
  <Table<PublishedItem> size="small" rowKey="id" loading={list.isFetching} dataSource={visible}
   rowSelection={{selectedRowKeys:selected,onChange:keys=>setSelected(keys.map(String))}}
   scroll={{x:900,y:470}} pagination={{current:page,pageSize:size,total:list.data?.total??0,showSizeChanger:true,pageSizeOptions:SIZE_OPTIONS,onChange:(p,s)=>{setPage(p);setSize(s);setSelected([])}}}
   columns={[{title:'سند',dataIndex:'document_title',width:220},{title:'عنوان',dataIndex:'article_label',width:130},{title:'متن',dataIndex:'full_text',ellipsis:true,width:350},
    {title:'PDF مرجع',width:250,render:(_,r)=>pdfFilename(r.source_reference)?<Space direction="vertical" size={0}><Typography.Text ellipsis title={r.source_reference} style={{maxWidth:230}}>{pdfFilename(r.source_reference)}</Typography.Text><a href={`${ROOT}/documents/${r.document_id}/source`} target="_blank" rel="noreferrer" download>دانلود PDF اصلی</a></Space>:<Tag title={r.source_reference}>نام فایل مرجع ثبت نشده</Tag>},{title:'وضعیت',width:120,render:()=>published?<Tag color="green">منتشر</Tag>:<Tag color="gold">آماده</Tag>}]}/>
  {!published&&<Space direction="vertical" style={{width:'100%'}}>
   <Checkbox checked={ack} onChange={e=>setAck(e.target.checked)}>مسئولیت تطبیق متن با منبع معتبر و انتخاب برای کاتالوگ کارشناسی را تأیید می‌کنم.</Checkbox>
   <Input.TextArea rows={2} maxLength={1500} value={note} onChange={e=>setNote(e.target.value)} placeholder="یادداشت مسئول انتشار و استناد"/>
   <Space wrap><Button type="primary" disabled={!ack||busy} onClick={()=>void action('SELECTED')}>انتشار موارد انتخابی ({selected.length})</Button>
    <Button disabled={!ack||busy} onClick={()=>void action('PAGE')}>انتشار همین صفحه ({visible.length})</Button>
    <Button disabled={!ack||busy} onClick={()=>void action('ALL')}>انتشار همه صفحات این دسته</Button></Space>
  </Space>}
  {published&&<Button danger disabled={!selected.length} onClick={()=>void revoke()}>لغو انتشار انتخاب‌شده‌ها ({selected.length})</Button>}
 </Card>
}

export function KnowledgeDuplicatesPanel(){
 const {message}=App.useApp(),client=useQueryClient()
 const [page,setPage]=useState(1),[size,setSize]=useState(20),[selected,setSelected]=useState<string[]>([])
 const [permanent,setPermanent]=useState(false),[busy,setBusy]=useState(false)
 const [lastResult,setLastResult]=useState<{removed:number;preserved:number;skipped:number}|null>(null)
 const list=useQuery({queryKey:['ai-curation-duplicates',page,size],queryFn:()=>api<Listing<Duplicate>>(ROOT+`/duplicate-groups?page=${page}&size=${size}`)})
 const rows=list.data?.items??[]
 const go=async(scope:'SELECTED'|'PAGE'|'ALL')=>{
  const hashes=scope==='SELECTED'?selected:scope==='PAGE'?rows.filter(x=>x.cleanable).map(x=>x.content_sha256):[]
  if(scope!=='ALL'&&!hashes.length){message.warning('ابتدا گروه تکراری انتخاب کن');return}
  const body={scope,hashes,permanent,expectedCount:0,confirmation:'DEDUPLICATE CONFIRMED'}
  setBusy(true)
  try{
   const preview=await post<{remove:number;preserved:number;skipped:number}>('/duplicates/preview',body)
   if(!preview.remove){setLastResult({removed:0,preserved:preview.preserved,skipped:preview.skipped});message.warning(`هیچ تکراری قابل پاک‌سازی نبود؛ ${preview.skipped} گروه به دلیل تفاوت منبع، نسخه یا انتشار محافظت شده‌اند.`);await list.refetch();return}
   ask(permanent?'حذف دائمی تکراری‌ها':'آرشیو تکراری‌ها',
    `${preview.remove} رکورد تکراری ${permanent?'دائماً حذف':'آرشیو'} می‌شود؛ ${preview.preserved} نسخه نگه داشته می‌شود و ${preview.skipped} گروه با منشأ/موضوع متفاوت محافظت می‌شود.`,async()=>{
    try{const outcome=await post<{removed:number;preserved:number;skipped:number}>('/duplicates/apply',{...body,expectedCount:preview.remove});setLastResult(outcome);message.success(`${outcome.removed} رکورد اضافی پاک‌سازی شد؛ ${outcome.skipped} گروه محافظت شد.`);setSelected([]);setPage(1);await client.invalidateQueries({queryKey:['ai-curation-duplicates']});void client.invalidateQueries({queryKey:['ai-lifecycle']})}
    catch(e){message.error(err(e))}
   },permanent)
  }catch(e){message.error(err(e))}finally{setBusy(false)}
 }
 return <Card size="small" title="پاک‌سازی هوشمند تکراری‌ها">
  <Alert showIcon type="warning" style={{marginBottom:12}} message="تنها تکراری‌های کاملاً هم‌منشأ قابل ادغام خودکار هستند"
   description="هش یکسان به‌تنهایی دلیل یکی‌بودن سند نیست. اگر عنوان، منبع PDF، نسخه تاریخی یا شماره ماده متفاوت باشد، گروه با برچسب محافظت‌شده باقی می‌ماند. پاک‌سازی موفق فقط زمانی است که تعداد فعال تکراری‌ها واقعاً کاهش یافته باشد."/>
  {lastResult&&<Alert showIcon type={lastResult.removed?'success':'warning'} style={{marginBottom:12}} message={`گزارش واقعی: ${lastResult.removed} حذف/آرشیو، ${lastResult.preserved} نسخه حفظ‌شده، ${lastResult.skipped} گروه محافظت‌شده`} description="گروه‌های محافظت‌شده همچنان در جدول دیده می‌شوند؛ این موضوع نشانه اجرای ناموفق عملیات نیست."/>}
  <Table<Duplicate> size="small" rowKey="content_sha256" loading={list.isFetching} dataSource={rows}
   rowSelection={{selectedRowKeys:selected,onChange:keys=>setSelected(keys.map(String)),getCheckboxProps:r=>({disabled:!r.cleanable})}}
   scroll={{x:1000,y:465}} pagination={{current:page,pageSize:size,total:list.data?.total??0,showSizeChanger:true,pageSizeOptions:['10','20','50','100','200'],onChange:(p,s)=>{setPage(p);setSize(s);setSelected([])}}}
   columns={[{title:'سند نمونه',dataIndex:'example_title',width:270},{title:'تکرار فعال',dataIndex:'occurrences',width:100},{title:'بسته‌ها',dataIndex:'batches',width:95},{title:'امکان پاک‌سازی',width:180,render:(_,r)=>r.cleanable?<Tag color="green">قابل پاک‌سازی</Tag>:<Tag color="orange" title={r.protection_reason}>محافظت‌شده (تفاوت منبع/نسخه)</Tag>},{title:'SHA-256',dataIndex:'content_sha256',ellipsis:true,width:300}]}/>
  <Space direction="vertical" style={{width:'100%'}}>
   <Checkbox checked={permanent} onChange={e=>setPermanent(e.target.checked)}>حذف دائمی (فقط مدیر ADMIN)؛ پشتیبان حسابرسی در سرور به‌صورت خودکار ساخته شود</Checkbox>
   <Space wrap><Button disabled={busy||!selected.length} onClick={()=>void go('SELECTED')}>پاک‌سازی انتخابی ({selected.length})</Button>
    <Button disabled={busy||!rows.some(x=>x.cleanable)} onClick={()=>void go('PAGE')}>پاک‌سازی قابل‌حذف‌های همین صفحه</Button>
    <Button danger={permanent} disabled={busy||!list.data?.total} onClick={()=>void go('ALL')}>پاک‌سازی همه صفحات</Button></Space>
  </Space>
 </Card>
}

export function KnowledgeDirectDeletePanel({selected,visible,refresh}:{selected:string[];visible:string[];refresh:()=>void}){
 const {message}=App.useApp()
 const [scope,setScope]=useState<'SELECTED'|'PAGE'|'ALL'>('SELECTED')
 const [confirm,setConfirm]=useState(''),[busy,setBusy]=useState(false)
 const request:Scope=scope==='ALL'?{type:'ALL',ids:[]}:{type:'ITEM',ids:scope==='PAGE'?visible:selected}
 const run=async()=>{
  if(confirm!=='DELETE STAGING PERMANENTLY'){message.warning('عبارت تأیید را دقیق وارد کن');return}
  if(scope!=='ALL'&&!request.ids.length){message.warning('حداقل یک رکورد را انتخاب کن');return}
  setBusy(true)
  try{
   const prev=await post<{count:number;published:number}>('/delete/preview',request)
   if(prev.count<1){message.warning('رکوردی برای حذف پیدا نشد');return}
   if(prev.published){message.error(`${prev.published} رکورد منتشرشده در محدوده وجود دارد؛ ابتدا انتشارشان را لغو کن.`);return}
   ask('حذف دائمی فقط از AI Import',`حذف دائمی ${prev.count} رکورد. یک پشتیبان حسابرسی در سرور به شکل خودکار ثبت می‌شود و نیازی به دانلود قبلی نیست. ممکن است رکوردهای تاریخچه‌دار محافظت شوند.`,async()=>{
    try{await post('/delete/apply',{selection:request,expectedCount:prev.count,confirmation:confirm});message.success('رکوردها حذف شدند');setConfirm('');refresh()}
    catch(e){message.error(err(e))}
   },true)
  }catch(e){message.error(err(e))}finally{setBusy(false)}
 }
 return <Card size="small" title="حذف دائمی مستقیم (فقط مدیر)" style={{marginTop:12}}>
  <Alert type="warning" showIcon style={{marginBottom:12}} message="پشتیبان‌گیری دستی اجباری نیست"
   description="حذف در یک اقدام و پس از تأیید انجام می‌شود؛ سرور خودکار یک پشتیبان حسابرسی ایجاد می‌کند. رکوردهای منتشرشده و نسخه‌های تاریخی محافظت‌شده قابل پاک‌سازی مستقیم نیستند."/>
  <Space wrap style={{marginBottom:10}}>
   <Select value={scope} style={{minWidth:260}} onChange={setScope} options={[{value:'SELECTED',label:`رکوردهای انتخابی (${selected.length})`},
    {value:'PAGE',label:`تمام ردیف‌های همین صفحه (${visible.length})`},{value:'ALL',label:'همه رکوردها در همه صفحات AI Import'}]}/>
   <Input value={confirm} onChange={e=>setConfirm(e.target.value)} placeholder="DELETE STAGING PERMANENTLY" style={{width:280}}/>
   <Button type="primary" danger disabled={busy||confirm!=='DELETE STAGING PERMANENTLY'} onClick={()=>void run()}>حذف دائمی مستقیم</Button>
  </Space>
  <Typography.Text type="secondary">حذف مستقیم در هر نوبت حداکثر ۱۵هزار رکورد را پشتیبانی می‌کند؛ برای حجم بیشتر دامنه انتخاب را کوچک‌تر کن. حذف در کاتالوگ قوانین قطعی انجام نمی‌شود.</Typography.Text>
 </Card>
}

// Published expert-reviewed AI knowledge is discoverable in the Knowledge Centre;
// it is deliberately kept distinct from authoritative legal releases.
type CatalogueItem={id:string;document_id:string;document_title:string;category:string;article_label?:string;full_text:string;evidence_quote:string;page_from:number;page_to:number;source_reference:string;published_at:string;published_by:string;legal_status:string}
export function KnowledgePublishedCataloguePanel(){
 const [category,setCategory]=useState<'LAW'|'NONLAW'|'ALL'>('ALL')
 const [page,setPage]=useState(1),[size,setSize]=useState(20)
 const [draft,setDraft]=useState(''),[search,setSearch]=useState('')
 const p=new URLSearchParams({category,page:String(page),size:String(size),q:search})
 const list=useQuery({queryKey:['knowledge-expert-catalogue',category,page,size,search],queryFn:()=>api<Listing<CatalogueItem>>('/api/v1/knowledge/ai-catalogue/search?'+p)})
 return <Card size="small" title="کاتالوگ دانش منتشرشده و بررسی‌شده" className="sakhtyar-curation-main">
   <Alert type="info" showIcon style={{marginBottom:12}} message="فقط رکوردهای منتشرشده در کاتالوگ کارشناسی"
     description="در اینجا فقط مواردی نمایش داده می‌شوند که در مرحله بازبینی پذیرفته و سپس صریحاً منتشر شده‌اند. این کاتالوگ جانشین انتشار رسمی قانون یا ضوابط معتبر نیست."/>
   <Space wrap style={{marginBottom:12}}>
    <Select value={category} style={{width:210}} onChange={v=>{setCategory(v);setPage(1)}} options={[{value:'ALL',label:'قانون و آموزش با هم'},{value:'LAW',label:'قوانین و مقررات'},{value:'NONLAW',label:'آموزشی و فنی'}]}/>
    <Input.Search value={draft} onChange={e=>setDraft(e.target.value)} onSearch={v=>{setSearch(v);setPage(1)}} allowClear placeholder="جست‌وجو در سند یا متن" style={{width:340,maxWidth:'100%'}}/>
   </Space>
   <Table<CatalogueItem> rowKey="id" size="small" loading={list.isFetching} dataSource={list.data?.items??[]}
    scroll={{x:1120,y:490}} pagination={{current:page,pageSize:size,total:list.data?.total??0,showSizeChanger:true,pageSizeOptions:['10','20','50','100'],onChange:(v,n)=>{setPage(v);setSize(n)}}}
    columns={[{title:'عنوان منبع',dataIndex:'document_title',width:230,ellipsis:true},
      {title:'طبقه',dataIndex:'category',width:120,render:v=><Tag>{v==='LAW'?'قوانین کارشناسی':'آموزشی / فنی'}</Tag>},
      {title:'ماده / بند',dataIndex:'article_label',width:120},
      {title:'متن قابل استفاده',dataIndex:'full_text',width:370,ellipsis:true},
      {title:'انتشار',dataIndex:'published_at',width:180,render:v=>String(v??'').slice(0,19)},
      {title:'فایل PDF',width:190,render:(_,r)=>pdfFilename(r.source_reference)?<Space direction="vertical" size={0}><Typography.Text ellipsis style={{maxWidth:185}} title={r.source_reference}>{pdfFilename(r.source_reference)}</Typography.Text><a target="_blank" rel="noreferrer" download href={`/api/v1/knowledge/admin/ai-curation/documents/${r.document_id}/source`}>دانلود PDF اصلی</a></Space>:<Tag>PDF شناسایی نشد</Tag>}]}/>
 </Card>
}
