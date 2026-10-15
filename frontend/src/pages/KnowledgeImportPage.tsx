import { useMemo, useState } from 'react'
import {
  Alert, App, Button, Card, Checkbox, Col, Descriptions, Input, Modal, Row,
  Select, Space, Statistic, Table, Tabs, Tag, Typography, Upload
} from 'antd'
import {
  CloudUploadOutlined, CopyOutlined, FileTextOutlined,
  ReloadOutlined, SafetyCertificateOutlined, ThunderboltOutlined
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import '../styles/knowledge-import-v53.css'

type Batch = {
  id: string; archive_name: string; archive_format: string; source_ai: string;
  status: string; created_at: string; documents: number; rules: number; segments: number
}
type Doc = {
  id: string; doc_key: string; title: string; category: string;
  source_reference: string; authority?: string; source_sha256?: string
}
type Item = {
  id: string; item_key: string; record_type: 'RULE' | 'SEGMENT'; segment_type: string;
  article_label?: string; full_text: string; evidence_quote: string;
  page_from: number; page_to: number; content_sha256: string;
  review_status: string; review_notes?: string; reviewed_by?: string;
  document_title: string; doc_key: string; category: string; source_reference: string
}
type ReviewEvent = {id: string; decision: string; notes?: string; reviewer: string; created_at: string}
type Instruction = {prompt: string; schemaVersion: string; provider: string}
type Uploaded = {id: string; documents: number; rules: number; segments: number; status: string}
const root = '/api/v1/knowledge/admin/ai-import'
const cat: Record<string,string> = {
  LAW: 'قوانین و ضوابط', EDUCATION: 'آموزشی', TECHNICAL: 'فنی و مهندسی',
  REFERENCE: 'مرجع', OTHER: 'سایر', UNCLASSIFIED: 'دسته‌بندی‌نشده'
}
const kind: Record<string,string> = {
  ARTICLE:'ماده', NOTE:'تبصره', CLAUSE:'بند', PASSAGE:'متن نیازمند بررسی'
}
const statusLabel: Record<string,string> = {
  PENDING_REVIEW:'در انتظار بازبینی', APPROVED:'بازبینی و تأیید شد',
  REJECTED:'رد شد', AWAITING_REVIEW:'منتظر بازبینی'
}

export function KnowledgeImportPage() {
  const {message} = App.useApp()
  const qc = useQueryClient()
  const [activeTab,setActiveTab] = useState('upload')
  const [provider,setProvider] = useState('ChatGPT')
  const [prompt,setPrompt] = useState('')
  const [showPrompt,setShowPrompt] = useState(false)
  const [archive,setArchive] = useState<File | null>(null)
  const [batchId,setBatchId] = useState<string>()
  const [focused,setFocused] = useState<Item | null>(null)
  const [decision,setDecision] = useState('APPROVED')
  const [reviewNotes,setReviewNotes] = useState('')
  const [selectedRuleKeys,setSelectedRuleKeys] = useState<string[]>([])
  const [ruleStatusFilter,setRuleStatusFilter] = useState('PENDING_REVIEW')
  const [documentFilter,setDocumentFilter] = useState<string[]>([])
  const [bulkOpen,setBulkOpen] = useState(false)
  const [bulkScope,setBulkScope] = useState<'SELECTED'|'ALL_PENDING_RULES'>('SELECTED')
  const [bulkDecision,setBulkDecision] = useState<'APPROVED'|'REJECTED'>('APPROVED')
  const [bulkNotes,setBulkNotes] = useState('')
  const [bulkAcknowledged,setBulkAcknowledged] = useState(false)
  const [search,setSearch] = useState('')
  const batches = useQuery({queryKey:['ai-import-batches'],queryFn:()=>api<Batch[]>(`${root}/packages`)})
  const selectedId = batchId ?? batches.data?.[0]?.id
  const batch = batches.data?.find(b=>b.id===selectedId)
  const docs = useQuery({queryKey:['ai-import-docs',selectedId],enabled:!!selectedId,
    queryFn:()=>api<Doc[]>(`${root}/packages/${selectedId}/documents`)})
  const rules = useQuery({queryKey:['ai-import-rules',selectedId],enabled:!!selectedId,
    queryFn:()=>api<Item[]>(`${root}/packages/${selectedId}/items?type=RULE`)})
  const segments = useQuery({queryKey:['ai-import-segments',selectedId],enabled:!!selectedId,
    queryFn:()=>api<Item[]>(`${root}/packages/${selectedId}/items?type=SEGMENT`)})
  const all = useMemo(()=>[...(rules.data??[]),...(segments.data??[])],[rules.data,segments.data])
  const pending = useMemo(()=>all.filter(x=>x.review_status==='PENDING_REVIEW'),[all])
  const pendingLawRules = useMemo(()=>(rules.data??[]).filter(r=>r.review_status==='PENDING_REVIEW'&&r.category==='LAW'),[rules.data])
  const ruleRows = useMemo(()=>(rules.data??[]).filter(r=>(!search||[r.document_title,r.item_key,r.article_label,r.full_text].join(' ').includes(search))&&
    (ruleStatusFilter==='ALL'||r.review_status===ruleStatusFilter)&&
    (!documentFilter.length||documentFilter.includes(r.doc_key))),[rules.data,search,ruleStatusFilter,documentFilter])
  const bulkCount = bulkScope==='SELECTED' ? selectedRuleKeys.length : pendingLawRules.length
  const history = useQuery({queryKey:['ai-import-history',selectedId,focused?.id],enabled:!!selectedId&&!!focused,
    queryFn:()=>api<ReviewEvent[]>(`${root}/packages/${selectedId}/items/${focused!.id}/history`)})
  const showError = (err:unknown) => message.error(err instanceof Error ? err.message : 'عملیات انجام نشد')
  const createPrompt = async () => {
    try {
      const r=await api<Instruction>(`${root}/instructions?provider=${encodeURIComponent(provider)}`)
      setPrompt(r.prompt)
      setShowPrompt(true)
    } catch(e) {showError(e)}
  }
  const upload = useMutation({
    mutationFn:async()=>{
      if(!archive) throw new Error('ابتدا فایل بسته دانش را انتخاب کن.')
      if(archive.size>24*1024*1024) throw new Error('اندازه فایل نباید بیش از ۲۴ مگابایت باشد.')
      const body=new FormData();body.append('file',archive)
      return api<Uploaded>(`${root}/packages`,{method:'POST',body})
    },
    onSuccess: x=>{
      message.success('بسته دانش دریافت و به مرحله بازبینی منتقل شد')
      setBatchId(x.id)
      setArchive(null)
      setActiveTab(x.rules>0?'rules':'segments')
      void qc.invalidateQueries({queryKey:['ai-import-batches']})
      void qc.invalidateQueries({queryKey:['ai-import-docs']})
      void qc.invalidateQueries({queryKey:['ai-import-rules']})
      void qc.invalidateQueries({queryKey:['ai-import-segments']})
    },onError:showError
  })
  const bulkReview = useMutation({
    mutationFn: async () => {
      if (!selectedId) throw new Error('ابتدا بسته دانش را انتخاب کن.')
      if (!bulkAcknowledged) throw new Error('ابتدا تأیید آگاهی از ماهیت بازبینی گروهی را علامت بزن.')
      if (bulkCount===0) throw new Error('هیچ قانون قابل بررسی انتخاب نشده است.')
      if (bulkDecision==='APPROVED' && bulkNotes.trim().length<12)
        throw new Error('برای تأیید، توضیح بررسی منبع باید حداقل ۱۲ نویسه باشد.')
      return api<{updated:number;publication:string}>(`${root}/packages/${selectedId}/items/bulk-review`,{
        method:'POST',body:JSON.stringify({
          scope:bulkScope, itemIds:bulkScope==='SELECTED'?selectedRuleKeys:[],
          decision:bulkDecision, notes:bulkNotes, acknowledged:true, expectedCount:bulkCount
        })
      })
    },
    onSuccess: data => {
      message.success(`تصمیم گروهی برای ${data.updated} قانون ثبت شد؛ انتشار خودکار انجام نشد.`)
      setBulkOpen(false);setSelectedRuleKeys([]);setBulkNotes('');setBulkAcknowledged(false)
      void qc.invalidateQueries({queryKey:['ai-import-rules',selectedId]})
      void qc.invalidateQueries({queryKey:['ai-import-segments',selectedId]})
      void qc.invalidateQueries({queryKey:['ai-import-batches']})
      void qc.invalidateQueries({queryKey:['ai-import-history']})
    },onError:showError
  })
  const openBulk=(scope:'SELECTED'|'ALL_PENDING_RULES',next:'APPROVED'|'REJECTED')=>{
    setBulkScope(scope);setBulkDecision(next);setBulkNotes('');setBulkAcknowledged(false);setBulkOpen(true)
  }
  const review = useMutation({
    mutationFn:async()=>{
      if(!selectedId || !focused) throw new Error('مورد انتخاب نشده است')
      if(decision==='APPROVED' && reviewNotes.trim().length<12)
        throw new Error('برای تأیید، توضیح بررسی منبع باید حداقل ۱۲ نویسه داشته باشد.')
      return api(`${root}/packages/${selectedId}/items/${focused.id}/review`,{
        method:'POST',body:JSON.stringify({decision,notes:reviewNotes})
      })
    },
    onSuccess:()=>{
      message.success('تصمیم بازبینی ثبت شد؛ محتوایی خودکار منتشر نشده است')
      setFocused(null);setReviewNotes('')
      void qc.invalidateQueries({queryKey:['ai-import-rules',selectedId]})
      void qc.invalidateQueries({queryKey:['ai-import-segments',selectedId]})
      void qc.invalidateQueries({queryKey:['ai-import-history']})
      void qc.invalidateQueries({queryKey:['ai-import-batches']})
    },onError:showError
  })
  const view=(r:Item)=>{setFocused(r);setDecision(r.review_status==='REJECTED'?'REJECTED':'APPROVED');setReviewNotes(r.review_notes??'')}
  const itemColumns = [
    {title:'سند',dataIndex:'document_title',key:'document_title',width:200,ellipsis:true},
    {title:'نوع',dataIndex:'segment_type',key:'segment_type',width:100,render:(v:string)=><Tag>{kind[v]??v}</Tag>},
    {title:'شناسه/عنوان',key:'idlabel',width:135,render:(_:unknown,r:Item)=><span>{r.article_label||r.item_key}</span>},
    {title:'صفحه',key:'pages',width:90,render:(_:unknown,r:Item)=>r.page_from===r.page_to?r.page_from:`${r.page_from} تا ${r.page_to}`},
    {title:'متن',dataIndex:'full_text',key:'full_text',width:310,ellipsis:true},
    {title:'وضعیت',dataIndex:'review_status',key:'review_status',width:175,render:(v:string)=><Tag color={v==='APPROVED'?'green':v==='REJECTED'?'red':'gold'}>{statusLabel[v]??v}</Tag>},
    {title:'جزئیات',key:'action',width:135,render:(_:unknown,r:Item)=><Button onClick={()=>view(r)}>مشاهده و بازبینی</Button>}
  ]
  const filtered=(rows:Item[])=>rows.filter(r=>!search ||
    [r.document_title,r.item_key,r.article_label,r.full_text].join(' ').includes(search))
  const tabs=[
    {key:'upload',label:'۱. ساخت دستور و بارگذاری',children:<Space direction="vertical" size="large" style={{width:'100%'}}>
      <Row gutter={[16,16]}>
        <Col xs={24} xl={12}><Card className="sakhtyar-import-step" title={<><ThunderboltOutlined/> ساخت دستور استخراج برای هوش مصنوعی</>}>
          <Typography.Paragraph>دستور استاندارد را بساز، همراه با PDFهای مرجع برای ChatGPT یا هوش مصنوعی دلخواه بفرست و فایل خروجی را دریافت کن.</Typography.Paragraph>
          <Space wrap><Select value={provider} style={{minWidth:190}} onChange={setProvider} options={[
            {value:'ChatGPT',label:'چت‌جی‌پی‌تی'},{value:'Claude',label:'کلود'},
            {value:'Gemini',label:'جمینای'},{value:'Other',label:'سایر مدل‌ها'}]}/>
          <Button type="primary" icon={<CopyOutlined/>} onClick={()=>void createPrompt()}>تولید دستور استخراج</Button></Space>
        </Card></Col>
        <Col xs={24} xl={12}><Card className="sakhtyar-import-step" title={<><CloudUploadOutlined/> بارگذاری بسته دانش</>}>
          <Alert type="info" showIcon message="بسته ورودی باید چهار فایل مشخص JSON/JSONL داشته باشد؛ تمام اطلاعات نامزد بررسی هستند." style={{marginBottom:16}} />
          <Upload.Dragger accept=".zip,.rar" maxCount={1} multiple={false}
            fileList={archive?[{uid:'ai-archive',name:archive.name,status:'done'}]:[]}
            beforeUpload={file=>{setArchive(file);return false}}
            onRemove={()=>{setArchive(null);return true}}>
            <p className="ant-upload-drag-icon"><CloudUploadOutlined/></p>
            <p>فایل ZIP یا RAR را انتخاب یا اینجا رها کن</p>
            <Typography.Text type="secondary">حداکثر ۲۴ مگابایت؛ برای RAR وجود 7-Zip روی سرور لازم است</Typography.Text>
          </Upload.Dragger>
          <Button type="primary" style={{marginTop:14}} loading={upload.isPending} disabled={!archive}
            onClick={()=>upload.mutate()}>بررسی ساختار و ورود به مرحله بازبینی</Button>
        </Card></Col>
      </Row>
      <Alert showIcon type="warning" message="ورود امن و بدون انتشار خودکار"
        description="استنادهای تولیدشده با AI پیش‌فرض معتبر نیستند. در این مرحله داده‌ها فقط وارد جداول موقتِ بازبینی می‌شوند و در قوانین قطعی و موتور تصمیم‌گیری فعال نمی‌شوند." />
    </Space>},
    {key:'rules',label:'۲. قوانین استخراج‌شده',children:<Card title="قوانین واردشده (نیازمند بررسی)">
      <Alert type="info" showIcon style={{marginBottom:12}}
        message="بازبینی گروهی تنها برای قوانینِ اسناد طبقه‌بندی‌شده به‌عنوان «قانون» فعال است."
        description="ابتدا چند نمونه را با «مشاهده و بازبینی» بررسی کن؛ سپس می‌توانی ردیف‌های دلخواه، همه نتایج فیلترشده یا همه قوانین در انتظارِ این بسته را انتخاب کنی. تأیید گروهی تنها وضعیت بازبینی را ثبت می‌کند و هیچ قانونی را خودکار منتشر نمی‌کند."/>
      <Space wrap style={{marginBottom:12}}>
        <Select value={ruleStatusFilter} style={{minWidth:180}} onChange={v=>{setRuleStatusFilter(v);setSelectedRuleKeys([])}}
          options={[{value:'PENDING_REVIEW',label:'در انتظار بازبینی'}, {value:'ALL',label:'همه وضعیت‌ها'},
            {value:'APPROVED',label:'تأییدشده'}, {value:'REJECTED',label:'ردشده'}]}/>
        <Select mode="multiple" allowClear showSearch optionFilterProp="label" value={documentFilter}
          onChange={v=>{setDocumentFilter(v);setSelectedRuleKeys([])}}
          style={{minWidth:300,maxWidth:460}} maxTagCount="responsive" placeholder="انتخاب اسناد برای فیلتر قوانین"
          options={(docs.data??[]).map(d=>({value:d.doc_key,label:d.title}))}/>
        <Button onClick={()=>setSelectedRuleKeys(ruleRows.filter(r=>r.review_status==='PENDING_REVIEW'&&r.category==='LAW').map(r=>r.id))}>
          انتخاب همه نتایج فیلترشده
        </Button>
        <Button onClick={()=>setSelectedRuleKeys([])} disabled={!selectedRuleKeys.length}>لغو انتخاب</Button>
        <Tag>انتخاب‌شده: {selectedRuleKeys.length.toLocaleString('fa-IR')}</Tag>
        <Button type="primary" disabled={!selectedRuleKeys.length} onClick={()=>openBulk('SELECTED','APPROVED')}>
          تأیید انتخاب‌شده‌ها
        </Button>
        <Button danger disabled={!selectedRuleKeys.length} onClick={()=>openBulk('SELECTED','REJECTED')}>
          رد انتخاب‌شده‌ها
        </Button>
        <Button disabled={!pendingLawRules.length} onClick={()=>openBulk('ALL_PENDING_RULES','APPROVED')}>
          تأیید همه قوانین در انتظار این بسته ({pendingLawRules.length.toLocaleString('fa-IR')})
        </Button>
      </Space>
      <Table<Item> rowKey="id" dataSource={ruleRows} loading={rules.isLoading}
        rowSelection={{selectedRowKeys:selectedRuleKeys,onChange:(keys)=>setSelectedRuleKeys(keys.map(String)),
          getCheckboxProps:(r:Item)=>({disabled:r.review_status!=='PENDING_REVIEW'||r.category!=='LAW'})}}
        scroll={{x:1250}} pagination={{pageSize:12,showSizeChanger:true,pageSizeOptions:[12,25,50]}} columns={itemColumns}/>
    </Card>},
    {key:'categories',label:'۳. دسته‌بندی اسناد',children:<Card title="منابع و دسته‌بندی پیشنهادی">
      <Alert type="warning" showIcon style={{marginBottom:12}} message="دسته‌بندی پیشنهادی، جایگزین تأیید اصالت سند نیست." />
      <Table<Doc> rowKey="id" loading={docs.isLoading} dataSource={docs.data??[]} pagination={{pageSize:12}}
        scroll={{x:950}} columns={[
          {title:'عنوان سند',dataIndex:'title',width:240},
          {title:'دسته',dataIndex:'category',width:170,render:(v:string)=><Tag>{cat[v]??v}</Tag>},
          {title:'مرجع منبع',dataIndex:'source_reference',width:300},
          {title:'مرجع صادرکننده',dataIndex:'authority',width:170,render:(v?:string)=>v||'نامشخص'},
          {title:'شناسه',dataIndex:'doc_key',width:120}
        ]}/>
    </Card>},
    {key:'segments',label:'۴. مواد و بندهای استخراج‌شده',children:<Card title="مواد، تبصره‌ها و بندها">
      <Table<Item> rowKey="id" dataSource={filtered(segments.data??[])} loading={segments.isLoading}
        scroll={{x:1130}} pagination={{pageSize:12}} columns={itemColumns}/>
    </Card>},
    {key:'review',label:'۵. بازبینی و تأیید',children:<Space direction="vertical" size="middle" style={{width:'100%'}}>
      <Alert type="info" showIcon message="ثبت تصمیم کارشناسی" description="تأیید این صفحه صرفاً یک تصمیم مستند روی نسخه واردشده است؛ انتقال به قوانین اجرایی و انتشار، مرحله مجزا و نیازمند طراحی کنترل‌های حقوقی خواهد بود." />
      <Row gutter={[12,12]}>
        <Col xs={12} md={8}><Card><Statistic title="منتظر بررسی" value={pending.length}/></Card></Col>
        <Col xs={12} md={8}><Card><Statistic title="تأییدشده برای مرحله بعد" value={all.filter(i=>i.review_status==='APPROVED').length}/></Card></Col>
        <Col xs={24} md={8}><Card><Statistic title="ردشده" value={all.filter(i=>i.review_status==='REJECTED').length}/></Card></Col>
      </Row>
      <Card title="موارد در انتظار تصمیم"><Table<Item> rowKey="id" dataSource={pending} pagination={{pageSize:12}}
        scroll={{x:1120}} columns={itemColumns}/></Card>
    </Space>}
  ]

  return <div className="sakhtyar-import-page sakhtyar-page-stack" dir="rtl">
    <Card className="sakhtyar-import-hero">
      <Space align="start" wrap>
        <FileTextOutlined style={{fontSize:28}}/>
        <div><Typography.Title level={2} style={{margin:0}}>ورود دانش</Typography.Title>
          <Typography.Paragraph style={{margin:'8px 0 0'}}>تولید دستور برای هوش مصنوعی، دریافت بسته ساختاریافته، بررسی مدارک و ثبت تصمیم کارشناسی</Typography.Paragraph></div>
      </Space>
    </Card>
    <Card size="small" className="sakhtyar-import-toolbar">
      <Space wrap style={{width:'100%',justifyContent:'space-between'}}>
        <Space wrap><Typography.Text strong>بسته انتخاب‌شده:</Typography.Text>
          <Select showSearch optionFilterProp="label" placeholder="ابتدا یک بسته دانش بارگذاری کن" style={{minWidth:300,maxWidth:'90vw'}}
            value={selectedId} onChange={v=>{setBatchId(v);setSelectedRuleKeys([])}} options={(batches.data??[]).map(b=>({value:b.id,label:`${b.archive_name} — ${new Date(b.created_at).toLocaleDateString('fa-IR')}`}))}/>
        </Space>
        <Button icon={<ReloadOutlined/>} loading={batches.isFetching} onClick={()=>{void batches.refetch();void docs.refetch();void rules.refetch();void segments.refetch()}}>تازه‌سازی</Button>
      </Space>
      {batch && <Row gutter={[12,12]} style={{marginTop:14}}>
        <Col xs={12} md={6}><Statistic title="تعداد اسناد" value={batch.documents}/></Col>
        <Col xs={12} md={6}><Statistic title="قوانین" value={batch.rules}/></Col>
        <Col xs={12} md={6}><Statistic title="مواد و بندها" value={batch.segments}/></Col>
        <Col xs={12} md={6}><Statistic title="وضعیت" value={statusLabel[batch.status]??batch.status} valueStyle={{fontSize:16}}/></Col>
      </Row>}
    </Card>
    {batches.isError && <Alert type="error" showIcon message="دریافت بسته‌ها ناموفق بود؛ راه‌اندازی Backend و اجرای Migration V51 را بررسی کن."/>}
    <Card className="sakhtyar-import-content">
      {activeTab!=='upload' && <Input.Search allowClear placeholder="جست‌وجو در متن و عنوان" value={search}
        onChange={e=>setSearch(e.target.value)} style={{width:310,maxWidth:'100%',marginBottom:12}}/>}
      <Tabs className="sakhtyar-knowledge-tabs" activeKey={activeTab} onChange={setActiveTab} items={tabs} />
    </Card>
    <Modal title="دستور استخراج ساختاریافته" open={showPrompt} width={950} onCancel={()=>setShowPrompt(false)}
      footer={<Space><Button onClick={()=>setShowPrompt(false)}>بستن</Button>
        <Button type="primary" icon={<CopyOutlined/>} onClick={()=>{
          void navigator.clipboard.writeText(prompt).then(()=>message.success('دستور کپی شد')).catch(()=>message.error('دسترسی کپی وجود ندارد؛ متن را دستی انتخاب کن'))
        }}>کپی دستور</Button></Space>}>
      <Alert style={{marginBottom:12}} type="info" showIcon message="این دستور را همراه اسناد اصلی به هوش مصنوعی بده و ZIP یا RAR تولیدشده را اینجا بارگذاری کن."/>
      <Input.TextArea dir="rtl" readOnly rows={21} value={prompt} style={{lineHeight:2,fontFamily:'inherit'}} />
    </Modal>
    <Modal title="ثبت تصمیم گروهی قوانین" width={760} open={bulkOpen}
      onCancel={()=>!bulkReview.isPending&&setBulkOpen(false)} okText={bulkDecision==='APPROVED'?'تأیید گروهی و ثبت تاریخچه':'رد گروهی و ثبت تاریخچه'}
      okButtonProps={{loading:bulkReview.isPending,disabled:!bulkAcknowledged||bulkCount===0||
        (bulkDecision==='APPROVED'&&bulkNotes.trim().length<12)}}
      onOk={()=>bulkReview.mutate()}>
      <Space direction="vertical" size="middle" style={{width:'100%'}}>
        <Alert type="warning" showIcon message={`تعداد موارد این عملیات: ${bulkCount.toLocaleString('fa-IR')}`}
          description={bulkScope==='ALL_PENDING_RULES'
            ? 'این عملیات روی همه قوانینِ در انتظارِ همین بسته که سندشان در دسته «قانون» است انجام می‌شود؛ فیلتر جست‌وجوی صفحه در این حالت اعمال نمی‌شود.'
            : 'این عملیات فقط روی ردیف‌های تیک‌خورده انجام می‌شود. برای دقت، ابتدا محتوای نمونه‌ها و استنادها را با اسناد اصلی مقایسه کن.'}/>
        <Select style={{width:'100%'}} value={bulkDecision} onChange={setBulkDecision}
          options={[{value:'APPROVED',label:'تأیید کارشناسی برای مرحله بعد'},
            {value:'REJECTED',label:'رد قوانین انتخاب‌شده'}]}/>
        <Input.TextArea rows={4} maxLength={2000} showCount value={bulkNotes} onChange={e=>setBulkNotes(e.target.value)}
          placeholder="روش نمونه‌برداری، اسناد و صفحات کنترل‌شده یا دلیل رد را با دقت بنویس (برای تأیید حداقل ۱۲ نویسه)."/>
        <Checkbox checked={bulkAcknowledged} onChange={e=>setBulkAcknowledged(e.target.checked)}>
          تأیید می‌کنم که مسئولیت این تصمیم گروهی با بازبین است، موارد لازم را بررسی کرده‌ام و این اقدام به‌معنای انتشار یا تأیید قانونی خودکار نیست.
        </Checkbox>
        <Typography.Text type="secondary">تاریخچه مستقل برای هر مورد ثبت می‌شود. هر خطای اعتبارسنجی، کل عملیات گروهی را برگشت می‌دهد.</Typography.Text>
      </Space>
    </Modal>
    <Modal title={focused?.article_label||'بازبینی مستندات واردشده'} width={950} open={!!focused}
      onCancel={()=>setFocused(null)} okText="ثبت تصمیم" okButtonProps={{
        loading:review.isPending,disabled:decision==='APPROVED'&&reviewNotes.trim().length<12}}
      onOk={()=>review.mutate()}>
      {focused && <Space direction="vertical" size="middle" style={{width:'100%'}}>
        <Descriptions bordered size="small" column={{xs:1,md:2}} items={[
          {key:'title',label:'سند',children:focused.document_title,span:2},
          {key:'type',label:'نوع',children:kind[focused.segment_type]??focused.segment_type},
          {key:'page',label:'صفحه',children:`${focused.page_from} تا ${focused.page_to}`},
          {key:'source',label:'مرجع',children:focused.source_reference,span:2},
          {key:'sha',label:'اثر انگشت متن',children:<Typography.Text copyable>{focused.content_sha256}</Typography.Text>,span:2}
        ]}/>
        <Typography.Text strong>متن کامل پیشنهادی هوش مصنوعی</Typography.Text>
        <Card size="small" className="sakhtyar-import-source-text">{focused.full_text}</Card>
        <Alert showIcon type="warning" message="شاهدی که AI معرفی کرده" description={focused.evidence_quote}/>
        <Select style={{width:'100%'}} value={decision} onChange={setDecision} options={[
          {value:'APPROVED',label:'تأیید کارشناسی برای مرحله بعد'},
          {value:'REJECTED',label:'رد به‌دلیل کیفیت یا استناد نامعتبر'},
          {value:'PENDING_REVIEW',label:'بازگرداندن به صف بازبینی'}
        ]}/>
        <Input.TextArea rows={3} maxLength={2000} showCount value={reviewNotes} onChange={e=>setReviewNotes(e.target.value)}
          placeholder="مثال: صفحه ۵ سند اصلی بررسی شد و متن بند با نسخه مرجع مطابقت دارد."/>
        {decision==='APPROVED' && <Alert type={reviewNotes.trim().length>=12?'success':'warning'} showIcon
          message={reviewNotes.trim().length>=12?'توضیح بازبینی برای ثبت کافی است.':'برای تأیید حداقل ۱۲ نویسه توضیح واقعی درباره بررسی منبع بنویس.'}/>}

        <Alert type="info" icon={<SafetyCertificateOutlined/>} showIcon message="تأیید، قانون را خودکار در موتور مقررات منتشر نمی‌کند."/>
        {(history.data??[]).length>0 && <Card size="small" title="تاریخچه تصمیم‌های کارشناسی">
          <Table<ReviewEvent> rowKey="id" size="small" pagination={{pageSize:5}} dataSource={history.data??[]}
            columns={[{title:'تصمیم',dataIndex:'decision',render:(v:string)=>statusLabel[v]??v},
              {title:'بازبین',dataIndex:'reviewer'},
              {title:'تاریخ',dataIndex:'created_at',render:(v:string)=>new Date(v).toLocaleString('fa-IR')},
              {title:'یادداشت',dataIndex:'notes'}]}/>
        </Card>}
      </Space>}
    </Modal>
  </div>
}
