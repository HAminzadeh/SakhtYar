import { Alert, Card, Space, Table, Typography } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'
type Descriptor = {type?:string;name?:string;version?:string;provider?:string;timeoutSeconds?:number;capabilities?:unknown[]}
export function Phase517AgentsPage() {
  const q=useQuery<Descriptor[]>({queryKey:['phase517-agents-registry'],queryFn:()=>api<Descriptor[]>('/api/v1/agents/registry'),retry:1})
  const components=useQuery<unknown[]>({queryKey:['phase517-components'],queryFn:()=>api<unknown[]>('/api/v1/intelligence/components'),retry:1})
  return <Space direction="vertical" size="large" style={{width:'100%'}}>
    <div><Typography.Title level={2}>Ø¹Ø§Ù…Ù„â€ŒÙ‡Ø§ Ùˆ Ø§Ø¬Ø²Ø§ÛŒ Ù‡ÙˆØ´Ù…Ù†Ø¯ÛŒ</Typography.Title><Typography.Paragraph type="secondary">Ø±Ø¬ÛŒØ³ØªØ±ÛŒ ÙˆØ§Ù‚Ø¹ÛŒ BackendØ› Ø±Ø§Ù‡â€ŒØ§Ù†Ø¯Ø§Ø²ÛŒ Workflow Ø¨Ø§ÛŒØ¯ Ø·Ø¨Ù‚ ØªØ¹Ø±ÛŒÙ Ù…Ø¹ØªØ¨Ø± Ùˆ Ù…Ø¬ÙˆØ² Ø§Ù†Ø¬Ø§Ù… Ø´ÙˆØ¯.</Typography.Paragraph></div>
    {(q.isError||components.isError)&&<Alert type="warning" showIcon message="Ø¯Ø±ÛŒØ§ÙØª Ø¨Ø®Ø´ÛŒ Ø§Ø² Ø±Ø¬ÛŒØ³ØªØ±ÛŒ Ù‡ÙˆØ´Ù…Ù†Ø¯ÛŒ Ù†Ø§Ù…ÙˆÙÙ‚ Ø¨ÙˆØ¯"/>}
    <Card title="Ø¹Ø§Ù…Ù„â€ŒÙ‡Ø§ÛŒ Ø«Ø¨Øªâ€ŒØ´Ø¯Ù‡" loading={q.isLoading}>
      <Table<Descriptor> rowKey={(r)=>String(r.type??r.name)} dataSource={Array.isArray(q.data)?q.data:[]} scroll={{x:700}} columns={[
        {title:'Ø¹Ø§Ù…Ù„',dataIndex:'type'}, {title:'Ù†Ø§Ù…',dataIndex:'name'}, {title:'Ù†Ø³Ø®Ù‡',dataIndex:'version'},
        {title:'Provider',dataIndex:'provider'}, {title:'Ù…Ù‡Ù„Øª (Ø«Ø§Ù†ÛŒÙ‡)',dataIndex:'timeoutSeconds'}]}/>
    </Card>
    <Card title="Ø§Ø¬Ø²Ø§ÛŒ Workflow" loading={components.isLoading}>
      <pre style={{whiteSpace:'pre-wrap',overflowWrap:'anywhere'}}>{JSON.stringify(components.data??[],null,2)}</pre>
    </Card>
    <Alert type="info" message="ØªÚ©Ù…ÛŒÙ„ Ø§Ø¬Ø±Ø§ÛŒ Ú†Ù†Ø¯Ù…Ø±Ø­Ù„Ù‡â€ŒØ§ÛŒ ÙˆØ§Ù‚Ø¹ÛŒ Ù†ÛŒØ§Ø²Ù…Ù†Ø¯ Definition Ù…Ø¹ØªØ¨Ø±ØŒ Ø¯Ø§Ø¯Ù‡ ÙˆØ±ÙˆØ¯ÛŒØŒ ØªØ³Øª Ø¨Ø§Ø²ÛŒØ§Ø¨ÛŒ Ùˆ ØªØ£ÛŒÛŒØ¯ Ø§Ù†Ø³Ø§Ù†ÛŒ Ø§Ø³Øª."/>
  </Space>
}