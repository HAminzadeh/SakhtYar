import {
  ApartmentOutlined,
  ArrowLeftOutlined,
  BarChartOutlined,
  BookOutlined,
  FileDoneOutlined,
  FileTextOutlined,
  HomeOutlined,
  PictureOutlined,
  RobotOutlined,
  TeamOutlined,
} from '@ant-design/icons'
import { Alert, Breadcrumb, Button, Card, Space, Spin, Tabs, Tag, Typography } from 'antd'
import { useQuery } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { ApiError, api } from '../api/client'
import { useAuth } from '../auth/AuthProvider'
import type { CaseItem, CaseStatus } from '../api/types'
import { AssistantPanel } from '../features/case/AssistantPanel'
import { CaseOverview } from '../features/case/CaseOverview'
import { DocumentsPanel } from '../features/case/DocumentsPanel'
import { OwnersPanel } from '../features/case/OwnersPanel'
import { ProjectGalleryPanel } from '../features/case/ProjectGalleryPanel'
import { PropertyPanel } from '../features/case/PropertyPanel'
import { GlobalLocationSelector } from '../features/case/GlobalLocationSelector'
import {
  UrbanAnalysisPanel,
  ConstructionCostPanel,
  FeasibilityPanel,
  FinancialAnalysisPanel,
  ScenarioComparisonPanel,
} from '../features/case/CaseAnalysisPanels'
import { Phase517BuilderMatchPanel, Phase517ContractsPanel } from '../features/case/Phase517CasePanels'

type CaseTab =
  | 'overview' | 'property' | 'owners' | 'documents' | 'gallery'
  | 'urban-analysis' | 'construction-cost' | 'feasibility' | 'financial'
  | 'comparison' | 'builder-matching' | 'contracts' | 'assistant'

type WorkflowStage = {
  id: string
  label: string
  caption: string
  description: string
  icon: ReactNode
  sections: CaseTab[]
}

// Stages are a navigation guide, not legal approvals or claimed completion states.
const workflowStages: WorkflowStage[] = [
  {
    id: 'preparation', label: 'تشکیل پرونده', caption: 'اطلاعات و مدارک',
    description: 'مشخصات ملک، مالکان، مدارک و تصاویر پروژه را ثبت و بررسی کنید.',
    icon: <HomeOutlined />, sections: ['overview', 'property', 'owners', 'documents', 'gallery'],
  },
  {
    id: 'analysis', label: 'مطالعات و برآورد', caption: 'ضوابط، هزینه و مالی',
    description: 'تحلیل شهرسازی، هزینه ساخت، امکان‌سنجی و سناریوهای مالی را بررسی کنید.',
    icon: <BarChartOutlined />, sections: ['urban-analysis', 'construction-cost', 'feasibility', 'financial', 'comparison'],
  },
  {
    id: 'builders', label: 'انتخاب سازنده', caption: 'بررسی همکاری',
    description: 'گزینه‌های سازنده و پیشنهادهای همکاری را ارزیابی کنید.',
    icon: <TeamOutlined />, sections: ['builder-matching'],
  },
  {
    id: 'agreement', label: 'توافق و قرارداد', caption: 'ثبت توافقات',
    description: 'پیش‌نویس‌ها و مراحل قراردادی را مدیریت کنید.',
    icon: <FileDoneOutlined />, sections: ['contracts'],
  },
]

const localCovers = Array.from({ length: 10 }, (_, index) =>
  `/assets/sakhtyar/projects/project-${String(index + 1).padStart(2, '0')}.jpg`,
)

function resolveCover(item: CaseItem) {
  if (item.id.startsWith('10000000-0000-0000-0000-0000000000')) {
    const n = Number(item.id.slice(-2))
    return localCovers[(Number.isFinite(n) && n > 0 ? n - 1 : 0) % localCovers.length]
  }
  return item.coverImageUrl || localCovers[0]
}

function statusMeta(status: CaseStatus) {
  switch (status) {
    case 'CONTRACT': return { label: 'در قرارداد', color: 'green' }
    case 'CONSTRUCTION': return { label: 'در حال ساخت', color: 'blue' }
    case 'NEGOTIATION': return { label: 'در مذاکره', color: 'gold' }
    case 'COMPLETED': return { label: 'تکمیل شده', color: 'cyan' }
    case 'ON_HOLD': return { label: 'متوقف', color: 'red' }
    case 'ACTIVE': return { label: 'فعال', color: 'green' }
    case 'ARCHIVED': return { label: 'بایگانی', color: 'default' }
    default: return { label: 'پیش‌نویس', color: 'default' }
  }
}

export function CaseDetailPage() {
  const { id = '' } = useParams()
  const { hasPermission } = useAuth()
  const canUseAgent = hasPermission('AGENT_USE')
  const [searchParams, setSearchParams] = useSearchParams()
  const requestedTab = searchParams.get('section')
  const allSectionIds: CaseTab[] = workflowStages.flatMap(stage => stage.sections)
  const tab: CaseTab = requestedTab === 'assistant' && canUseAgent
    ? 'assistant'
    : allSectionIds.includes(requestedTab as CaseTab)
      ? requestedTab as CaseTab
      : 'overview'
  const stageIndex = workflowStages.findIndex(stage => stage.sections.includes(tab))
  const currentStage = workflowStages[stageIndex >= 0 ? stageIndex : 0]

  const navigateSection = (nextTab: CaseTab) => {
    setSearchParams(previous => {
      const next = new URLSearchParams(previous)
      next.set('section', nextTab)
      return next
    })
  }

  const caseQuery = useQuery({
    queryKey: ['case', id],
    queryFn: () => api<CaseItem>(`/api/v1/cases/${id}`),
    enabled: Boolean(id),
  })

  if (caseQuery.isLoading) return <div className="sakhtyar-loading"><Spin size="large" /></div>
  if (caseQuery.isError || !caseQuery.data) {
    const error = caseQuery.error
    const message = error instanceof ApiError
      ? error.status === 403 ? 'شما مجوز دسترسی به این پرونده را ندارید.'
        : error.status === 404 ? 'پرونده پیدا نشد.'
          : error.status === 401 ? 'نشست کاربری منقضی شده است. لطفاً دوباره وارد شوید.'
            : error.message
      : 'دریافت اطلاعات پرونده ناموفق بود.'
    return <Alert type="error" showIcon message={message} />
  }

  const item = caseQuery.data
  const status = statusMeta(item.status)
  const statusClass = `sakhtyar-status-${item.status.toLowerCase()}`

  // Keep all existing panels and API contracts; only their navigation changes.
  const tabs = [
    { key: 'overview', label: 'نمای کلی', icon: <HomeOutlined />,
      children: <CaseOverview caseId={id} caseItem={item} /> },
    { key: 'property', label: 'مشخصات ملک', icon: <ApartmentOutlined />,
      children: <><GlobalLocationSelector caseId={id} /><PropertyPanel caseId={id} caseItem={item} /></> },
    { key: 'owners', label: 'مالکان', icon: <TeamOutlined />,
      children: <OwnersPanel caseId={id} createdBy={item.createdBy} archived={item.status === 'ARCHIVED'} /> },
    { key: 'documents', label: 'مدارک', icon: <FileTextOutlined />,
      children: <DocumentsPanel caseId={id} /> },
    { key: 'gallery', label: 'تصاویر پروژه', icon: <PictureOutlined />,
      children: <ProjectGalleryPanel caseId={id} caseTitle={item.title} /> },
    { key: 'urban-analysis', label: 'ضوابط شهرسازی', icon: <BookOutlined />,
      children: <UrbanAnalysisPanel caseId={id} /> },
    { key: 'construction-cost', label: 'هزینه ساخت', icon: <BarChartOutlined />,
      children: <ConstructionCostPanel caseId={id} /> },
    { key: 'feasibility', label: 'امکان‌سنجی', icon: <BarChartOutlined />,
      children: <FeasibilityPanel caseId={id} /> },
    { key: 'financial', label: 'تحلیل مالی', icon: <BarChartOutlined />,
      children: <FinancialAnalysisPanel caseId={id} /> },
    { key: 'comparison', label: 'مقایسه سناریوها', icon: <BarChartOutlined />,
      children: <ScenarioComparisonPanel caseId={id} /> },
    { key: 'builder-matching', label: 'تطبیق سازندگان', icon: <TeamOutlined />,
      children: <Phase517BuilderMatchPanel caseId={id} /> },
    { key: 'contracts', label: 'قراردادها', icon: <FileDoneOutlined />,
      children: <Phase517ContractsPanel caseId={id} /> },
  ]

  const visibleTabs = tabs.filter(section => currentStage.sections.includes(section.key as CaseTab))
  const activeSectionIndex = currentStage.sections.indexOf(tab)
  const nextTab = tab === 'assistant' ? 'overview' as CaseTab
    : activeSectionIndex < currentStage.sections.length - 1
      ? currentStage.sections[activeSectionIndex + 1]
      : workflowStages[stageIndex + 1]?.sections[0]
  const nextLabel = nextTab === 'overview' ? 'بازگشت به نمای کلی'
    : tabs.find(section => section.key === nextTab)?.label

  return (
    <div className="sakhtyar-page-stack sakhtyar-case-page-v56 sy-case-workflow-page">
      <Breadcrumb className="sy-case-breadcrumb" items={[
        { title: <Link to="/cases">پرونده‌ها</Link> },
        { title: item.title },
        { title: tab === 'assistant' ? 'دستیار هوشمند' : currentStage.label },
      ]} />

      <section className="sakhtyar-case-split-hero sakhtyar-animated-card">
        <div className="sakhtyar-case-split-media">
          <img src={resolveCover(item)} alt={item.title} className="sakhtyar-case-split-media__image" />
        </div>
        <div className="sakhtyar-case-split-info">
          <Space wrap>
            <Tag color={status.color} className={`sakhtyar-status-chip ${statusClass}`}>
              {status.label}
            </Tag>
            <Typography.Text type="secondary">پرونده مشارکت در ساخت</Typography.Text>
          </Space>
          <Typography.Title>{item.title}</Typography.Title>
          <Typography.Paragraph>{item.description || 'پرونده مشارکت در ساخت'}</Typography.Paragraph>
          <div className="sakhtyar-case-meta-grid">
            <span><small>موقعیت</small><strong>{[item.district, item.city].filter(Boolean).join('، ') || 'ثبت نشده'}</strong></span>
            <span><small>مساحت زمین</small><strong>{item.landAreaM2
              ? `${item.landAreaM2.toLocaleString('fa-IR')} متر مربع` : 'ثبت نشده'}</strong></span>
            <span><small>آخرین به‌روزرسانی</small><strong>{new Date(item.updatedAt).toLocaleDateString('fa-IR')}</strong></span>
          </div>
        </div>
      </section>

      <Card className="sakhtyar-tabs-card sakhtyar-animated-card sy-case-workflow-card">
        <div className="sy-case-workflow-head">
          <div>
            <Typography.Title level={4}>مسیر انجام کار پرونده</Typography.Title>
            <Typography.Text type="secondary">
              هر مرحله را انتخاب کنید و از زیرمنوی آن وارد بخش موردنظر شوید.
              این مسیر، راهنمای انجام کار است و به معنی تأیید یا تکمیل حقوقی مراحل نیست.
            </Typography.Text>
          </div>
          {canUseAgent && <Button type={tab === 'assistant' ? 'primary' : 'default'}
            icon={<RobotOutlined />} onClick={() => navigateSection('assistant')}>
            دستیار هوشمند
          </Button>}
        </div>

        <div className="sy-case-stage-grid" role="group" aria-label="مراحل پرونده مشارکت در ساخت">
          {workflowStages.map((stage, index) => (
            <button type="button" key={stage.id}
              className={`sy-case-stage${index === stageIndex ? ' sy-case-stage--active' : ''}`}
              aria-pressed={index === stageIndex}
              onClick={() => navigateSection(stage.sections[0])}>
              <span className="sy-case-stage-number">{(index + 1).toLocaleString('fa-IR')}</span>
              <span className="sy-case-stage-content"><strong>{stage.icon} {stage.label}</strong><small>{stage.caption}</small></span>
            </button>
          ))}
        </div>

        {tab === 'assistant'
          ? <div className="sy-case-workflow-content">
            <Alert type="info" showIcon message="دستیار هوشمند، ابزار کمکی همه مراحل پرونده است" />
            <AssistantPanel caseId={id} />
          </div>
          : <div className="sy-case-workflow-content">
            <div className="sy-case-stage-description">
              <Typography.Text strong>مرحله {(stageIndex + 1).toLocaleString('fa-IR')} از ۴ — {currentStage.label}</Typography.Text>
              <Typography.Text type="secondary">{currentStage.description}</Typography.Text>
            </div>
            <Tabs activeKey={tab} onChange={key => navigateSection(key as CaseTab)}
              items={visibleTabs} size="large" className="sy-case-subtabs" />
          </div>}

        {nextTab && <div className="sy-case-workflow-next">
          <Typography.Text type="secondary">بخش بعدی پیشنهادی:</Typography.Text>
          <Button onClick={() => navigateSection(nextTab)} icon={<ArrowLeftOutlined />}>
            {nextLabel}
          </Button>
        </div>}
      </Card>
    </div>
  )
}
