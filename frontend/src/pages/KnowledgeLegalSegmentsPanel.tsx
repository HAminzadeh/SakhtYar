import {Alert, Button, Card, Descriptions, Modal, Space, Table, Tag, Typography} from 'antd'
import {useQuery} from '@tanstack/react-query'
import {useState} from 'react'
import {api} from '../api/client'

type Segment = {
  id: string; document_name: string; ordinal: number; segment_type: string;
  article_label: string; full_text: string; page_from: number; page_to: number;
  review_status: string; qwen_status?: string; qwen_topic?: string;
  qwen_evidence?: string; text_sha256: string; sentence_count?: string
}
type Paged = {items: Segment[]; total: number; page: number; pageSize: number}
const kindLabel: Record<string,string> = {ARTICLE:'ماده',NOTE:'تبصره',CLAUSE:'بند',PASSAGE:'متن فاقد ساختار قانونی'}
const modelLabel: Record<string,string> = {
  GROUNDED_PROPOSAL:'پیشنهاد مدل با شاهد موجود در متن',
  NOT_A_LEGAL_CLAUSE:'نیازمند تعیین ماهیت سند',
  NOT_EVALUATED_LIMIT:'مدل هنوز بررسی نکرده',
  SKIPPED_TOO_LONG:'طول متن زیاد؛ نیازمند بررسی',
  MODEL_ERROR:'مدل پاسخ نداد',
  REJECTED_UNGROUNDED_QUOTE:'نقل‌قول مدل با سند تطبیق نداشت'
}
export function KnowledgeLegalSegmentsPanel({executionId}: {executionId?: string}) {
  const [page,setPage]=useState(1)
  const [focus,setFocus]=useState<Segment | null>(null)
  const query = useQuery({
    queryKey:['knowledge-legal-segments',executionId,page], enabled:!!executionId,
    queryFn:()=>api<Paged>(`/api/v1/knowledge/admin/legal/segments?executionId=${encodeURIComponent(executionId!)}&page=${page}&pageSize=20`)
  })
  return <Space direction="vertical" style={{width:'100%'}} size="middle">
    <Alert type="warning" showIcon message="مواد و بندها فقط نامزد بازبینی هستند"
      description="متن کامل از منبع ذخیره شده؛ برچسب Qwen صرفاً پیشنهاد است. هیچ ماده یا تبصره‌ای با این مرحله به‌صورت خودکار تأیید حقوقی نمی‌شود." />
    {!executionId && <Alert type="info" message="ابتدا یک اجرای استخراج دانش را باز کنید."/>}
    {query.isError && <Alert type="error" showIcon message="خطا در دریافت مواد"
      description={query.error instanceof Error ? query.error.message : 'Migration V50 یا مجوز دسترسی را بررسی کنید.'}/>}
    <Card title="متن کامل مواد، تبصره‌ها و بندهای استخراج‌شده"
      extra={<Button onClick={()=>void query.refetch()} loading={query.isFetching}>تازه‌سازی</Button>}>
      <Table<Segment> rowKey="id" dataSource={query.data?.items??[]} loading={query.isLoading}
        scroll={{x:1050}} pagination={{current:page,pageSize:20,total:query.data?.total??0,showSizeChanger:false,onChange:setPage}}
        columns={[
          {title:'سند',dataIndex:'document_name',width:210,ellipsis:true},
          {title:'نوع',dataIndex:'segment_type',width:175,render:v=><Tag>{kindLabel[v]??v}</Tag>},
          {title:'عنوان',dataIndex:'article_label',width:120,render:v=>v||'—'},
          {title:'صفحه',width:120,render:(_,s)=>s.page_from===s.page_to?String(s.page_from):`${s.page_from}–${s.page_to}`},
          {title:'متن کامل (نمونه)',dataIndex:'full_text',width:310,render:v=><Typography.Text ellipsis style={{maxWidth:300}}>{v}</Typography.Text>},
          {title:'تحلیل مدل',dataIndex:'qwen_status',width:230,render:v=><Tag color={v==='GROUNDED_PROPOSAL'?'blue':'gold'}>{modelLabel[v]??v??'بدون تحلیل'}</Tag>},
          {title:'بازبینی',width:120,render:(_,s)=><Button onClick={()=>setFocus(s)}>مشاهده کامل</Button>}
        ]}/>
    </Card>
    <Modal title={focus?.article_label || 'متن کامل منبع'} width={940} open={!!focus} onCancel={()=>setFocus(null)} footer={null}>
      {focus && <Space direction="vertical" size="middle" style={{width:'100%'}}>
        <Descriptions bordered column={2} size="small" items={[
          {key:'doc',label:'سند',children:focus.document_name,span:2},
          {key:'type',label:'نوع',children:kindLabel[focus.segment_type]??focus.segment_type},
          {key:'pages',label:'صفحه',children:`${focus.page_from} تا ${focus.page_to}`},
          {key:'count',label:'جملات شناسایی‌شده با Hazm',children:focus.sentence_count??'—'},
          {key:'status',label:'وضعیت',children:'در انتظار بازبینی حقوقی'}
        ]}/>
        <Typography.Text strong>متن منبع بدون کوتاه‌سازی</Typography.Text>
        <Typography.Paragraph copyable style={{whiteSpace:'pre-wrap',direction:'rtl',lineHeight:2,border:'1px solid #ddd',padding:18,borderRadius:8,maxHeight:'60vh',overflowY:'auto'}}>
          {focus.full_text}
        </Typography.Paragraph>
        {!!focus.qwen_evidence && <Alert type="info" showIcon message="نقل‌قول پیشنهادی مدل که در متن منبع پیدا شد"
          description={focus.qwen_evidence}/>}
        <Typography.Text type="secondary">این پنل صرفاً برای بررسی متن استخراج‌شده است. تأیید ضابطه در فرآیند مستقل بازبینی کارشناسی انجام می‌شود.</Typography.Text>
      </Space>}
    </Modal>
  </Space>
}
