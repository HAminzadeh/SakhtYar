import {App, Alert, Button, Card, Descriptions, Input, Modal, Select, Space, Table, Tag, Typography} from 'antd'
import {useMutation, useQuery, useQueryClient} from '@tanstack/react-query'
import {useState} from 'react'
import {api} from '../api/client'

type Category = 'LAW' | 'EDUCATION' | 'TECHNICAL' | 'REFERENCE' | 'OTHER' | 'UNCLASSIFIED'
type DocumentRow = {
  id: string; original_name: string; sha256: string; relative_path: string; detected_title?: string;
  folder_category?: string; page_count?: number; intake_status?: string; category: Category;
  notes?: string; reviewed_by?: string; reviewed_at?: string; extracted_rule_count: number
}
export type RuleRow = {
  id: string; stable_key: string; operator: string; numeric_value: number | null; text_value: string;
  confidence: number; status: string; page_from: number; original_name: string;
  document_id: string; sha256: string; category: Category; decision?: string; reviewer?: string;
  reviewed_at?: string; source_authority?: string; jurisdiction_code?: string
}
type Paged<T> = {items: T[]; total: number; page: number; pageSize: number; classificationReady?: boolean}
const categories: {value: Category; label: string}[] = [
  {value: 'LAW', label: 'قوانین، ضوابط و مصوبات'},
  {value: 'EDUCATION', label: 'آموزش و جزوه'},
  {value: 'TECHNICAL', label: 'فنی و مهندسی'},
  {value: 'REFERENCE', label: 'مرجع و داده پشتیبان'},
  {value: 'OTHER', label: 'سایر'},
  {value: 'UNCLASSIFIED', label: 'طبقه‌بندی‌نشده'},
]
const categoryName = (key?: string) => categories.find(x => x.value === key)?.label ?? (key || '—')
const ruleStatus = (value?: string, decision?: string) =>
  decision === 'APPROVED' && value === 'VERIFIED' ? 'تأیید کارشناسی ثبت‌شده' :
  decision === 'REJECTED' && value === 'REJECTED' ? 'رد کارشناسی ثبت‌شده' :
  value === 'VERIFIED' ? 'VERIFIED بدون سابقه تأیید انسانی' :
  value === 'REJECTED' ? 'REJECTED بدون سابقه رد انسانی' : 'در انتظار بررسی'
const catOptions = [{value:'ALL',label:'همه دسته‌ها'}, ...categories]

export function KnowledgeGovernanceWorkbench({mode, executionId, onReviewRule}: {
  mode: 'documents' | 'rules'; executionId?: string; onReviewRule?: (rule: RuleRow) => void
}) {
  const {message} = App.useApp()
  const qc = useQueryClient()
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [category, setCategory] = useState('ALL')
  const [status, setStatus] = useState('ALL')
  const [input, setInput] = useState('')
  const [search, setSearch] = useState('')
  const [editing, setEditing] = useState<DocumentRow | null>(null)
  const [newCategory, setNewCategory] = useState<Category>('LAW')
  const [notes, setNotes] = useState('')
  const params = new URLSearchParams({page:String(page),pageSize:String(pageSize),category,status,search})
  if (mode === 'rules' && executionId) params.set('executionId',executionId)
  const queryKey = ['knowledge-catalog',mode,page,pageSize,category,status,search,executionId]
  const query = useQuery({
    queryKey,
    queryFn: () => api<Paged<DocumentRow | RuleRow>>(`/api/v1/knowledge/admin/catalog/${mode}?${params}`),
    retry: 1,
  })
  const updateCategory = useMutation({
    mutationFn: ({id,category,notes}: {id:string; category:Category; notes:string}) =>
      api(`/api/v1/knowledge/admin/catalog/documents/${id}/classification`,{
        method:'POST',body:JSON.stringify({category,notes}),
      }),
    onSuccess: () => {
      message.success('دسته‌بندی با نام بازبین و تاریخ ثبت شد')
      setEditing(null)
      void qc.invalidateQueries({queryKey:['knowledge-catalog']})
      void qc.invalidateQueries({queryKey:['kp-governance-pending']})
    },
    onError: err => message.error(err instanceof Error ? err.message : 'خطای ثبت دسته‌بندی'),
  })
  const reload = () => {void query.refetch()}
  const resetPage = () => setPage(1)
  const open = (doc:DocumentRow) => {
    setEditing(doc)
    setNewCategory(doc.category === 'UNCLASSIFIED' ? 'LAW' : doc.category)
    setNotes(doc.notes ?? '')
  }
  const rows = query.data?.items ?? []
  return <Space direction="vertical" size="middle" style={{width:'100%'}}>
    <Alert showIcon type={mode === 'rules' ? 'warning' : 'info'}
      message={mode === 'rules' ? 'عدد استخراج‌شده هنوز حکم معتبر شهرداری نیست' : 'دسته‌بندی باید توسط کاربر مسئول تأیید شود'}
      description={mode === 'rules'
        ? 'عملگر OBSERVED و وضعیت CANDIDATE به معنای شناسایی عدد از متن است. برای تأیید قانون، ابتدا نوع سند را «قوانین و ضوابط» ثبت کنید و سپس صفحه، متن حکم، شرط‌ها، استثناها، تاریخ اعتبار و مرجع را در تب «بازبینی و تأیید» بررسی کنید.'
        : 'دسته‌بندی پیشنهادی پوشه جایگزین تصمیم کارشناس نیست. فایل آموزشی نباید به‌صورت خودکار وارد قوانین اجرایی شود. تغییر دسته‌بندی و نام بازبین در سابقه ذخیره می‌شود.'} />
    {query.data?.classificationReady === false && <Alert showIcon type="warning"
      message="ساختار دسته‌بندی هنوز در پایگاه داده فعال نیست"
      description="فهرست اسناد و قوانین قابل مشاهده است؛ برای ثبت دسته‌بندی باید پس از پایان پردازش‌های فعال، Backend را راه‌اندازی مجدد کرده و اجرای موفق Flyway V47 را بررسی کنید." />}
    <Card title={mode === 'rules' ? 'فهرست قوانین و گزاره‌های استخراج‌شده' : 'فهرست اسناد و دسته‌بندی منبع'}
      extra={<Button onClick={reload} loading={query.isFetching}>تازه‌سازی</Button>}>
      <Space wrap style={{marginBottom:14}}>
        <Input.Search value={input} onChange={e=>setInput(e.target.value)} placeholder="جست‌وجو در سند یا متن"
          style={{width:280}} allowClear onSearch={v=>{setSearch(v);resetPage()}} />
        <Select style={{minWidth:190}} value={category} options={catOptions} onChange={v=>{setCategory(v);resetPage()}} />
        {mode === 'rules' && <Select style={{minWidth:170}} value={status} onChange={v=>{setStatus(v);resetPage()}}
          options={[{value:'ALL',label:'همه وضعیت‌ها'},{value:'CANDIDATE',label:'در انتظار بررسی'},
            {value:'VERIFIED',label:'VERIFIED (با/بدون سابقه بررسی)'},{value:'REJECTED',label:'رد شده'}]}/>}
        <Tag>مجموع: {query.data?.total ?? 0}</Tag>
      </Space>
      {query.isError && <Alert showIcon type="error" style={{marginBottom:12}}
        message="دریافت داده‌ها ناموفق بود"
        description={(query.error instanceof Error ? query.error.message : 'API یا Migration مربوطه هنوز اعمال نشده است.')} />}
      {mode === 'documents' ? <Table<DocumentRow> rowKey="id" loading={query.isLoading}
        dataSource={rows as DocumentRow[]} scroll={{x:1080}}
        pagination={{current:page,pageSize,total:query.data?.total??0,showSizeChanger:true,pageSizeOptions:[10,20,50],
          onChange:(p,s)=>{setPage(p);setPageSize(s)}}}
        columns={[
          {title:'نام سند',dataIndex:'original_name',width:230,ellipsis:true},
          {title:'مسیر منبع',dataIndex:'relative_path',ellipsis:true,width:255},
          {title:'دسته تأییدشده',dataIndex:'category',width:205,render:v=><Tag color={v==='LAW'?'green':v==='UNCLASSIFIED'?'orange':'blue'}>{categoryName(v)}</Tag>},
          {title:'گروه پوشه',dataIndex:'folder_category',width:140,ellipsis:true},
          {title:'صفحات',dataIndex:'page_count',width:75},
          {title:'گزاره‌های استخراج‌شده',dataIndex:'extracted_rule_count',width:150},
          {title:'بازبین',dataIndex:'reviewed_by',width:145,render:v=>v??'—'},
          {title:'عملیات',width:130,render:(_,d)=><Button disabled={query.data?.classificationReady === false} onClick={()=>open(d)}>بررسی دسته</Button>},
        ]} expandable={{expandedRowRender:d=><Descriptions size="small" column={1} bordered
          items={[{key:'hash',label:'SHA-256',children:<Typography.Text copyable>{d.sha256}</Typography.Text>},
            {key:'path',label:'مسیر دقیق',children:d.relative_path},
            {key:'notes',label:'یادداشت بررسی',children:d.notes || '—'},
            {key:'date',label:'تاریخ دسته‌بندی',children:d.reviewed_at || '—'}]}/>}} />
      : <Table<RuleRow> rowKey="id" loading={query.isLoading} dataSource={rows as RuleRow[]}
        scroll={{x:1180}} pagination={{current:page,pageSize,total:query.data?.total??0,
          showSizeChanger:true,pageSizeOptions:[10,20,50],onChange:(p,s)=>{setPage(p);setPageSize(s)}}}
        columns={[
          {title:'شناسه گزاره',dataIndex:'stable_key',width:210,ellipsis:true},
          {title:'سند',dataIndex:'original_name',width:230,ellipsis:true},
          {title:'دسته سند',dataIndex:'category',width:180,render:v=><Tag>{categoryName(v)}</Tag>},
          {title:'صفحه PDF',dataIndex:'page_from',width:100},
          {title:'عدد',dataIndex:'numeric_value',width:105,render:v=>v??'—'},
          {title:'عملگر',dataIndex:'operator',width:105},
          {title:'وضعیت',dataIndex:'status',width:210,render:(_,r)=><Tag color={r.decision==='APPROVED' && r.status==='VERIFIED'?'green':r.decision==='REJECTED' && r.status==='REJECTED'?'red':'gold'}>{ruleStatus(r.status,r.decision)}</Tag>},
          {title:'بازبین',dataIndex:'reviewer',width:140,render:v=>v??'—'},
          {title:'بررسی',width:130,render:(_,r)=><Button size="small" disabled={r.status !== 'CANDIDATE' || r.category !== 'LAW'} onClick={()=>onReviewRule?.(r)}>بازبینی</Button>},
        ]} expandable={{expandedRowRender:r=><Space direction="vertical" style={{width:'100%'}}>
          <Typography.Text strong>متن شواهد استخراج‌شده:</Typography.Text>
          <Typography.Paragraph copyable style={{whiteSpace:'pre-wrap'}}>{r.text_value || 'شاهد متنی ثبت نشده است.'}</Typography.Paragraph>
          <Descriptions size="small" bordered column={2} items={[
            {key:'document',label:'سند',children:r.original_name},
            {key:'page',label:'صفحه',children:r.page_from},
            {key:'confidence',label:'امتیاز استخراج',children:r.confidence ?? '—'},
            {key:'jurisdiction',label:'محدوده اعتبار',children:r.jurisdiction_code ?? 'تأیید نشده'},
            {key:'sha',label:'SHA-256',span:2,children:<Typography.Text copyable>{r.sha256}</Typography.Text>},
          ]}/>
          {r.category !== 'LAW' && <Alert type="warning" showIcon
            message="این سند هنوز در دسته قوانین تأیید نشده و نباید به‌عنوان ضابطه اجرایی تصویب شود." />}
        </Space>}} />}
    </Card>
    <Modal title="تعیین دسته‌بندی سند" open={!!editing} onCancel={()=>setEditing(null)}
      okText="ثبت تصمیم دسته‌بندی" okButtonProps={{loading:updateCategory.isPending}}
      onOk={()=>{
        if(newCategory==='LAW' && notes.trim().length<12){message.error('برای اسناد قانونی، توضیح و مرجع بررسی (حداقل ۱۲ کاراکتر) الزامی است.');return}
        if(editing)updateCategory.mutate({id:editing.id,category:newCategory,notes})
      }}>
      <Typography.Paragraph>{editing?.original_name}</Typography.Paragraph>
      <Typography.Paragraph type="secondary">برای انتخاب «قانون» باید مرجع رسمی سند و ماهیت حقوقی آن را مستقلاً بررسی کرده باشید. این اقدام، به‌خودی‌خود هیچ قانون استخراج‌شده‌ای را تأیید نمی‌کند.</Typography.Paragraph>
      <Select style={{width:'100%'}} options={categories.filter(c=>c.value!=='UNCLASSIFIED')}
        value={newCategory} onChange={setNewCategory}/>
      <Input.TextArea style={{marginTop:12}} rows={3} maxLength={2000} showCount
        placeholder="مبنای طبقه‌بندی، مرجع، تاریخ و توضیح بازبین" value={notes} onChange={e=>setNotes(e.target.value)}/>
    </Modal>
  </Space>
}
