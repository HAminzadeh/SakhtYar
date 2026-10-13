import { Alert, Button, Card, Col, Form, Input, InputNumber, Row, Space, Statistic, Table, Tag, Typography } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../api/client'

type Summary = {
  city?: string; district?: string; sampleCount?: number; medianPricePerM2?: number | null;
  averagePricePerM2?: number | null; changePercent?: number | null;
  currencyCode?: string | null; latestObservedAt?: string | null; freshnessStatus?: string
}
type Point = { day: string; sampleCount: number; medianPricePerM2: number | null; averagePricePerM2: number | null }
type Sample = { id: string; city: string; district?: string; areaM2?: number; pricePerM2?: number; currencyCode?: string; observedAt?: string; sourceCode?: string; sourceUrl?: string; confidence?: number }
type MarketResponse = { generatedAt: string; summary: Summary; trend: Point[]; snapshot?: {id:string;generatedAt:string} | null }
type ComparableResponse = {generatedAt:string; sampleCount:number; comparables:Sample[]; disclaimer:string}

const numberText = (value: number | null | undefined) => value == null ? 'Ù†Ø§Ù…Ø´Ø®Øµ' : Number(value).toLocaleString('fa-IR')

export function Phase517MarketPage() {
  const [form] = Form.useForm()
  const [filters, setFilters] = useState({ city: 'ØªÙ‡Ø±Ø§Ù†', district: '6', days: 30, areaM2: 0 })
  const qs = new URLSearchParams({ city: filters.city, district: filters.district, days: String(filters.days) })
  const qComparable = new URLSearchParams({ city: filters.city, district: filters.district, limit: '20' })
  if (filters.areaM2 > 0) qComparable.set('areaM2', String(filters.areaM2))
  const market = useQuery<MarketResponse>({
    queryKey: ['phase517-market', filters],
    queryFn: () => api<MarketResponse>(`/api/web/v1/public/market?${qs.toString()}`),
    enabled: Boolean(filters.city), retry: 1,
  })
  const comps = useQuery<ComparableResponse>({
    queryKey: ['phase517-comparables', filters],
    queryFn: () => api<ComparableResponse>(`/api/web/v1/market/comparables?${qComparable.toString()}`),
    enabled: Boolean(filters.city), retry: 1,
  })
  const s = market.data?.summary
  return <Space direction="vertical" size="large" style={{width:'100%'}}>
    <div><Typography.Title level={2}>Ù‡ÙˆØ´Ù…Ù†Ø¯ÛŒ Ø¨Ø§Ø²Ø§Ø± Ø§Ù…Ù„Ø§Ú©</Typography.Title>
      <Typography.Paragraph type="secondary">Ø¯Ø§Ø¯Ù‡â€ŒÙ‡Ø§ÛŒ Ø°Ø®ÛŒØ±Ù‡â€ŒØ´Ø¯Ù‡ Ø¯Ø± BackendØ› Ù‡ÛŒÚ† Ù‚ÛŒÙ…Øª ÛŒØ§ Ø³ÛŒÚ¯Ù†Ø§Ù„ ØªØ®Ù…ÛŒÙ†ÛŒ Ø³Ø§Ø®ØªÙ‡ Ù†Ù…ÛŒâ€ŒØ´ÙˆØ¯.</Typography.Paragraph></div>
    <Card title="Ù…Ø­Ø¯ÙˆØ¯Ù‡ Ø¬ØºØ±Ø§ÙÛŒØ§ÛŒÛŒ Ùˆ Ø¨Ø§Ø²Ù‡ ØªØ­Ù„ÛŒÙ„">
      <Form form={form} layout="inline" onFinish={v => setFilters({city:String(v.city).trim(),district:String(v.district??'').trim(),days:Number(v.days??30),areaM2:Number(v.areaM2??0)})}
        initialValues={filters}>
        <Form.Item name="city" label="Ø´Ù‡Ø±" rules={[{required:true}]}><Input maxLength={120}/></Form.Item>
        <Form.Item name="district" label="Ù…Ù†Ø·Ù‚Ù‡"><Input maxLength={120}/></Form.Item>
        <Form.Item name="days" label="Ø±ÙˆØ²"><InputNumber min={1} max={3650}/></Form.Item>
        <Form.Item name="areaM2" label="Ù…ØªØ±Ø§Ú˜ Ù…Ø´Ø§Ø¨Ù‡"><InputNumber min={0}/></Form.Item>
        <Form.Item><Button type="primary" htmlType="submit" loading={market.isFetching}>Ø¯Ø±ÛŒØ§ÙØª ØªØ­Ù„ÛŒÙ„</Button></Form.Item>
      </Form>
    </Card>
    {market.isError && <Alert type="error" showIcon message="Ø§Ø±ØªØ¨Ø§Ø· Ø¨Ø§ Ø³Ø±ÙˆÛŒØ³ Ø¨Ø§Ø²Ø§Ø± Ø¨Ø±Ù‚Ø±Ø§Ø± Ù†Ø´Ø¯" description={String(market.error)} />}
    {s && Number(s.sampleCount ?? 0) === 0 && <Alert type="warning" showIcon message="Ù†Ù…ÙˆÙ†Ù‡ Ù…Ø¹ØªØ¨Ø± Ø¨Ø§Ø²Ø§Ø± Ø¯Ø± Ø§ÛŒÙ† Ø¨Ø§Ø²Ù‡ ÙˆØ¬ÙˆØ¯ Ù†Ø¯Ø§Ø±Ø¯" description="Ù†Ø¨ÙˆØ¯ Ø¯Ø§Ø¯Ù‡ Ø¨Ù‡ Ù…Ø¹Ù†ÛŒ ØµÙØ± Ø¨ÙˆØ¯Ù† Ù‚ÛŒÙ…Øª Ù…Ù„Ú© Ù†ÛŒØ³Øª."/>}
    {s && Number(s.sampleCount ?? 0) > 0 && <>
      <Row gutter={[12,12]}>
        <Col xs={24} md={6}><Card><Statistic title="Ù…ÛŒØ§Ù†Ù‡ Ù‚ÛŒÙ…Øª Ù…Ø´Ø§Ù‡Ø¯Ù‡â€ŒØ´Ø¯Ù‡ Ù‡Ø± Ù…ØªØ±" value={numberText(s.medianPricePerM2)} suffix={s.currencyCode ?? ''}/></Card></Col>
        <Col xs={24} md={6}><Card><Statistic title="Ù…ÛŒØ§Ù†Ú¯ÛŒÙ† Ù‚ÛŒÙ…Øª Ù‡Ø± Ù…ØªØ±" value={numberText(s.averagePricePerM2)} suffix={s.currencyCode ?? ''}/></Card></Col>
        <Col xs={24} md={6}><Card><Statistic title="ØªØ¹Ø¯Ø§Ø¯ Ù†Ù…ÙˆÙ†Ù‡â€ŒÙ‡Ø§" value={s.sampleCount ?? 0}/></Card></Col>
        <Col xs={24} md={6}><Card><Statistic title="ØªØºÛŒÛŒØ± Ù†Ø³Ø¨Øª Ø¨Ù‡ Ø¨Ø§Ø²Ù‡ Ù‚Ø¨Ù„" value={s.changePercent == null ? 'Ù†Ø§Ù…Ø´Ø®Øµ' : `${numberText(s.changePercent)}Ùª`}/></Card></Col>
      </Row>
      <Alert type="info" showIcon message="Ù…Ù†Ø¨Ø¹ Ùˆ Ú©ÛŒÙÛŒØª ØªØ­Ù„ÛŒÙ„" description={<Space wrap>
        <Tag>ÙˆØ¶Ø¹ÛŒØª ØªØ§Ø²Ú¯ÛŒ: {s.freshnessStatus ?? 'Ù†Ø§Ù…Ø´Ø®Øµ'}</Tag>
        <Tag>Ø¢Ø®Ø±ÛŒÙ† Ù…Ø´Ø§Ù‡Ø¯Ù‡: {s.latestObservedAt ?? 'Ù†Ø§Ù…Ø´Ø®Øµ'}</Tag>
        <Tag>Ù†Ø³Ø®Ù‡ Snapshot: {market.data?.snapshot?.id ?? 'Ù…Ù†ØªØ´Ø± Ù†Ø´Ø¯Ù‡'}</Tag>
        <Tag>ØªØ§Ø±ÛŒØ® Ø¯Ø±ÛŒØ§ÙØª: {market.data?.generatedAt ?? 'Ù†Ø§Ù…Ø´Ø®Øµ'}</Tag>
      </Space>}/>
      <Card title="Ø±ÙˆÙ†Ø¯ Ù‚ÛŒÙ…Øª Ø¨Ø± Ø§Ø³Ø§Ø³ Ù…Ø´Ø§Ù‡Ø¯Ø§Øª ÙˆØ§Ù‚Ø¹ÛŒ">
        <Table<Point> rowKey="day" size="small" pagination={{pageSize:10}} scroll={{x:550}} dataSource={market.data?.trend??[]}
          columns={[{title:'ØªØ§Ø±ÛŒØ®',dataIndex:'day'},{title:'ØªØ¹Ø¯Ø§Ø¯ Ù†Ù…ÙˆÙ†Ù‡',dataIndex:'sampleCount'},
          {title:'Ù…ÛŒØ§Ù†Ù‡ Ù‚ÛŒÙ…Øª Ù‡Ø± Ù…ØªØ±',dataIndex:'medianPricePerM2',render:(v:number|null)=>numberText(v)},
          {title:'Ù…ÛŒØ§Ù†Ú¯ÛŒÙ† Ù‚ÛŒÙ…Øª Ù‡Ø± Ù…ØªØ±',dataIndex:'averagePricePerM2',render:(v:number|null)=>numberText(v)}]}/>
      </Card>
    </>}
    <Card title="Ù†Ù…ÙˆÙ†Ù‡â€ŒÙ‡Ø§ÛŒ Ù‚Ø§Ø¨Ù„ Ù…Ù‚Ø§ÛŒØ³Ù‡" loading={comps.isLoading}>
      {comps.isError && <Alert type="warning" message="Ø¯Ø±ÛŒØ§ÙØª Ù†Ù…ÙˆÙ†Ù‡â€ŒÙ‡Ø§ÛŒ Ù…Ø´Ø§Ø¨Ù‡ Ù†Ø§Ù…ÙˆÙÙ‚ Ø¨ÙˆØ¯"/>}
      <Table<Sample> rowKey="id" pagination={{pageSize:10}} scroll={{x:850}} dataSource={comps.data?.comparables??[]}
        columns={[{title:'Ù…Ù†Ø¨Ø¹',dataIndex:'sourceCode'}, {title:'Ù…Ù†Ø·Ù‚Ù‡',dataIndex:'district'},
          {title:'Ù…ØªØ±Ø§Ú˜',dataIndex:'areaM2',render:(v:number)=>numberText(v)},
          {title:'Ù‚ÛŒÙ…Øª Ù‡Ø± Ù…ØªØ±',dataIndex:'pricePerM2',render:(v:number)=>numberText(v)},
          {title:'Ø§Ø±Ø²',dataIndex:'currencyCode'}, {title:'ØªØ§Ø±ÛŒØ®',dataIndex:'observedAt'},
          {title:'Ø´Ø§Ù‡Ø¯',dataIndex:'sourceUrl',render:(v:string|undefined)=>v?.startsWith('https://')?<a href={v} target="_blank" rel="noreferrer">Ù…Ø´Ø§Ù‡Ø¯Ù‡ Ù…Ù†Ø¨Ø¹</a>:'Ù†Ø§Ù…ÙˆØ¬ÙˆØ¯'}]}/>
      <Typography.Paragraph type="secondary">{comps.data?.disclaimer ?? 'Ù‚ÛŒÙ…Øª Ø¢Ú¯Ù‡ÛŒ Ù„Ø²ÙˆÙ…Ø§Ù‹ Ù‚ÛŒÙ…Øª Ù…Ø¹Ø§Ù…Ù„Ù‡ Ù†ÛŒØ³Øª. ØµØ­Øª Ù…Ù†Ø¨Ø¹ Ø¨Ø§ÛŒØ¯ Ø¨Ø±Ø±Ø³ÛŒ Ø´ÙˆØ¯.'}</Typography.Paragraph>
    </Card>
  </Space>
}