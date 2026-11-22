import { Alert, Card, Space, Table, Tag, Typography } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'
import { ContextHelp } from '../ui/ContextHelp'
type Notification = {id:string; subject:string; channel:string; target:string; status:string; attemptCount:number; lastError?:string; createdAt?:string}
type Rule = {id:string; ruleType?:string; active?:boolean; status?:string; city?:string}
export function Phase517EventsPage() {
  const outbox=useQuery<Notification[]>({queryKey:['phase517-outbox'],queryFn:()=>api<Notification[]>('/api/v1/notifications/outbox?limit=100'),retry:1})
  const rules=useQuery<Rule[]>({queryKey:['phase517-rules'],queryFn:()=>api<Rule[]>('/api/v1/monitoring/rules'),retry:1})
  return <Space direction="vertical" size="large" style={{width:'100%'}}>
    <ContextHelp topic="operations" />
    <div><Typography.Title level={2}>مانیتورینگ و صف اعلان‌ها</Typography.Title>
      <Typography.Paragraph type="secondary">نمای مدیریت؛ وجود پیام در Outbox به معنی ارسال نهایی نیست.</Typography.Paragraph></div>
    {(outbox.isError || rules.isError) && <Alert type="error" message="برخی داده‌های عملیاتی در دسترس نیستند" description="دسترسی USER_MANAGE و سلامت Backend را بررسی کنید."/>}
    <Card title="قوانین پایش" loading={rules.isLoading}>
      <Table<Rule> rowKey="id" dataSource={rules.data??[]} scroll={{x:580}}
        columns={[{title:'شناسه',dataIndex:'id',ellipsis:true},{title:'نوع',dataIndex:'ruleType'},{title:'شهر',dataIndex:'city'},
        {title:'وضعیت',dataIndex:'active',render:(v:boolean|undefined)=>v===undefined?'نامشخص':<Tag color={v?'green':'default'}>{v?'فعال':'غیرفعال'}</Tag>}]}/>
    </Card>
    <Card title="پیام‌های در انتظار یا ناموفق" loading={outbox.isLoading}>
      <Table<Notification> rowKey="id" dataSource={outbox.data??[]} scroll={{x:800}} columns={[
        {title:'موضوع',dataIndex:'subject'},{title:'کانال',dataIndex:'channel'},
        {title:'وضعیت',dataIndex:'status'}, {title:'تلاش‌ها',dataIndex:'attemptCount'},
        {title:'آخرین خطا',dataIndex:'lastError'}, {title:'ایجاد',dataIndex:'createdAt'}]}/>
    </Card>
    <Alert type="warning" showIcon message="تا اتصال و تأیید واقعی سرویس ارسال، نباید پیام‌ها به‌صورت دستی SENT علامت‌گذاری شوند."/>
  </Space>
}
