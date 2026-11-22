import { Alert, Card, Space, Table, Typography } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'
import { ContextHelp } from '../ui/ContextHelp'
type Descriptor = {type?:string;name?:string;version?:string;provider?:string;timeoutSeconds?:number;capabilities?:unknown[]}
export function Phase517AgentsPage() {
  const q=useQuery<Descriptor[]>({queryKey:['phase517-agents-registry'],queryFn:()=>api<Descriptor[]>('/api/v1/agents/registry'),retry:1})
  const components=useQuery<unknown[]>({queryKey:['phase517-components'],queryFn:()=>api<unknown[]>('/api/v1/intelligence/components'),retry:1})
  return <Space direction="vertical" size="large" style={{width:'100%'}}>
    <ContextHelp topic="ai" />
    <div><Typography.Title level={2}>عامل‌ها و اجزای هوشمندی</Typography.Title><Typography.Paragraph type="secondary">رجیستری واقعی Backend؛ راه‌اندازی Workflow باید طبق تعریف معتبر و مجوز انجام شود.</Typography.Paragraph></div>
    {(q.isError||components.isError)&&<Alert type="warning" showIcon message="دریافت بخشی از رجیستری هوشمندی ناموفق بود"/>}
    <Card title="عامل‌های ثبت‌شده" loading={q.isLoading}>
      <Table<Descriptor> rowKey={(r)=>String(r.type??r.name)} dataSource={Array.isArray(q.data)?q.data:[]} scroll={{x:700}} columns={[
        {title:'عامل',dataIndex:'type'}, {title:'نام',dataIndex:'name'}, {title:'نسخه',dataIndex:'version'},
        {title:'Provider',dataIndex:'provider'}, {title:'مهلت (ثانیه)',dataIndex:'timeoutSeconds'}]}/>
    </Card>
    <Card title="اجزای Workflow" loading={components.isLoading}>
      <pre style={{whiteSpace:'pre-wrap',overflowWrap:'anywhere'}}>{JSON.stringify(components.data??[],null,2)}</pre>
    </Card>
    <Alert type="info" message="تکمیل اجرای چندمرحله‌ای واقعی نیازمند Definition معتبر، داده ورودی، تست بازیابی و تأیید انسانی است."/>
  </Space>
}
