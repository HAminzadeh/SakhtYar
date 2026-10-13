import { Alert, Card, Space, Table, Tag, Typography } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'
type Notification = {id:string; subject:string; channel:string; target:string; status:string; attemptCount:number; lastError?:string; createdAt?:string}
type Rule = {id:string; ruleType?:string; active?:boolean; status?:string; city?:string}
export function Phase517EventsPage() {
  const outbox=useQuery<Notification[]>({queryKey:['phase517-outbox'],queryFn:()=>api<Notification[]>('/api/v1/notifications/outbox?limit=100'),retry:1})
  const rules=useQuery<Rule[]>({queryKey:['phase517-rules'],queryFn:()=>api<Rule[]>('/api/v1/monitoring/rules'),retry:1})
  return <Space direction="vertical" size="large" style={{width:'100%'}}>
    <div><Typography.Title level={2}>Ù…Ø§Ù†ÛŒØªÙˆØ±ÛŒÙ†Ú¯ Ùˆ ØµÙ Ø§Ø¹Ù„Ø§Ù†â€ŒÙ‡Ø§</Typography.Title>
      <Typography.Paragraph type="secondary">Ù†Ù…Ø§ÛŒ Ù…Ø¯ÛŒØ±ÛŒØªØ› ÙˆØ¬ÙˆØ¯ Ù¾ÛŒØ§Ù… Ø¯Ø± Outbox Ø¨Ù‡ Ù…Ø¹Ù†ÛŒ Ø§Ø±Ø³Ø§Ù„ Ù†Ù‡Ø§ÛŒÛŒ Ù†ÛŒØ³Øª.</Typography.Paragraph></div>
    {(outbox.isError || rules.isError) && <Alert type="error" message="Ø¨Ø±Ø®ÛŒ Ø¯Ø§Ø¯Ù‡â€ŒÙ‡Ø§ÛŒ Ø¹Ù…Ù„ÛŒØ§ØªÛŒ Ø¯Ø± Ø¯Ø³ØªØ±Ø³ Ù†ÛŒØ³ØªÙ†Ø¯" description="Ø¯Ø³ØªØ±Ø³ÛŒ USER_MANAGE Ùˆ Ø³Ù„Ø§Ù…Øª Backend Ø±Ø§ Ø¨Ø±Ø±Ø³ÛŒ Ú©Ù†ÛŒØ¯."/>}
    <Card title="Ù‚ÙˆØ§Ù†ÛŒÙ† Ù¾Ø§ÛŒØ´" loading={rules.isLoading}>
      <Table<Rule> rowKey="id" dataSource={rules.data??[]} scroll={{x:580}}
        columns={[{title:'Ø´Ù†Ø§Ø³Ù‡',dataIndex:'id',ellipsis:true},{title:'Ù†ÙˆØ¹',dataIndex:'ruleType'},{title:'Ø´Ù‡Ø±',dataIndex:'city'},
        {title:'ÙˆØ¶Ø¹ÛŒØª',dataIndex:'active',render:(v:boolean|undefined)=>v===undefined?'Ù†Ø§Ù…Ø´Ø®Øµ':<Tag color={v?'green':'default'}>{v?'ÙØ¹Ø§Ù„':'ØºÛŒØ±ÙØ¹Ø§Ù„'}</Tag>}]}/>
    </Card>
    <Card title="Ù¾ÛŒØ§Ù…â€ŒÙ‡Ø§ÛŒ Ø¯Ø± Ø§Ù†ØªØ¸Ø§Ø± ÛŒØ§ Ù†Ø§Ù…ÙˆÙÙ‚" loading={outbox.isLoading}>
      <Table<Notification> rowKey="id" dataSource={outbox.data??[]} scroll={{x:800}} columns={[
        {title:'Ù…ÙˆØ¶ÙˆØ¹',dataIndex:'subject'},{title:'Ú©Ø§Ù†Ø§Ù„',dataIndex:'channel'},
        {title:'ÙˆØ¶Ø¹ÛŒØª',dataIndex:'status'}, {title:'ØªÙ„Ø§Ø´â€ŒÙ‡Ø§',dataIndex:'attemptCount'},
        {title:'Ø¢Ø®Ø±ÛŒÙ† Ø®Ø·Ø§',dataIndex:'lastError'}, {title:'Ø§ÛŒØ¬Ø§Ø¯',dataIndex:'createdAt'}]}/>
    </Card>
    <Alert type="warning" showIcon message="ØªØ§ Ø§ØªØµØ§Ù„ Ùˆ ØªØ£ÛŒÛŒØ¯ ÙˆØ§Ù‚Ø¹ÛŒ Ø³Ø±ÙˆÛŒØ³ Ø§Ø±Ø³Ø§Ù„ØŒ Ù†Ø¨Ø§ÛŒØ¯ Ù¾ÛŒØ§Ù…â€ŒÙ‡Ø§ Ø¨Ù‡â€ŒØµÙˆØ±Øª Ø¯Ø³ØªÛŒ SENT Ø¹Ù„Ø§Ù…Øªâ€ŒÚ¯Ø°Ø§Ø±ÛŒ Ø´ÙˆÙ†Ø¯."/>
  </Space>
}