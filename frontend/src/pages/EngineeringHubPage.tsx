import { useMemo, useState } from 'react'
import {
  Alert,
  Button,
  Card,
  Col,
  Drawer,
  Empty,
  Input,
  List,
  Row,
  Select,
  Space,
  Statistic,
  Steps,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd'
import {
  AuditOutlined,
  BookOutlined,
  CheckCircleOutlined,
  CopyOutlined,
  FileTextOutlined,
  InfoCircleOutlined,
  RobotOutlined,
  SafetyOutlined,
  SearchOutlined,
  SettingOutlined,
  ToolOutlined,
} from '@ant-design/icons'
import { hubCatalog, type HubEntry, type HubWorkflow } from '../data/engineeringCatalog'
import '../styles/engineering-hub.css'

const { Title, Paragraph, Text } = Typography
const allEntries: HubEntry[] = [...hubCatalog.agents, ...hubCatalog.skills]
const categories = [...new Map(allEntries.map(item => [item.category, item.categoryLabel])).entries()]
const tuned = allEntries.filter(item => item.readiness === 'adapted')

function CopyPrompt({ prompt }: { prompt: string }) {
  const [busy, setBusy] = useState(false)
  const copy = async () => {
    setBusy(true)
    try {
      if (!navigator.clipboard?.writeText) {
        message.warning('کپی خودکار در این مرورگر در دسترس نیست؛ لطفاً متن را دستی انتخاب و کپی کنید.')
        return
      }
      await navigator.clipboard.writeText(prompt)
      message.success('متن درخواست کپی شد')
    } catch {
      message.warning('کپی خودکار انجام نشد؛ متن را دستی انتخاب و کپی کنید.')
    } finally {
      setBusy(false)
    }
  }
  return <div className="sy-eh-prompt">
    <Text className="sy-eh-prompt-text">{prompt}</Text>
    <Button onClick={copy} loading={busy} icon={<CopyOutlined />} size="small">کپی درخواست</Button>
  </div>
}

function EntryCollection({ kind, onOpen }: { kind: 'agent' | 'skill'; onOpen: (entry: HubEntry) => void }) {
  const [query, setQuery] = useState('')
  const [category, setCategory] = useState('all')
  const [maturity, setMaturity] = useState('all')
  const source = kind === 'agent' ? hubCatalog.agents : hubCatalog.skills
  const visible = useMemo(() => source.filter(item => {
    const match = `${item.slug} ${item.title} ${item.description} ${item.categoryLabel}`.toLocaleLowerCase('fa-IR')
    return (category === 'all' || item.category === category)
      && (maturity === 'all' || item.readiness === maturity)
      && match.includes(query.trim().toLocaleLowerCase('fa-IR'))
  }), [source, query, category, maturity])
  return <Card className="sy-eh-section" bordered={false}>
    <div className="sy-eh-toolbar">
      <Input
        allowClear
        prefix={<SearchOutlined />}
        aria-label="جست‌وجوی قابلیت‌ها"
        placeholder={kind === 'agent' ? 'جست‌وجوی نقش، نام یا کاربرد Agent…' : 'جست‌وجوی روش، نام یا کاربرد Skill…'}
        value={query}
        onChange={e => setQuery(e.target.value)}
      />
      <Select
        aria-label="فیلتر دسته تخصصی"
        value={category}
        onChange={setCategory}
        options={[{ value: 'all', label: 'تمام دسته‌ها' }, ...categories.map(([value, label]) => ({ value, label }))]}
      />
      <Select
        aria-label="فیلتر وضعیت آماده‌سازی"
        value={maturity}
        onChange={setMaturity}
        options={[
          { value: 'all', label: 'تمام سطوح' },
          { value: 'adapted', label: 'سفارشی‌شده' },
          { value: 'catalog', label: 'الگوی مقدماتی' },
        ]}
      />
    </div>
    <Text type="secondary">{visible.length.toLocaleString('fa-IR')} مورد از {source.length.toLocaleString('fa-IR')} مورد — برای جزئیات روی کارت کلیک کنید.</Text>
    <List
      className="sy-eh-list"
      dataSource={visible}
      locale={{ emptyText: <Empty description="موردی با این فیلتر یافت نشد" /> }}
      pagination={visible.length > 12 ? { pageSize: 12, showSizeChanger: false, align: 'center' } : false}
      renderItem={item => <List.Item key={item.id}>
        <button type="button" className="sy-eh-entry" onClick={() => onOpen(item)} aria-label={`مشاهده راهنمای ${item.slug}`}>
          <div className="sy-eh-entry-head">
            <span className="sy-eh-entry-icon">{kind === 'agent' ? <RobotOutlined /> : <BookOutlined />}</span>
            <span className="sy-eh-entry-name">
              <strong>{item.title}</strong>
              <code dir="ltr">{item.slug}</code>
            </span>
            <Tag color={item.readiness === 'adapted' ? 'green' : 'default'}>
              {item.readiness === 'adapted' ? 'سفارشی‌شده' : 'الگوی مقدماتی'}
            </Tag>
          </div>
          <Paragraph ellipsis={{ rows: 2 }} className="sy-eh-entry-desc">{item.description}</Paragraph>
          <div className="sy-eh-entry-foot"><span>{item.categoryLabel}</span><span>مشاهده مراحل ←</span></div>
        </button>
      </List.Item>}
    />
  </Card>
}

function WorkflowGallery() {
  const [selected, setSelected] = useState<HubWorkflow>(hubCatalog.workflows[0])
  return <Row gutter={[16, 16]}>
    <Col xs={24} lg={8}>
      <Space direction="vertical" size="small" style={{ width: '100%' }}>
        {hubCatalog.workflows.map(flow => <button
          key={flow.id}
          className={`sy-eh-workflow-option ${selected.id === flow.id ? 'is-selected' : ''}`}
          type="button"
          onClick={() => setSelected(flow)}
        >
          <strong>{flow.title}</strong>
          <span>{flow.summary}</span>
        </button>)}
      </Space>
    </Col>
    <Col xs={24} lg={16}>
      <Card className="sy-eh-section sy-eh-workflow-detail" bordered={false}>
        <Space wrap><Tag color="blue">{selected.tag}</Tag><Text type="secondary">گردش‌کار راهنما؛ بدون اجرای خودکار</Text></Space>
        <Title level={4}>{selected.title}</Title>
        <Paragraph>{selected.summary}</Paragraph>
        <Steps direction="vertical" size="small" items={selected.steps.map(s => ({ title: s }))} />
        <Paragraph><Text strong>Agentهای پیشنهادی:</Text> {selected.agents.map(a => <Tag key={a}>{a}</Tag>)}</Paragraph>
        <Paragraph><Text strong>Skillهای مرتبط:</Text> {selected.skills.map(a => <Tag key={a}>{a}</Tag>)}</Paragraph>
        <Title level={5}>نمونه درخواست برای ChatGPT</Title>
        <CopyPrompt prompt={selected.example} />
      </Card>
    </Col>
  </Row>
}

export function EngineeringHubPage() {
  const [openEntry, setOpenEntry] = useState<HubEntry | null>(null)
  return <div className="sy-eh-page" dir="rtl">
    <section className="sy-eh-hero">
      <div className="sy-eh-hero-mark"><ToolOutlined /></div>
      <div className="sy-eh-hero-copy">
        <div className="sy-eh-kicker">SAKHTYAR · AI ENGINEERING HUB</div>
        <Title level={2}>مرکز مهندسی هوشمند</Title>
        <Paragraph>راهنمای یکپارچه نقش‌ها، روش‌ها و گردش‌کارهای توسعه ساخت‌یار — سفارشی‌شده برای استفاده مستقیم در گفتگوهای ChatGPT.</Paragraph>
        <Space wrap><Tag color="cyan">بدون وابستگی به Claude، Codex و MCP</Tag><Tag>نسخه {hubCatalog.version}</Tag><Tag>فقط‌خواندنی</Tag></Space>
      </div>
    </section>

    <Row gutter={[14, 14]} className="sy-eh-metrics">
      <Col xs={12} md={6}><Card bordered={false}><Statistic title="Agentهای فهرست" value={hubCatalog.agents.length} prefix={<RobotOutlined />} /></Card></Col>
      <Col xs={12} md={6}><Card bordered={false}><Statistic title="Skillهای فهرست" value={hubCatalog.skills.length} prefix={<BookOutlined />} /></Card></Col>
      <Col xs={12} md={6}><Card bordered={false}><Statistic title="سفارشی‌سازی‌های آماده" value={tuned.length} prefix={<CheckCircleOutlined />} /></Card></Col>
      <Col xs={12} md={6}><Card bordered={false}><Statistic title="گردش‌کارهای نمونه" value={hubCatalog.workflows.length} prefix={<AuditOutlined />} /></Card></Col>
    </Row>

    <Alert className="sy-eh-boundary" showIcon type="info" message="این بخش موتور اجرای Agent نیست."
      description="Agentها و Skillها روش‌های متنی قابل استفاده توسط ChatGPT هستند. فهرست‌ شدن یک مورد به معنای فعال‌بودن کامل آن نیست. برای استفاده در گفتگوهای بعدی، باید دستورالعمل‌های این ماژول را به ChatGPT Project منتقل کنید." />

    <Tabs className="sy-eh-tabs" defaultActiveKey="overview" items={[
      { key: 'overview', label: <span><InfoCircleOutlined /> معرفی</span>, children: <Row gutter={[14,14]}>
        <Col xs={24} lg={14}><Card bordered={false} className="sy-eh-section">
          <Title level={4}>از این مرکز چگونه استفاده کنیم؟</Title>
          <Steps direction="vertical" size="small" items={[
            { title: 'نوع مسئله را انتخاب کن', description: 'نیاز توسعه، اشکال، امنیت، UI یا کیفیت دانش را مشخص کن.', status: 'finish' },
            { title: 'Agent و Skill مناسب را پیدا کن', description: 'از جست‌وجو، فیلتر و راهنمای مورد استفاده کمک بگیر.', status: 'wait' },
            { title: 'نمونه درخواست را به ChatGPT بده', description: 'متن را کپی و همراه با فایل‌های واقعی و معیار پذیرش ارسال کن.', status: 'wait' },
            { title: 'نتیجه را با شواهد تأیید کن', description: 'برنامه و پیش‌نویس از اجرای موفق build، تست یا انتشار جداست.', status: 'wait' },
          ]} />
        </Card></Col>
        <Col xs={24} lg={10}><Card bordered={false} className="sy-eh-section">
          <Title level={4}>محدوده‌های مجزا</Title>
          <Paragraph><Text strong>مرکز مهندسی:</Text> راهنمای توسعه سورس؛ فقط سند و محتوای نمایشی.</Paragraph>
          <Paragraph><Text strong>عامل‌های عملیاتی سامانه:</Text> سرویس‌های موجود در Backend که برای فرآیندهای تجاری محصول تعریف شده‌اند.</Paragraph>
          <Alert type="warning" showIcon message="این دو مجموعه مستقل هستند" description="این ماژول هیچ API، جدول دیتابیس، Job یا مجوز جدیدی ایجاد نمی‌کند." />
          <Paragraph style={{ marginTop: 16 }}><Text strong>وضعیت بلوغ:</Text> «سفارشی‌شده» یعنی متن متناسب با ساخت‌یار بازنویسی شده؛ «الگوی مقدماتی» فقط نقطه شروع و نیازمند تکمیل است.</Paragraph>
        </Card></Col>
      </Row> },
      { key: 'agents', label: <span><RobotOutlined /> Agentها</span>, children: <EntryCollection kind="agent" onOpen={setOpenEntry} /> },
      { key: 'skills', label: <span><BookOutlined /> Skillها</span>, children: <EntryCollection kind="skill" onOpen={setOpenEntry} /> },
      { key: 'workflows', label: <span><AuditOutlined /> گردش‌کارها</span>, children: <WorkflowGallery /> },
      { key: 'setup', label: <span><SettingOutlined /> راه‌اندازی</span>, children: <Card bordered={false} className="sy-eh-section">
        <Title level={4}>فعال‌سازی در ChatGPT Project</Title>
        <Alert showIcon type="success" message="به Claude یا سرویس هوش مصنوعی جداگانه نیاز ندارید" description="این روش از تحلیل متنی ChatGPT استفاده می‌کند؛ فایل‌های مرجع را باید در گفتگوی مربوط در دسترس قرار دهید." />
        <Steps style={{ marginTop: 24 }} direction="vertical" items={[
          { title: 'دستورالعمل Project', description: 'محتوای engineering/ai-harness/CHATGPT-PROJECT-INSTRUCTIONS.md را در Instructions پروژه ChatGPT قرار دهید.' },
          { title: 'فایل‌های مرجع', description: 'برای دسترسی به جزئیات، فایل‌های موردنیاز از agents و skills یا یک بسته مرجع را به Project اضافه کنید.' },
          { title: 'شروع درخواست', description: 'از همین صفحه نمونه Prompt یک نقش یا Skill را کپی و در ChatGPT همراه شواهد و فایل‌ها استفاده کنید.' },
          { title: 'به‌روزرسانی کنترل‌شده', description: 'هر تغییر در catalog و روش‌ها باید در Git نسخه‌بندی و پس از بررسی به Project نیز منتقل شود.' },
        ]} />
        <Paragraph type="secondary"><FileTextOutlined /> فایل مرجع: <code dir="ltr">engineering/ai-harness/README-FA.md</code></Paragraph>
        <Paragraph type="secondary"><SafetyOutlined /> داده‌های سری، دسترسی به فایل‌های محلی و اجرای تست خودکار جزو عملکرد این صفحه نیستند.</Paragraph>
      </Card> },
    ]} />

    <Drawer
      title={<span>{openEntry?.type === 'agent' ? 'راهنمای Agent' : 'راهنمای Skill'} — {openEntry?.title}</span>}
      open={openEntry !== null}
      onClose={() => setOpenEntry(null)}
      width="min(720px, 100vw)"
      destroyOnClose
      className="sy-eh-drawer"
    >
      {openEntry && <div dir="rtl">
        <Space wrap><Tag>{openEntry.categoryLabel}</Tag>
          <Tag color={openEntry.readiness === 'adapted' ? 'green' : 'default'}>{openEntry.readiness === 'adapted' ? 'سفارشی‌شده' : 'الگوی مقدماتی'}</Tag></Space>
        <Paragraph style={{ marginTop: 12 }}><code dir="ltr">{openEntry.slug}</code></Paragraph>
        <Paragraph>{openEntry.description}</Paragraph>
        <Title level={5}>مراحل استفاده</Title>
        <ol className="sy-eh-ordered">{openEntry.steps.map((s, i) => <li key={i}>{s.replace(/^[۱۲۳۴۵۶۷۸۹۰0-9]+[.،\s]*/, '')}</li>)}</ol>
        <Title level={5}>خروجی مورد انتظار</Title>
        <Paragraph>{openEntry.output}</Paragraph>
        <Alert showIcon type="warning" message="محدودیت و ملاحظه مهم" description={openEntry.warning} />
        <Title level={5} style={{ marginTop: 18 }}>نمونه درخواست آماده</Title>
        <CopyPrompt prompt={openEntry.example} />
        <Paragraph style={{ marginTop: 18 }} type="secondary">مرجع نام در نسخه ECC: <a href={openEntry.source} target="_blank" rel="noopener noreferrer">مشاهده فایل مبنا</a> (ارجاع اختیاری؛ این پنل برای کارکرد به اینترنت وابسته نیست.)</Paragraph>
      </div>}
    </Drawer>
  </div>
}
