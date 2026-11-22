import { Alert, Card, Space, Table, Tag, Typography } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'
import { ContextHelp } from '../ui/ContextHelp'
type Builder = {id:string; legalName:string; tradeName?:string; city?:string; province?:string; verified:boolean; createdAt?:string}
export function Phase517BuildersPage() {
  const q = useQuery<Builder[]>({queryKey:['phase517-builders'],queryFn:()=>api<Builder[]>('/api/v1/builders'),retry:1})
  return <Space direction="vertical" size="large" style={{width:'100%'}}>
    <ContextHelp topic="builder" />
    <div><Typography.Title level={2}>مدیریت سازندگان</Typography.Title><Typography.Paragraph type="secondary">اطلاعات واقعی سازندگان ثبت‌شده در بک‌اند و وضعیت راستی‌آزمایی</Typography.Paragraph></div>
    {q.isError && <Alert type="error" showIcon message="بارگذاری فهرست سازندگان ناموفق بود" description={String(q.error)}/>}
    <Card loading={q.isLoading}><Table<Builder> rowKey="id" scroll={{x:650}} dataSource={q.data??[]}
      columns={[{title:'نام حقوقی',dataIndex:'legalName'},{title:'نام تجاری',dataIndex:'tradeName'},{title:'شهر',dataIndex:'city'},
      {title:'استان',dataIndex:'province'}, {title:'راستی‌آزمایی',dataIndex:'verified',render:(v:boolean)=>v?<Tag color="green">تأییدشده</Tag>:<Tag>تأییدنشده</Tag>}]}/></Card>
    <Alert type="info" message="تأیید سازنده باید براساس مستندات انجام شود؛ وجود پروفایل به معنی صلاحیت تأییدشده نیست."/>
  </Space>
}
