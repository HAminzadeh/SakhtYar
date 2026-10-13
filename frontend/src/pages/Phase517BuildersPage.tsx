import { Alert, Card, Space, Table, Tag, Typography } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'
type Builder = {id:string; legalName:string; tradeName?:string; city?:string; province?:string; verified:boolean; createdAt?:string}
export function Phase517BuildersPage() {
  const q = useQuery<Builder[]>({queryKey:['phase517-builders'],queryFn:()=>api<Builder[]>('/api/v1/builders'),retry:1})
  return <Space direction="vertical" size="large" style={{width:'100%'}}>
    <div><Typography.Title level={2}>Ù…Ø¯ÛŒØ±ÛŒØª Ø³Ø§Ø²Ù†Ø¯Ú¯Ø§Ù†</Typography.Title><Typography.Paragraph type="secondary">Ø§Ø·Ù„Ø§Ø¹Ø§Øª ÙˆØ§Ù‚Ø¹ÛŒ Ø³Ø§Ø²Ù†Ø¯Ú¯Ø§Ù† Ø«Ø¨Øªâ€ŒØ´Ø¯Ù‡ Ø¯Ø± Ø¨Ú©â€ŒØ§Ù†Ø¯ Ùˆ ÙˆØ¶Ø¹ÛŒØª Ø±Ø§Ø³ØªÛŒâ€ŒØ¢Ø²Ù…Ø§ÛŒÛŒ</Typography.Paragraph></div>
    {q.isError && <Alert type="error" showIcon message="Ø¨Ø§Ø±Ú¯Ø°Ø§Ø±ÛŒ ÙÙ‡Ø±Ø³Øª Ø³Ø§Ø²Ù†Ø¯Ú¯Ø§Ù† Ù†Ø§Ù…ÙˆÙÙ‚ Ø¨ÙˆØ¯" description={String(q.error)}/>}
    <Card loading={q.isLoading}><Table<Builder> rowKey="id" scroll={{x:650}} dataSource={q.data??[]}
      columns={[{title:'Ù†Ø§Ù… Ø­Ù‚ÙˆÙ‚ÛŒ',dataIndex:'legalName'},{title:'Ù†Ø§Ù… ØªØ¬Ø§Ø±ÛŒ',dataIndex:'tradeName'},{title:'Ø´Ù‡Ø±',dataIndex:'city'},
      {title:'Ø§Ø³ØªØ§Ù†',dataIndex:'province'}, {title:'Ø±Ø§Ø³ØªÛŒâ€ŒØ¢Ø²Ù…Ø§ÛŒÛŒ',dataIndex:'verified',render:(v:boolean)=>v?<Tag color="green">ØªØ£ÛŒÛŒØ¯Ø´Ø¯Ù‡</Tag>:<Tag>ØªØ£ÛŒÛŒØ¯Ù†Ø´Ø¯Ù‡</Tag>}]}/></Card>
    <Alert type="info" message="ØªØ£ÛŒÛŒØ¯ Ø³Ø§Ø²Ù†Ø¯Ù‡ Ø¨Ø§ÛŒØ¯ Ø¨Ø±Ø§Ø³Ø§Ø³ Ù…Ø³ØªÙ†Ø¯Ø§Øª Ø§Ù†Ø¬Ø§Ù… Ø´ÙˆØ¯Ø› ÙˆØ¬ÙˆØ¯ Ù¾Ø±ÙˆÙØ§ÛŒÙ„ Ø¨Ù‡ Ù…Ø¹Ù†ÛŒ ØµÙ„Ø§Ø­ÛŒØª ØªØ£ÛŒÛŒØ¯Ø´Ø¯Ù‡ Ù†ÛŒØ³Øª."/>
  </Space>
}