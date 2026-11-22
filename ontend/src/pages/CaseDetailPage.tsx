import {
  ApartmentOutlined,
  CalendarOutlined,
  EnvironmentOutlined,
  ExpandOutlined,
  FileTextOutlined,
  GlobalOutlined,
  HomeOutlined,
  RobotOutlined,
  TeamOutlined,
} from '@ant-design/icons'
import { Alert, Card, Space, Spin, Tabs, Tag, Typography } from 'antd'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { api } from '../api/client'
import { useAuth } from '../auth/AuthProvider'
import type { CaseItem, CaseStatus } from '../api/types'
import { AssistantPanel } from '../features/case/AssistantPanel'
import { CaseOverview } from '../features/case/CaseOverview'
import { DocumentsPanel } from '../features/case/DocumentsPanel'
import { OwnersPanel } from '../features/case/OwnersPanel'
import { PropertyMap } from '../features/case/PropertyMap'
import { PropertyPanel } from '../features/case/PropertyPanel'
import { projectImage } from '../ui/projectImages'

type CaseTab = 'overview' | 'property' | 'owners' | 'map' | 'documents' | 'assistant'

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
  const [tab, setTab] = useState<CaseTab>('overview')
  const caseQuery = useQuery({ queryKey: ['case', id], queryFn: () => api<CaseItem>(`/api/v1/cases/${id}`), enabled: Boolean(id) })

  if (caseQuery.isLoading) return <div className="sakhtyar-loading"><Spin size="large" /></div>
  if (caseQuery.isError || !caseQuery.data) return <Alert type="error" showIcon message="پرونده پیدا نشد." />

  const item = caseQuery.data
  const status = statusMeta(item.status)
  const cover = projectImage(item)
  const tabs = [
    { key: 'overview', label: 'خلاصه', icon: <HomeOutlined />, children: <CaseOverview caseId={id} caseItem={item} /> },
    { key: 'property', label: 'مشخصات ملک', icon: <ApartmentOutlined />, children: <PropertyPanel caseId={id} caseItem={item} /> },
    { key: 'owners', label: 'مالکین', icon: <TeamOutlined />, children: <OwnersPanel caseId={id} /> },
    { key: 'map', label: 'نقشه', icon: <GlobalOutlined />, children: <PropertyMap caseId={id} /> },
    { key: 'documents', label: 'مدارک', icon: <FileTextOutlined />, children: <DocumentsPanel caseId={id} /> },
    ...(canUseAgent ? [{ key: 'assistant', label: 'دستیار هوشمند', icon: <RobotOutlined />, children: <AssistantPanel caseId={id} /> }] : []),
  ]

  return (
    <div className="sakhtyar-page-stack">
      <section className="sakhtyar-case-split-hero">
        <div className="sakhtyar-case-split-media" style={{ backgroundImage: `url("${cover}")` }} />
        <div className="sakhtyar-case-split-info">
          <Space wrap><Tag color={status.color}>{status.label}</Tag><Typography.Text type="secondary">پرونده مشارکت در ساخت</Typography.Text></Space>
          <Typography.Title>{item.title}</Typography.Title>
          <Typography.Paragraph>{item.description || 'پرونده مشارکت در ساخت'}</Typography.Paragraph>
          <div className="sakhtyar-case-info-grid">
            <span><EnvironmentOutlined /><small>موقعیت</small><strong>{[item.district, item.city].filter(Boolean).join('، ') || 'ثبت نشده'}</strong></span>
            <span><ExpandOutlined /><small>مساحت زمین</small><strong>{item.landAreaM2 ? `${item.landAreaM2.toLocaleString('fa-IR')} متر مربع` : 'ثبت نشده'}</strong></span>
            <span><CalendarOutlined /><small>آخرین بروزرسانی</small><strong>{new Date(item.updatedAt).toLocaleDateString('fa-IR')}</strong></span>
          </div>
        </div>
      </section>

      <Card className="sakhtyar-tabs-card">
        <Tabs activeKey={tab} onChange={(key) => setTab(key as CaseTab)} items={tabs} size="large" />
      </Card>
    </div>
  )
}
