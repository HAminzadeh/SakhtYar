import { BookOutlined, InfoCircleOutlined, SearchOutlined } from '@ant-design/icons'
import { Alert, Button, Card, Collapse, Empty, Input, Space, Tabs, Tag, Typography } from 'antd'
import { useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { helpTopics } from '../ui/helpTopics'
import './HelpCenterPage.css'

const groups = ['شروع کار', 'تنظیمات و امنیت', 'دانش و هوشمندی', 'بازار و مشارکت'] as const

export function HelpCenterPage() {
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const [search, setSearch] = useState('')
  const selectedTopic = helpTopics.find(t => t.key === params.get('topic'))
  const [group, setGroup] = useState<string>(selectedTopic?.group || groups[0])
  const activeGroup = selectedTopic?.group || group
  const visible = useMemo(() => helpTopics.filter(t =>
    t.group === activeGroup && `${t.title} ${t.summary} ${t.steps.map(s => s.title + ' ' + s.detail).join(' ')}`.toLocaleLowerCase('fa-IR')
      .includes(search.trim().toLocaleLowerCase('fa-IR'))
  ), [activeGroup, search])
  const activate = (key: string) => setParams({ topic: key })

  return <div className="sakhtyar-page-stack sy-help" dir="rtl">
    <Card>
      <Space align="start" size="middle" wrap>
        <BookOutlined style={{ fontSize: 30, color: '#2865ed' }} />
        <div>
          <Typography.Title level={2} style={{ marginTop: 0 }}>مرکز آموزش ساخت‌یار</Typography.Title>
          <Typography.Paragraph>راهنماهای کاربردی واقعی برای هر بخش؛ به‌همراه هدف تنظیمات، مراحل اجرا، کنترل خطا و نکات امنیتی.</Typography.Paragraph>
        </div>
      </Space>
      <Input allowClear prefix={<SearchOutlined />} placeholder="جست‌وجو در راهنمای همین گروه..." value={search}
        onChange={e => setSearch(e.target.value)} style={{ maxWidth: 480 }} />
    </Card>
    <Tabs activeKey={activeGroup} onChange={next => { setGroup(next); setParams({}); setSearch('') }}
      items={groups.map(g => ({ key: g, label: g }))} />
    {visible.length === 0 && <Empty description="راهنمایی مطابق عبارت جست‌وجو پیدا نشد" />}
    <div className="sy-help-grid">
      {visible.map(topic => <Card key={topic.key} className="sy-help-card"
        title={<Space><InfoCircleOutlined /><span>{topic.title}</span></Space>}
        extra={<Button type="link" onClick={() => activate(topic.key)}>
          {selectedTopic?.key === topic.key ? 'در حال مطالعه' : 'مشاهده جزئیات'}
        </Button>}>
        <Typography.Paragraph>{topic.summary}</Typography.Paragraph>
        <Space wrap><Tag color="blue">{topic.steps.length} مرحله عملی</Tag><Tag>{topic.group}</Tag></Space>
        {selectedTopic?.key === topic.key && <>
          <Collapse style={{ marginTop: 16 }} defaultActiveKey={[topic.key + '-0']}
            items={topic.steps.map((s, i) => ({
              key: topic.key + '-' + i,
              label: `${i + 1}. ${s.title}`,
              children: <Typography.Paragraph>{s.detail}</Typography.Paragraph>
            }))} />
          {topic.notes.map((note, i) => <Alert key={i} type="info" showIcon style={{ marginTop: 10 }} message={note} />)}
          {topic.route && <Button type="primary" style={{ marginTop: 14 }} onClick={() => navigate(topic.route!)}>
            باز کردن بخش مرتبط: {topic.title}
          </Button>}
        </>}
        {selectedTopic?.key !== topic.key && <Button type="link" onClick={() => activate(topic.key)}>
          مطالعه راهنمای کامل
        </Button>}
      </Card>)}
    </div>
    <Alert type="warning" showIcon message="راهنما جایگزین بررسی حقوقی، امنیتی یا کارشناسی پرونده نیست. برای عملیات حساس از مجوز و منابع معتبر استفاده کنید." />
  </div>
}
