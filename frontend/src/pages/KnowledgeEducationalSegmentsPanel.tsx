import {Alert, Card, Space, Table, Tag, Typography} from 'antd'
import {useQuery} from '@tanstack/react-query'
import {api} from '../api/client'

type EducationalRow = {
  id: string
  archive_name: string
  document_title: string
  category: string
  source_reference: string
  segment_type: string
  article_label?: string
  full_text: string
  evidence_quote: string
  page_from: number
  page_to: number
  review_status: string
}

export function KnowledgeEducationalSegmentsPanel() {
  const {data, isLoading, isError, refetch} = useQuery({
    queryKey: ['knowledge-educational-extracts'],
    queryFn: () => api<EducationalRow[]>('/api/v1/knowledge/admin/ai-import/educational-items')
  })
  return <Card title="مطالب آموزشی و فنی استخراج‌شده" extra={<Typography.Link onClick={() => void refetch()}>تازه‌سازی</Typography.Link>}>
    <Alert style={{marginBottom:12}} showIcon type="info"
      message="جزوه‌ها و مطالب فنی، قانون نیستند"
      description="این موارد از بسته‌های ساختاریافته AI وارد شده‌اند، اصالت و صحت آن‌ها باید بررسی شود و نباید به موتور اجرای مقررات منتقل شوند."/>
    {isError && <Alert type="error" showIcon message="دریافت مطالب آموزشی ناموفق بود."/>}
    <Table<EducationalRow> rowKey="id" loading={isLoading} dataSource={data ?? []}
      scroll={{x:1250}} pagination={{pageSize:15,showSizeChanger:true}}
      expandable={{expandedRowRender:r=><Space direction="vertical" style={{width:'100%'}}>
        <Typography.Text strong>متن دقیق استخراج‌شده</Typography.Text>
        <Typography.Paragraph style={{whiteSpace:'pre-wrap'}}>{r.full_text}</Typography.Paragraph>
        <Typography.Text type="secondary">شاهد: {r.evidence_quote}</Typography.Text>
        <Typography.Text type="secondary">مرجع: {r.source_reference}</Typography.Text>
      </Space>}}
      columns={[
        {title:'سند',dataIndex:'document_title',width:240,ellipsis:true},
        {title:'دسته',dataIndex:'category',width:130,render:v=><Tag>{v==='EDUCATION'?'آموزشی':'فنی و مهندسی'}</Tag>},
        {title:'بسته',dataIndex:'archive_name',width:180,ellipsis:true},
        {title:'موضوع',dataIndex:'article_label',width:200,ellipsis:true},
        {title:'صفحات',width:95,render:(_,r)=>`${r.page_from}–${r.page_to}`},
        {title:'وضعیت بازبینی',dataIndex:'review_status',width:170,render:v=><Tag color={v==='APPROVED'?'green':v==='REJECTED'?'red':'gold'}>{v}</Tag>},
        {title:'متن',dataIndex:'full_text',ellipsis:true}
      ]}/>
  </Card>
}
