import { Alert, Button, Card, Col, Form, Input, InputNumber, Row, Space, Statistic, Table, Tag, Typography } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../api/client'
import { ContextHelp } from '../ui/ContextHelp'

type Summary = {
  city?: string; district?: string; sampleCount?: number; medianPricePerM2?: number | null;
  averagePricePerM2?: number | null; changePercent?: number | null;
  currencyCode?: string | null; latestObservedAt?: string | null; freshnessStatus?: string
}
type Point = { day: string; sampleCount: number; medianPricePerM2: number | null; averagePricePerM2: number | null }
type Sample = { id: string; city: string; district?: string; areaM2?: number; pricePerM2?: number; currencyCode?: string; observedAt?: string; sourceCode?: string; sourceUrl?: string; confidence?: number }
type MarketResponse = { generatedAt: string; summary: Summary; trend: Point[]; snapshot?: {id:string;generatedAt:string} | null }
type ComparableResponse = {generatedAt:string; sampleCount:number; comparables:Sample[]; disclaimer:string}

const numberText = (value: number | null | undefined) => value == null ? 'نامشخص' : Number(value).toLocaleString('fa-IR')

export function Phase517MarketPage() {
  const [form] = Form.useForm()
  const [filters, setFilters] = useState({ city: 'تهران', district: '6', days: 30, areaM2: 0 })
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
    <ContextHelp topic="market" />
    <div><Typography.Title level={2}>هوشمندی بازار املاک</Typography.Title>
      <Typography.Paragraph type="secondary">داده‌های ذخیره‌شده در Backend؛ هیچ قیمت یا سیگنال تخمینی ساخته نمی‌شود.</Typography.Paragraph></div>
    <Card title="محدوده جغرافیایی و بازه تحلیل">
      <Form form={form} layout="inline" onFinish={v => setFilters({city:String(v.city).trim(),district:String(v.district??'').trim(),days:Number(v.days??30),areaM2:Number(v.areaM2??0)})}
        initialValues={filters}>
        <Form.Item name="city" label="شهر" rules={[{required:true}]}><Input maxLength={120}/></Form.Item>
        <Form.Item name="district" label="منطقه"><Input maxLength={120}/></Form.Item>
        <Form.Item name="days" label="روز"><InputNumber min={1} max={3650}/></Form.Item>
        <Form.Item name="areaM2" label="متراژ مشابه"><InputNumber min={0}/></Form.Item>
        <Form.Item><Button type="primary" htmlType="submit" loading={market.isFetching}>دریافت تحلیل</Button></Form.Item>
      </Form>
    </Card>
    {market.isError && <Alert type="error" showIcon message="ارتباط با سرویس بازار برقرار نشد" description={String(market.error)} />}
    {s && Number(s.sampleCount ?? 0) === 0 && <Alert type="warning" showIcon message="نمونه معتبر بازار در این بازه وجود ندارد" description="نبود داده به معنی صفر بودن قیمت ملک نیست."/>}
    {s && Number(s.sampleCount ?? 0) > 0 && <>
      <Row gutter={[12,12]}>
        <Col xs={24} md={6}><Card><Statistic title="میانه قیمت مشاهده‌شده هر متر" value={numberText(s.medianPricePerM2)} suffix={s.currencyCode ?? ''}/></Card></Col>
        <Col xs={24} md={6}><Card><Statistic title="میانگین قیمت هر متر" value={numberText(s.averagePricePerM2)} suffix={s.currencyCode ?? ''}/></Card></Col>
        <Col xs={24} md={6}><Card><Statistic title="تعداد نمونه‌ها" value={s.sampleCount ?? 0}/></Card></Col>
        <Col xs={24} md={6}><Card><Statistic title="تغییر نسبت به بازه قبل" value={s.changePercent == null ? 'نامشخص' : `${numberText(s.changePercent)}٪`}/></Card></Col>
      </Row>
      <Alert type="info" showIcon message="منبع و کیفیت تحلیل" description={<Space wrap>
        <Tag>وضعیت تازگی: {s.freshnessStatus ?? 'نامشخص'}</Tag>
        <Tag>آخرین مشاهده: {s.latestObservedAt ?? 'نامشخص'}</Tag>
        <Tag>نسخه Snapshot: {market.data?.snapshot?.id ?? 'منتشر نشده'}</Tag>
        <Tag>تاریخ دریافت: {market.data?.generatedAt ?? 'نامشخص'}</Tag>
      </Space>}/>
      <Card title="روند قیمت بر اساس مشاهدات واقعی">
        <Table<Point> rowKey="day" size="small" pagination={{pageSize:10}} scroll={{x:550}} dataSource={market.data?.trend??[]}
          columns={[{title:'تاریخ',dataIndex:'day'},{title:'تعداد نمونه',dataIndex:'sampleCount'},
          {title:'میانه قیمت هر متر',dataIndex:'medianPricePerM2',render:(v:number|null)=>numberText(v)},
          {title:'میانگین قیمت هر متر',dataIndex:'averagePricePerM2',render:(v:number|null)=>numberText(v)}]}/>
      </Card>
    </>}
    <Card title="نمونه‌های قابل مقایسه" loading={comps.isLoading}>
      {comps.isError && <Alert type="warning" message="دریافت نمونه‌های مشابه ناموفق بود"/>}
      <Table<Sample> rowKey="id" pagination={{pageSize:10}} scroll={{x:850}} dataSource={comps.data?.comparables??[]}
        columns={[{title:'منبع',dataIndex:'sourceCode'}, {title:'منطقه',dataIndex:'district'},
          {title:'متراژ',dataIndex:'areaM2',render:(v:number)=>numberText(v)},
          {title:'قیمت هر متر',dataIndex:'pricePerM2',render:(v:number)=>numberText(v)},
          {title:'ارز',dataIndex:'currencyCode'}, {title:'تاریخ',dataIndex:'observedAt'},
          {title:'شاهد',dataIndex:'sourceUrl',render:(v:string|undefined)=>v?.startsWith('https://')?<a href={v} target="_blank" rel="noreferrer">مشاهده منبع</a>:'ناموجود'}]}/>
      <Typography.Paragraph type="secondary">{comps.data?.disclaimer ?? 'قیمت آگهی لزوماً قیمت معامله نیست. صحت منبع باید بررسی شود.'}</Typography.Paragraph>
    </Card>
  </Space>
}
