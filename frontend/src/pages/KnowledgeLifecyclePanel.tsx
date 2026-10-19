import {useMemo, useState} from 'react'
import type {Key} from 'react'
import {Alert, App, Button, Card, Checkbox, Col, Descriptions, Divider, Input, Modal, Row, Select, Space, Statistic, Progress, Table, Tabs, Tag, Typography} from 'antd'
import {DatabaseOutlined, DeleteOutlined, ReloadOutlined, SafetyCertificateOutlined} from '@ant-design/icons'
import {KnowledgeDuplicatesPanel,KnowledgeDirectDeletePanel} from './KnowledgeCurationPanels'
import '../styles/knowledge-curation-v55.css'
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query'
import {api} from '../api/client'

const ROOT='/api/v1/knowledge/admin/ai-lifecycle'
const catNames: Record<string,string>={LAW:'قوانین و مقررات',EDUCATION:'مطالب آموزشی',TECHNICAL:'منابع فنی',REFERENCE:'منابع مرجع',OTHER:'سایر',UNCLASSIFIED:'نیازمند تعیین نوع'}
type Listing<T>={items:T[];total:number;page:number;size:number}
type Package={id:string;archive_name:string;source_ai:string;source_mode?:string;created_at:string;documents:number;rules:number;segments:number;issues:number;archived_at?:string}
type Doc={id:string;batch_id:string;archive_name:string;title:string;doc_key:string;category:string;suggested_category?:string;classification_reason?:string;source_reference:string;regulation_family?:string;edition_label?:string;calendar_type?:string;publication_date?:string;effective_from?:string;effective_to?:string;territorial_scope?:string;item_count:number;archived_at?:string}
type Item={id:string;batch_id:string;document_id:string;document_title:string;archive_name:string;item_key:string;record_type:string;segment_type:string;article_label?:string;full_text:string;evidence_quote:string;page_from:number;page_to:number;category:string;source_reference:string;review_status:string;archived_at?:string}
type Issue={id:string;batch_id:string;origin_file:string;line_number:number;doc_key?:string;item_key?:string;issue_code:string;explanation:string;suggestion:string;state:string;row_payload?:unknown}
type Scope={type:'ALL'|'BATCH'|'DOCUMENT'|'ITEM'|'CATEGORY';ids:string[];category?:string}
type Overview={batches:number;documents:number;rules:number;segments:number;education:number;pending:number;issues:number;archived:number}
type Difference={key:string;status:string;before?:Item;after?:Item}
function qs(values: Record<string,string|number|boolean|undefined>){const p=new URLSearchParams();Object.entries(values).forEach(([k,v])=>{if(v!==undefined&&v!==''&&v!==false)p.set(k,String(v))});return '?'+p.toString()}
function fmt(x:unknown){return new Date(String(x)).toLocaleString('fa-IR')}
function errorText(e:unknown){return e instanceof Error?e.message:'عملیات انجام نشد'}
/** Compact exact-text highlight; does not infer legal amendments or clause renumbering. */
function diffHighlight(before:string,after:string,side:'before'|'after'){
  let prefix=0;const lim=Math.min(before.length,after.length)
  while(prefix<lim&&before[prefix]===after[prefix])prefix++
  let suffix=0
  while(suffix<lim-prefix && before[before.length-1-suffix]===after[after.length-1-suffix])suffix++
  const text=side==='before'?before:after
  const middle=text.slice(prefix,suffix?text.length-suffix:text.length)
  return <Typography.Paragraph style={{whiteSpace:'pre-wrap',lineHeight:2}}>{text.slice(0,prefix)}
    {middle&&<mark style={{background:side==='before'?'#fce6e4':'#e4f8e6',padding:'1px 2px'}}>{middle}</mark>}
    {suffix?text.slice(text.length-suffix):''}</Typography.Paragraph>
}

export function KnowledgeLifecyclePanel(){
  const {message}=App.useApp();const client=useQueryClient()
  const [tab,setTab]=useState('overview')
  const [packagePage,setPackagePage]=useState(1)
  const [packageSize,setPackageSize]=useState(50) // SAKHTYAR_V534_PAGINATION
  const [helpOpen,setHelpOpen]=useState(false)
  const [packageSearch,setPackageSearch]=useState('')
  const [cleanupSearch,setCleanupSearch]=useState('')
  const [cleanupDraft,setCleanupDraft]=useState('')
  const [cleanupCategory,setCleanupCategory]=useState('ALL')
  const [cleanupBatch,setCleanupBatch]=useState('')
  const [cleanupPage,setCleanupPage]=useState(1)
  const [cleanupSize,setCleanupSize]=useState(20)
  const [cleanupShowArchived,setCleanupShowArchived]=useState(true)
  const [page,setPage]=useState(1);const [size,setSize]=useState(20)
  const [batch,setBatch]=useState('');const [category,setCategory]=useState('ALL');const [search,setSearch]=useState('')
  const [withArchived,setWithArchived]=useState(false)
  const [selectionMode,setSelectionMode]=useState<Scope['type']>('ITEM')
  const [selected,setSelected]=useState<string[]>([])
  const [reason,setReason]=useState('');const [preview,setPreview]=useState<{items:number;laws:number;approved:number}|null>(null)
  const [backupId,setBackupId]=useState('');const [backupSnapshot,setBackupSnapshot]=useState<unknown>(null)
  const [deleteConfirm,setDeleteConfirm]=useState('')
  const [issueDecision,setIssueDecision]=useState<Issue|null>(null);const [issueNote,setIssueNote]=useState('')
  const [editionDoc,setEditionDoc]=useState<Doc|null>(null)
  const [family,setFamily]=useState('');const [edition,setEdition]=useState('');const [calendar,setCalendar]=useState('JALALI')
  const [effectiveFrom,setEffectiveFrom]=useState('');const [publicationDate,setPublicationDate]=useState('')
  const [territory,setTerritory]=useState('');const [versionLeft,setVersionLeft]=useState('');const [versionRight,setVersionRight]=useState('')
  const [baseline,setBaseline]=useState('')
  const [asOfDate,setAsOfDate]=useState('')
  const [lookupDate,setLookupDate]=useState('')
  const overview=useQuery({queryKey:['ai-lifecycle-overview'],queryFn:()=>api<Overview>(ROOT+'/overview')})
  const packages=useQuery({queryKey:['ai-lifecycle-packages',packagePage,packageSize,packageSearch],queryFn:()=>api<Listing<Package>>(ROOT+'/packages'+qs({page:packagePage,size:packageSize,includingArchived:true,search:packageSearch}))})
  const allPackages=useQuery({queryKey:['knowledge-workspace','selector'],queryFn:()=>api<Package[]>('/api/v1/knowledge/admin/ai-workspace/selector')})
  const documents=useQuery({queryKey:['ai-lifecycle-documents',page,size,batch,category,search,withArchived],queryFn:()=>api<Listing<Doc>>(ROOT+'/documents'+qs({page,size,batchId:batch,category,search,includingArchived:withArchived}))})
  const cleanupItems=useQuery({queryKey:['ai-lifecycle-cleanup-items',cleanupPage,cleanupSize,cleanupBatch,cleanupCategory,cleanupSearch,cleanupShowArchived],
    enabled:tab==='cleanup',queryFn:()=>api<Listing<Item>>(ROOT+'/items'+qs({page:cleanupPage,size:cleanupSize,batchId:cleanupBatch,category:cleanupCategory,search:cleanupSearch,includingArchived:cleanupShowArchived}))})
  const itemsCategory = tab==='laws' ? 'LAW' : tab==='education' ? (category==='ALL'||category==='LAW'?'NONLAW':category) : category
  const items=useQuery({queryKey:['ai-lifecycle-items',page,size,batch,itemsCategory,search,withArchived],queryFn:()=>api<Listing<Item>>(ROOT+'/items'+qs({page,size,batchId:batch,category:itemsCategory,search,includingArchived:withArchived}))})
  const issues=useQuery({queryKey:['ai-lifecycle-issues',batch],queryFn:()=>api<Issue[]>(ROOT+'/issues'+qs({batchId:batch,limit:500}))})
  const audits=useQuery({queryKey:['ai-lifecycle-events'],queryFn:()=>api<Array<{id:string;actor:string;event_type:string;scope_type:string;affected_count:number;created_at:string;note?:string}>>(ROOT+'/events')})
  const baselines=useQuery({queryKey:['ai-lifecycle-baselines'],queryFn:()=>api<Array<{id:string;label:string;created_at:string;created_by:string}>>(ROOT+'/baselines')})
  const editionDocs=useQuery({queryKey:['ai-lifecycle-editions',family],enabled:family.trim().length>0,queryFn:()=>api<{items:Doc[]}>(ROOT+'/editions'+qs({family}))})
  const asOf=useQuery({queryKey:['ai-lifecycle-asof',lookupDate,family],enabled:!!lookupDate,queryFn:()=>api<{items:Doc[];disclaimer:string}>(ROOT+'/as-of'+qs({date:lookupDate,family}))})
  const diff=useQuery({queryKey:['ai-lifecycle-diff',versionLeft,versionRight],enabled:!!versionLeft&&!!versionRight&&versionLeft!==versionRight,
    queryFn:()=>api<{changes:Difference[];warning:string}>(ROOT+'/compare'+qs({from:versionLeft,to:versionRight}))})
  const refresh=()=>{void client.invalidateQueries({queryKey:['ai-lifecycle']});void client.invalidateQueries({queryKey:['ai-lifecycle-cleanup-items']});void client.invalidateQueries({queryKey:['ai-import-batches']});void client.invalidateQueries({queryKey:['ai-import-docs']});void client.invalidateQueries({queryKey:['ai-import-rules']});void client.invalidateQueries({queryKey:['ai-import-segments']});void client.invalidateQueries({queryKey:['knowledge-educational-extracts']})}
  const call=useMutation({mutationFn:({url,body}:{url:string;body:unknown})=>api<Record<string,unknown>>(ROOT+url,{method:'POST',body:JSON.stringify(body)}),onSuccess:()=>{refresh();message.success('عملیات ثبت شد')},onError:e=>message.error(errorText(e))})
  const request=(url:string,body:unknown)=>call.mutateAsync({url,body})
  const singleRecord=async(row:Item,restore:boolean)=>{
    const selectedScope:Scope={type:'ITEM',ids:[row.id]}
    try{
      if(restore){
        await request('/restore',selectedScope)
        setPreview(null);setBackupId('');return
      }
      const impact=await api<{items:number;laws:number;approved:number}>(ROOT+'/preview',{method:'POST',body:JSON.stringify(selectedScope)})
      if(impact.items!==1){message.warning('رکورد انتخابی تغییر کرده است؛ فهرست را تازه‌سازی کن.');refresh();return}
      Modal.confirm({
        title:'آرشیو همین یک رکورد؟',
        content:<Space direction="vertical"><Typography.Text>{row.document_title} — {row.article_label||row.item_key}</Typography.Text>
          <Typography.Text type="secondary">این عمل فقط یک رکورد مرحله ورود AI را از نمایش فعال خارج می‌کند و قابل‌بازیابی است. سابقه بازبینی نگهداری می‌شود.</Typography.Text></Space>,
        okText:'آرشیو همین رکورد',okButtonProps:{danger:true},cancelText:'انصراف',
        onOk:async()=>{await request('/archive',{selection:selectedScope,reason:'آرشیو تک‌رکوردی از مدیریت دانش؛ قابل بازیابی'});setPreview(null);setBackupId('')}
      })
    }catch(e){message.error(errorText(e))}
  }
  const permanentlyDeleteOne=async(r:Item)=>{
    try{
      const selection:Scope={type:'ITEM',ids:[r.id]}
      const impact=await api<{count:number;published:number}>('/api/v1/knowledge/admin/ai-curation/delete/preview',{method:'POST',body:JSON.stringify(selection)})
      if(impact.count!==1){message.warning('رکورد انتخابی در دسترس نیست؛ فهرست را تازه‌سازی کن.');return}
      if(impact.published){message.error('این رکورد منتشرشده است؛ ابتدا انتشارش را لغو کن.');return}
      Modal.confirm({title:'حذف دائمی همین رکورد؟',content:'فقط رکورد موقت AI Import حذف می‌شود؛ نسخه پشتیبان حسابرسی در سرور خودکار ایجاد خواهد شد. این حذف به‌طور عادی قابل بازیابی نیست.',
       okText:'حذف دائمی یک رکورد',okButtonProps:{danger:true},cancelText:'انصراف',
       onOk:async()=>{try{
         await api('/api/v1/knowledge/admin/ai-curation/delete/apply',{method:'POST',body:JSON.stringify({selection,expectedCount:1,confirmation:'DELETE STAGING PERMANENTLY'})})
         message.success('رکورد به‌صورت دائمی حذف شد');refresh()
       }catch(e){message.error(errorText(e))}}
      })
    }catch(e){message.error(errorText(e))}
  }
  const scope:Scope=useMemo(()=>({type:selectionMode,ids:selectionMode==='ALL'||selectionMode==='CATEGORY'?[]:selected,category:selectionMode==='CATEGORY'?category:undefined}),[selected,selectionMode,category])
  const choose=(mode:Scope['type'])=>{setSelectionMode(mode);setSelected([]);setPreview(null);setBackupId('');setBackupSnapshot(null)}
  const startPreview=async()=>{if(selectionMode==='CATEGORY'&&category==='ALL'){message.warning('برای پاک‌سازی دسته‌ای، یک دسته مشخص انتخاب کن');return}
    if(selectionMode!=='ALL'&&selectionMode!=='CATEGORY'&&selected.length===0){message.warning('حداقل یک مورد را انتخاب کن');return}
    try{setPreview(await api<{items:number;laws:number;approved:number}>(ROOT+'/preview',{method:'POST',body:JSON.stringify(scope)}))}catch(e){message.error(errorText(e))}}
  const archive=async(restore=false)=>{if(!restore&&reason.trim().length<8){message.warning('دلیل حذف موقت باید حداقل ۸ نویسه باشد');return}
    try{await request(restore?'/restore':'/archive',restore?scope:{selection:scope,reason});setSelected([]);setPreview(null);setBackupId('')}catch{/* notification above */}}
  const prepareBackup=async()=>{try{const response=await request('/backup',scope);setBackupId(String(response.backupId));setBackupSnapshot(response.snapshot);message.info('نسخه پشتیبان سمت سرور ثبت شد؛ فایل خروجی را هم نگهداری کن.')}catch{/* handled */}}
  const downloadBackup=()=>{if(!backupSnapshot)return;const body=JSON.stringify({backupId,snapshot:backupSnapshot},null,2);const blob=new Blob([body],{type:'application/json;charset=utf-8'});const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download=`sakhtyar-knowledge-backup-${backupId}.json`;document.body.appendChild(a);a.click();a.remove();URL.revokeObjectURL(url)}
  const hardDelete=async()=>{if(deleteConfirm!=='DELETE STAGING PERMANENTLY')return;
    try{await request('/purge',{backupId,confirmation:deleteConfirm});setBackupId('');setBackupSnapshot(null);setDeleteConfirm('');setSelected([]);setPreview(null)}catch{/* handled */}}
  const showDocEdit=(d:Doc)=>{setEditionDoc(d);setFamily(d.regulation_family??'');setEdition(d.edition_label??'');setCalendar(d.calendar_type??'JALALI');setPublicationDate(d.publication_date??'');setEffectiveFrom(d.effective_from??'');setTerritory(d.territorial_scope??'')}
  const saveEdition=async()=>{if(!editionDoc)return;
    try{await api(ROOT+'/documents/'+editionDoc.id+'/edition',{method:'PUT',body:JSON.stringify({regulationFamily:family,editionLabel:edition,calendarType:calendar,publicationDate:publicationDate||null,effectiveFrom:effectiveFrom||null,territorialScope:territory})});message.success('مشخصات نسخه ذخیره شد');setEditionDoc(null);refresh()}catch(e){message.error(errorText(e))}}
  const pagination={current:page,pageSize:size,total:tab==='documents'?documents.data?.total??0:items.data?.total??0,showSizeChanger:true,pageSizeOptions:['10','20','50','100','200','500','1000'],showTotal:(n:number)=>n.toLocaleString('fa-IR')+' رکورد',onChange:(p:number,s:number)=>{setPage(p);setSize(s);setSelected([])}}
  const rowSelect=(mode:Scope['type'])=>({selectedRowKeys:selectionMode===mode?selected:[],onChange:(ids:Key[])=>{setSelectionMode(mode);setSelected(ids.map(String));setPreview(null);setBackupId('')}})
  const categoryOptions=[{value:'ALL',label:'همه دسته‌ها'},{value:'NONLAW',label:'آموزشی و منابع غیرقانونی'},...Object.entries(catNames).map(([value,label])=>({value,label}))]
  const overviewCards=[['بسته‌ها',overview.data?.batches],['اسناد',overview.data?.documents],['قوانین پیشنهادی',overview.data?.rules],['مواد و قطعات',overview.data?.segments],['مطالب آموزشی / فنی',overview.data?.education],['در انتظار بازبینی',overview.data?.pending],['خطاهای باز',overview.data?.issues],['موارد آرشیوشده',overview.data?.archived]] as const
  const guide:Record<string,string>={overview:'نمای کلی و آمار بسته‌ها و جدول صفحه‌بندی‌شده.',documents:'مدیریت اسناد، منشأ و شناسنامه نسخه.',laws:'قوانین پیشنهادی و غیرمنتشرشده.',education:'مطالب آموزشی و فنی با دسته‌بندی مستقل.',issues:'خطاهای ورود و قرنطینه.',cleanup:'آرشیو قابل‌بازیابی، نسخه پشتیبان و حذف کنترل‌شده.',versions:'تاریخچه نسخه اسناد و تفاوت‌ها.',duplicates:'رکوردهای دارای متن یکسان.',audit:'تاریخچه تصمیم‌های ثبت‌شده.'}
  const editor=<Modal open={!!editionDoc} title="شناسنامه نسخه سند" onCancel={()=>setEditionDoc(null)} okText="ذخیره شناسنامه" onOk={()=>void saveEdition()}>
    <Space direction="vertical" style={{width:'100%'}}>
      <Alert type="info" showIcon message="سال شمسی را به‌صورت عنوان نسخه ثبت کن؛ تاریخ‌های دقیق این فرم میلادی ISO هستند و نباید حدس زده شوند."/>
      <Input placeholder="خانواده مقررات، مثلاً طرح تفصیلی تهران" value={family} onChange={e=>setFamily(e.target.value)}/>
      <Input placeholder="عنوان نسخه، مثلاً شهریور ۱۳۹۸" value={edition} onChange={e=>setEdition(e.target.value)}/>
      <Select value={calendar} onChange={setCalendar} options={[{value:'JALALI',label:'تقویم شمسی'},{value:'GREGORIAN',label:'میلادی'},{value:'UNKNOWN',label:'نامشخص'}]}/>
      <Input placeholder="تاریخ انتشار ISO: 2020-01-01 (فقط پس از تأیید)" value={publicationDate} onChange={e=>setPublicationDate(e.target.value)}/>
      <Input placeholder="تاریخ لازم‌الاجرا ISO: 2020-01-01 (فقط پس از تأیید)" value={effectiveFrom} onChange={e=>setEffectiveFrom(e.target.value)}/>
      <Input placeholder="محدوده جغرافیایی و استثناها" value={territory} onChange={e=>setTerritory(e.target.value)}/>
    </Space>
  </Modal>
  return <div className="sakhtyar-lifecycle" dir="rtl">
    <Card className="sakhtyar-curation-main" title={<Space><DatabaseOutlined/>مرکز مدیریت چرخه عمر دانش</Space>}
      extra={<Space wrap><Checkbox checked={withArchived} onChange={e=>{setWithArchived(e.target.checked);setPage(1)}}>نمایش موارد آرشیوشده</Checkbox>
        <Button icon={<ReloadOutlined/>} onClick={refresh}>تازه‌سازی</Button></Space>}>
      <Space wrap style={{marginBottom:9}}><Button size="small" onClick={()=>setHelpOpen(v=>!v)}>راهنمای این بخش</Button>
      <Typography.Text type="secondary">مدیریت داده‌های مرحله ورود AI؛ انتشار مقررات جداگانه انجام می‌شود.</Typography.Text></Space>
      {helpOpen&&<Alert showIcon closable onClose={()=>setHelpOpen(false)} type="info" style={{marginBottom:12}} message="راهنمای مدیریت دانش"
        description={guide[tab]??'مورد مورد نظر را باز کرده و جزئیات و وضعیت آن را بررسی کن.'}/>}

      <Tabs activeKey={tab} onChange={k=>{setTab(k);setHelpOpen(false);setPage(1);setPreview(null);setBackupId('')}} items={[
        {key:'overview',label:'آمار همه بسته‌ها',children:<>
          <Row gutter={[12,12]} className="sakhtyar-curation-kpis">{overviewCards.map(([label,value],index)=><Col xs={12} sm={8} xl={6} xxl={3} key={label}>
            <Card size="small" className={`sakhtyar-curation-kpi sakhtyar-curation-kpi-${index%4}`}><Statistic title={label} value={value??0} loading={overview.isLoading}/>
            <div className="sakhtyar-curation-kpi-bar"/></Card></Col>)}</Row>
          <Row gutter={[12,12]} style={{marginTop:14}}>
            <Col xs={24} lg={12}><Card size="small" title="پیشرفت بازبینی داده‌های استخراج‌شده">
              <Progress percent={Math.max(0,100-Math.round(100*(overview.data?.pending??0)/Math.max(1,(overview.data?.rules??0)+(overview.data?.segments??0))))} strokeColor="#2563eb"/>
              <Typography.Text type="secondary">نسبت رکوردهایی که از انتظار بازبینی خارج شده‌اند؛ نه نسبت اعتبار قانونی.</Typography.Text>
            </Card></Col>
            <Col xs={24} lg={12}><Card size="small" title="سهم منابع آموزشی و فنی از قطعات">
              <Progress percent={Math.round(100*(overview.data?.education??0)/Math.max(1,overview.data?.segments??0))} strokeColor="#0d9488"/>
              <Typography.Text type="secondary">منابع آموزشی و فنی جداگانه از نامزدهای قانونی نگهداری می‌شوند.</Typography.Text>
            </Card></Col>
          </Row>
          <Divider>فهرست همه بسته‌ها</Divider>
          <Input.Search allowClear placeholder="جست‌وجو میان همه نام‌های بسته‌ها" style={{width:330,marginBottom:12}} onSearch={v=>{setPackageSearch(v);setPackagePage(1)}}/>
          <Table<Package> size="small" rowKey="id" loading={packages.isLoading} dataSource={packages.data?.items??[]} scroll={{x:950,y:510}}
            pagination={{current:packagePage,pageSize:packageSize,total:packages.data?.total??0,showSizeChanger:true,pageSizeOptions:['10','20','50','100','200','500','1000'],onChange:(p,s)=>{setPackagePage(p);setPackageSize(s)}}} rowSelection={rowSelect('BATCH')}
            columns={[{title:'بسته',dataIndex:'archive_name',width:270},{title:'زمان ورود',dataIndex:'created_at',render:fmt},{title:'اسناد',dataIndex:'documents'},{title:'قوانین',dataIndex:'rules'},{title:'مواد',dataIndex:'segments'},{title:'خطاها',dataIndex:'issues'},{title:'وضعیت',render:(_,r)=>r.archived_at?<Tag color="orange">آرشیو</Tag>:<Tag color="green">فعال</Tag>}]}/>
        </>},
        {key:'documents',label:'اسناد و طبقه‌بندی',children:<>
          <Space wrap style={{marginBottom:12}}><Select allowClear placeholder="همه بسته‌ها" value={batch||undefined} onChange={v=>{setBatch(v??'');setPage(1)}} style={{width:230}} options={(allPackages.data??[]).map(p=>({value:p.id,label:p.archive_name}))}/>
            <Select value={category} onChange={v=>{setCategory(v);setPage(1)}} style={{width:200}} options={categoryOptions}/>
            <Input.Search allowClear style={{width:240}} placeholder="عنوان سند یا منبع" onSearch={v=>{setSearch(v);setPage(1)}}/>
            <Checkbox checked={withArchived} onChange={e=>setWithArchived(e.target.checked)}>نمایش آرشیو</Checkbox></Space>
          <Space wrap style={{marginBottom:10}}><Typography.Text type="secondary">تفکیک اسناد:</Typography.Text>
            <Button size="small" type={category==='ALL'?'primary':'default'} onClick={()=>{setCategory('ALL');setPage(1)}}>همه</Button>
            <Button size="small" type={category==='LAW'?'primary':'default'} onClick={()=>{setCategory('LAW');setPage(1)}}>قوانین</Button>
            <Button size="small" type={category==='NONLAW'?'primary':'default'} onClick={()=>{setCategory('NONLAW');setPage(1)}}>آموزشی / فنی / مرجع</Button></Space>
          <Table<Doc> size="small" rowKey="id" loading={documents.isLoading} dataSource={documents.data?.items??[]} pagination={pagination} rowSelection={rowSelect('DOCUMENT')} scroll={{x:1200}}
            columns={[{title:'عنوان',dataIndex:'title',width:250},{title:'دسته',dataIndex:'category',render:v=>catNames[String(v)]??String(v)},
              {title:'مرجع فایل',dataIndex:'source_reference',width:250,ellipsis:true},{title:'دریافت PDF',width:140,render:(_,r)=><a href={`/api/v1/knowledge/admin/ai-curation/documents/${r.id}/source`} target="_blank" rel="noreferrer">دانلود فایل اصلی</a>},{title:'بسته',dataIndex:'archive_name',width:170},
              {title:'رکورد',dataIndex:'item_count'},{title:'نسخه',dataIndex:'edition_label'},{title:'وضعیت',render:(_,r)=>r.archived_at?<Tag>آرشیو</Tag>:<Tag color="green">فعال</Tag>},
              {title:'عملیات',render:(_,r)=><Button size="small" onClick={()=>showDocEdit(r)}>تاریخچه / نسخه</Button>}]}/>
        </>},
        {key:'laws',label:'قوانین و مقررات',children:<ItemsTable category="LAW" items={items} pagination={pagination} select={rowSelect('ITEM')} setCategory={setCategory} setPage={setPage} currentCategory={category} setBatch={setBatch} batch={batch} packages={allPackages.data??[]} search={search} setSearch={setSearch}/>},
        {key:'education',label:'آموزشی و منابع فنی',children:<>
          <Alert type="info" showIcon style={{marginBottom:10}} message="منابع آموزشی، فنی و مرجع، خودکار قانون لازم‌الاجرا محسوب نمی‌شوند."/>
          <ItemsTable category="EDUCATION" items={items} pagination={pagination} select={rowSelect('ITEM')} setCategory={setCategory} setPage={setPage} currentCategory={category} setBatch={setBatch} batch={batch} packages={allPackages.data??[]} search={search} setSearch={setSearch}/>
        </>},
        {key:'issues',label:'خطاها و قرنطینه',children:<>
          <Alert type="warning" showIcon style={{marginBottom:12}} message="ردیف مشکل‌دار برای اصلاح نگهداری می‌شود و به قانون معتبر تبدیل نخواهد شد. فایل‌های دیگر بدون توقف ادامه می‌یابند."/>
          <Table<Issue> rowKey="id" size="small" dataSource={issues.data??[]} loading={issues.isLoading} scroll={{x:1100}} pagination={{pageSize:20}} expandable={{expandedRowRender:r=><pre style={{whiteSpace:'pre-wrap',direction:'ltr'}}>{JSON.stringify(r.row_payload,null,2)}</pre>}}
            columns={[{title:'فایل',dataIndex:'origin_file',width:190},{title:'خط',dataIndex:'line_number',width:75},{title:'شناسه',dataIndex:'item_key',width:140},
              {title:'دلیل',dataIndex:'explanation',width:350},{title:'راه‌حل',dataIndex:'suggestion',width:300},
              {title:'وضعیت',dataIndex:'state'},{title:'رسیدگی',render:(_,r)=><Button size="small" disabled={r.state!=='OPEN'} onClick={()=>{setIssueDecision(r);setIssueNote('')}}>ثبت تصمیم</Button>}]}/>
        </>},
        {key:'cleanup',label:<Space><DeleteOutlined/>مدیریت حذف و بازیابی</Space>,children:<>
          <Alert type="warning" showIcon message="حذف قابل‌بازیابی پیش‌فرض است. حذف دائمی مستقیم فقط برای مدیر ADMIN و پس از تأیید جداگانه مجاز است. سرور خودکار پشتیبان حسابرسی تهیه می‌کند؛ گرفتن پشتیبان دستی اختیاری است."/>
          <Space direction="vertical" style={{width:'100%',marginTop:12}}>
            <Select value={selectionMode} onChange={choose} style={{maxWidth:340}} options={[{value:'ITEM',label:'عنوان / رکورد انتخابی'},{value:'DOCUMENT',label:'اسناد انتخابی'},{value:'BATCH',label:'بسته‌های انتخابی'},{value:'CATEGORY',label:'تمام موارد یک دسته'}, {value:'ALL',label:'تمام داده‌های مرحله AI Import'}]}/>
            {selectionMode==='CATEGORY'&&<Select value={category} onChange={setCategory} style={{width:240}} options={categoryOptions.filter(x=>x.value!=='ALL')}/>}
            <Typography.Text type="secondary">برای حذف تک‌رکوردی، از فهرست همین تب جست‌وجو کن و «آرشیو همین رکورد» را بزن؛ نیازی به رفتن به تب دیگر نیست. برای عملیات گروهی می‌توانی ردیف‌ها را همین‌جا انتخاب کنی. انتخاب جاری: {selectionMode==='ALL'?'تمام موارد':selectionMode==='CATEGORY'?'دسته '+(catNames[category]??category):selected.length+' مورد'}</Typography.Text>
            <Divider>انتخاب و آرشیو / بازیابی تک‌رکوردی</Divider>
            <Space wrap style={{width:'100%'}}>
              <Input.Search allowClear value={cleanupDraft} onChange={e=>setCleanupDraft(e.target.value)} onSearch={value=>{setCleanupSearch(value);setCleanupPage(1)}} placeholder="جست‌وجوی عنوان، متن یا شناسه" style={{width:310}}/>
              <Select allowClear value={cleanupBatch||undefined} placeholder="همه بسته‌ها" style={{width:270}} onChange={value=>{setCleanupBatch(value??'');setCleanupPage(1)}} options={(allPackages.data??[]).map(p=>({label:p.archive_name,value:p.id}))}/>
              <Select value={cleanupCategory} style={{width:195}} onChange={value=>{setCleanupCategory(value);setCleanupPage(1)}} options={[{value:'ALL',label:'همه دسته‌ها'},{value:'LAW',label:'قوانین'},{value:'NONLAW',label:'آموزشی و غیرقانونی'},...['EDUCATION','TECHNICAL','REFERENCE','OTHER','UNCLASSIFIED'].map(value=>({value,label:catNames[value]}))]}/>
              <Checkbox checked={cleanupShowArchived} onChange={e=>{setCleanupShowArchived(e.target.checked);setCleanupPage(1)}}>نمایش آرشیوی‌ها برای بازیابی</Checkbox>
            </Space>
            <Table<Item> size="small" rowKey="id" loading={cleanupItems.isFetching} dataSource={cleanupItems.data?.items??[]}
              rowSelection={rowSelect('ITEM')} scroll={{x:1080,y:430}}
              pagination={{current:cleanupPage,pageSize:cleanupSize,total:cleanupItems.data?.total??0,showSizeChanger:true,pageSizeOptions:['10','20','50','100','200','500'],showTotal:n=>`${n.toLocaleString('fa-IR')} رکورد`,onChange:(p,sz)=>{setCleanupPage(p);setCleanupSize(sz)}}}
              columns={[
                {title:'سند',dataIndex:'document_title',width:250,ellipsis:true},
                {title:'عنوان / شناسه',width:190,render:(_,r)=>r.article_label||r.item_key},
                {title:'نوع',dataIndex:'category',width:145,render:v=>catNames[String(v)]??String(v)},
                {title:'بسته',dataIndex:'archive_name',width:180,ellipsis:true},
                {title:'صفحه',width:75,render:(_,r)=>r.page_from},
                {title:'وضعیت',width:100,render:(_,r)=>r.archived_at?<Tag color="orange">آرشیوشده</Tag>:<Tag color="green">فعال</Tag>},
                {title:'عملیات تک‌ردیفی',width:255,fixed:'left' as const,render:(_,r)=><Space wrap><Button size="small" type={r.archived_at?'default':'primary'} danger={!r.archived_at}
                  loading={call.isPending} onClick={()=>void singleRecord(r,!!r.archived_at)}>{r.archived_at?'بازیابی همین رکورد':'آرشیو همین رکورد'}</Button><Button size="small" danger onClick={()=>void permanentlyDeleteOne(r)}>حذف دائمی</Button></Space>}
              ]}/>
            <Typography.Text type="secondary">انتخاب ردیف‌های همین جدول برای پیش‌نمایش گروهی، آرشیو یا تهیه نسخه پشتیبان نیز قابل استفاده است. حذف دائمی فقط پس از پشتیبان‌گیری و تأیید مدیر انجام می‌شود.</Typography.Text>
            <Space wrap><Button onClick={()=>void startPreview()}>محاسبه اثر عملیات</Button><Button onClick={()=>void archive(true)} disabled={!['ALL','CATEGORY'].includes(selectionMode)&&!selected.length}>بازیابی انتخاب‌ها</Button></Space>
            {preview&&<Descriptions bordered size="small" column={2} items={[{key:'n',label:'کل رکوردهای درگیر',children:preview.items},{key:'l',label:'داده‌های دسته قانون',children:preview.laws},{key:'a',label:'قبلاً بازبینی‌شده',children:preview.approved},{key:'s',label:'محدوده',children:'فقط جداول ورود هوشمند AI'}]}/>}
            <Input.TextArea placeholder="دلیل عملیات؛ حداقل ۸ نویسه" value={reason} onChange={e=>setReason(e.target.value)} rows={2}/>
            <Button danger type="primary" disabled={!preview||preview.items===0||reason.trim().length<8} onClick={()=>void archive()}>آرشیو / حذف قابل‌بازیابی</Button>
            <KnowledgeDirectDeletePanel selected={selected} visible={(cleanupItems.data?.items??[]).map(r=>r.id)} refresh={refresh}/>
            <details style={{marginTop:12}}><summary>روش پیشرفته با نسخه پشتیبان دستی (اختیاری)</summary>
            <Divider>حذف دائمی با پشتیبان دستی (روش قدیمی)</Divider>
            <Space wrap><Button icon={<SafetyCertificateOutlined/>} disabled={!preview||preview.items===0} onClick={()=>void prepareBackup()}>۱. تهیه نسخه پشتیبان سمت سرور</Button>
            <Button disabled={!backupSnapshot} onClick={downloadBackup}>۲. دانلود فایل پشتیبان JSON</Button></Space>
            <Input value={deleteConfirm} onChange={e=>setDeleteConfirm(e.target.value)} placeholder="۳. برای تأیید: DELETE STAGING PERMANENTLY"/>
            <Button danger disabled={!backupId||deleteConfirm!=='DELETE STAGING PERMANENTLY'} onClick={()=>Modal.confirm({title:'حذف دائمی و غیرقابل‌بازیابی از جداول فعال',content:'ابتدا مطمئن شو فایل JSON را دانلود کرده‌ای. فقط رکوردهای ورود AI حذف می‌شوند. آیا ادامه می‌دهی؟',okText:'تأیید نهایی',okButtonProps:{danger:true},onOk:()=>hardDelete()})}>۴. حذف دائمی رکوردهای انتخابی</Button>
            </details>
          </Space>
        </>},
        {key:'versions',label:'تاریخچه و تفاوت نسخه‌ها',children:<>
          <Alert type="info" showIcon message="نسخه‌های مقررات مستقل از نسخه انتشار کل پایگاه دانش نگهداری می‌شوند. تاریخ اجرا باید کارشناسی تأیید شود."/>
          <Space wrap style={{marginTop:12,marginBottom:12}}><Input value={family} onChange={e=>setFamily(e.target.value)} placeholder="خانواده مقررات (نام دقیق ثبت‌شده)" style={{width:290}}/>
            <Select style={{width:230}} value={versionLeft||undefined} placeholder="نسخه مبنا" onChange={setVersionLeft} options={(editionDocs.data?.items??[]).map(d=>({value:d.id,label:d.edition_label||d.title}))}/>
            <Select style={{width:230}} value={versionRight||undefined} placeholder="نسخه جدید" onChange={setVersionRight} options={(editionDocs.data?.items??[]).map(d=>({value:d.id,label:d.edition_label||d.title}))}/></Space>
          <Table<Doc> size="small" rowKey="id" pagination={false} dataSource={editionDocs.data?.items??[]} columns={[{title:'نسخه',dataIndex:'edition_label'},{title:'سند',dataIndex:'title'},{title:'انتشار (میلادی)',dataIndex:'publication_date'},{title:'اجرای مقررات (میلادی)',dataIndex:'effective_from'},{title:'محدوده',dataIndex:'territorial_scope'}]}/>
          {diff.isError&&<Alert type="warning" showIcon message="برای مقایسه، دو سند از خانواده یکسان با شناسنامه ثبت‌شده انتخاب کن."/>}
          {diff.data&&<Table<Difference> rowKey="key" pagination={{pageSize:12}} dataSource={diff.data.changes} expandable={{expandedRowRender:r=><Row gutter={12}><Col xs={24} md={12}><Card size="small" title="متن قبلی">{diffHighlight(r.before?.full_text??'',r.after?.full_text??'','before')}</Card></Col><Col xs={24} md={12}><Card size="small" title="متن جدید">{diffHighlight(r.before?.full_text??'',r.after?.full_text??'','after')}</Card></Col></Row>}}
            columns={[{title:'ماده/عنوان',dataIndex:'key'},{title:'تغییر',dataIndex:'status',render:v=><Tag color={v==='ADDED'?'green':v==='REMOVED'?'red':'orange'}>{v==='ADDED'?'اضافه‌شده':v==='REMOVED'?'حذف‌شده':'ویرایش‌شده'}</Tag>},{title:'صفحه قدیم',render:(_,r)=>r.before?.page_from},{title:'صفحه جدید',render:(_,r)=>r.after?.page_from}]}/>}
          <Divider>استعلام تاریخ گذشته</Divider>
          <Space wrap><Input placeholder="تاریخ میلادی دقیق، مثل 2024-01-01" value={asOfDate} onChange={e=>setAsOfDate(e.target.value)} style={{width:240}}/>
            <Button disabled={!/^\d{4}-\d{2}-\d{2}$/.test(asOfDate)} onClick={()=>setLookupDate(asOfDate)}>جست‌وجوی نسخه‌های قابل بررسی در تاریخ</Button></Space>
          {asOf.data&&<Alert style={{marginTop:10}} type="warning" showIcon message="نتیجه استعلام، نامزد کارشناسی است و اعتبار قانونی نهایی نیست" description={asOf.data.disclaimer}/>}
          {asOf.data&&<Table<Doc> rowKey="id" size="small" dataSource={asOf.data.items} pagination={{pageSize:8}} columns={[{title:'سند',dataIndex:'title'},{title:'نسخه',dataIndex:'edition_label'},{title:'شروع اجرا',dataIndex:'effective_from'},{title:'محدوده',dataIndex:'territorial_scope'}]}/>}
          <Divider>نسخه‌های پایه ثبت‌شده</Divider><Space wrap><Input placeholder="عنوان نسخه پایه" value={baseline} onChange={e=>setBaseline(e.target.value)}/>
            <Button onClick={()=>void request('/baselines',{label:baseline,note:'Snapshot of AI import inventory; requires human legal review'}).then(()=>setBaseline('')).catch(()=>{})} disabled={baseline.trim().length<4}>ثبت نسخه پایه آماری</Button></Space>
          <Table size="small" rowKey="id" dataSource={baselines.data??[]} pagination={{pageSize:8}} columns={[{title:'نسخه',dataIndex:'label'},{title:'زمان ثبت',dataIndex:'created_at',render:fmt},{title:'ثبت‌کننده',dataIndex:'created_by'}]}/>
        </>},
{key:'duplicates',label:'داده‌های تکراری',children:<KnowledgeDuplicatesPanel/>},
        {key:'audit',label:'تاریخچه عملیات',children:<Table rowKey="id" dataSource={audits.data??[]} loading={audits.isLoading} pagination={{pageSize:20}} columns={[{title:'عملیات',dataIndex:'event_type'},{title:'کاربر',dataIndex:'actor'},{title:'نوع',dataIndex:'scope_type'},{title:'تعداد',dataIndex:'affected_count'},{title:'تاریخ',dataIndex:'created_at',render:fmt},{title:'توضیح',dataIndex:'note'}]}/>}
      ]}/>
    </Card>
    {editor}
    <Modal open={!!issueDecision} title="رسیدگی به خطای استخراج" onCancel={()=>setIssueDecision(null)} footer={<Space><Button onClick={()=>setIssueDecision(null)}>بستن</Button><Button onClick={()=>void request('/issues/'+issueDecision?.id+'/decision',{decision:'DISMISSED',note:issueNote}).then(()=>setIssueDecision(null)).catch(()=>{})} disabled={issueNote.length<8}>نادیده‌گرفتن با ثبت دلیل</Button><Button type="primary" onClick={()=>void request('/issues/'+issueDecision?.id+'/decision',{decision:'RESOLVED',note:issueNote}).then(()=>setIssueDecision(null)).catch(()=>{})} disabled={issueNote.length<8}>ثبت اصلاح در منبع و بستن خطا</Button></Space>}>
      <Alert type="warning" showIcon message={issueDecision?.explanation} description={issueDecision?.suggestion}/>
      <Typography.Paragraph style={{marginTop:10}}>اگر داده اصلاح شده است، فایل JSONL تصحیح‌شده را دوباره به‌صورت بسته جدید وارد کن. بستن خطا، به‌تنهایی داده نامعتبر را وارد قوانین نمی‌کند.</Typography.Paragraph>
      <Input.TextArea rows={3} value={issueNote} onChange={e=>setIssueNote(e.target.value)} placeholder="توضیح تصمیم و راه‌حل انجام‌شده (حداقل ۸ نویسه)"/>
    </Modal>
  </div>
}

type DataQuery={data?:Listing<Item>;isLoading:boolean}
function ItemsTable({category,items,pagination,select,setCategory,setPage,currentCategory,setBatch,batch,packages,search,setSearch}:{
 category:'LAW'|'EDUCATION';items:DataQuery;pagination:{current:number;pageSize:number;total:number;showSizeChanger:boolean;onChange:(p:number,s:number)=>void};
 select:{selectedRowKeys:string[];onChange:(ids:Key[])=>void};setCategory:(x:string)=>void;setPage:(x:number)=>void;
 currentCategory:string;setBatch:(x:string)=>void;batch:string;packages:Package[];search:string;setSearch:(x:string)=>void
}){
  const visible=(items.data?.items??[]).filter(r=>category==='LAW'?r.category==='LAW':r.category!=='LAW')
  return <>
    <Space wrap style={{marginBottom:12}}>
      <Select allowClear value={batch||undefined} placeholder="همه بسته‌ها" style={{width:235}} onChange={v=>{setBatch(v??'');setPage(1)}} options={packages.map(p=>({value:p.id,label:p.archive_name}))}/>
      <Input.Search allowClear value={search} onChange={e=>{setSearch(e.target.value);setPage(1)}} placeholder="جست‌وجوی متن و عنوان" style={{width:260}}/>
      <Select value={currentCategory} style={{width:190}} onChange={v=>{setCategory(v);setPage(1)}} options={category==='LAW'?[{value:'LAW',label:'قوانین'}]:[{value:'ALL',label:'همه محتوای غیرقانونی'},...['EDUCATION','TECHNICAL','REFERENCE','OTHER','UNCLASSIFIED'].map(v=>({value:v,label:catNames[v]}))]}/>
    </Space>
    <Table<Item> size="small" rowKey="id" loading={items.isLoading} dataSource={visible} pagination={pagination} rowSelection={select} scroll={{x:1050}}
      expandable={{expandedRowRender:r=><Space direction="vertical" style={{width:'100%'}}><Typography.Text strong>متن دقیق استخراج‌شده</Typography.Text><Typography.Paragraph style={{whiteSpace:'pre-wrap'}}>{r.full_text}</Typography.Paragraph><Typography.Text type="secondary">شاهد: {r.evidence_quote}</Typography.Text><Typography.Text type="secondary">منشأ: {r.source_reference}، صفحه {r.page_from}</Typography.Text></Space>}}
      columns={[{title:'سند',dataIndex:'document_title',width:230},{title:'موضوع',dataIndex:'article_label',width:170},{title:'دسته',dataIndex:'category',render:v=>catNames[String(v)]??v},{title:'صفحات',render:(_,r)=>`${r.page_from}–${r.page_to}`},{title:'بسته',dataIndex:'archive_name',width:180},{title:'وضعیت',dataIndex:'review_status',width:150},{title:'متن',dataIndex:'full_text',ellipsis:true,width:320}]}/>
  </>
}
// SAKHTYAR_V55_CURATION
