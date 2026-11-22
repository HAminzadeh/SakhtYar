import { BuildOutlined, FileTextOutlined, PieChartOutlined, TeamOutlined } from '@ant-design/icons'
import { Alert, Card, Col, Progress, Row, Space, Typography } from 'antd'
import { useQuery } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { ApiError, api } from '../../api/client'
import type { CaseItem, DocumentItem, OwnerItem, PropertyItem } from '../../api/types'

function sharePercent(owners: OwnerItem[]) {
  return owners.reduce((sum, owner) => {
    if (!owner.ownershipDenominator) return sum
    return sum + (owner.ownershipNumerator / owner.ownershipDenominator) * 100
  }, 0)
}

function faNumber(value: number, maximumFractionDigits = 2) {
  return new Intl.NumberFormat('fa-IR', { maximumFractionDigits }).format(value)
}

function StatCard({ title, value, subtitle, icon, tone }: { title: string; value: string; subtitle?: string; icon: ReactNode; tone: 'blue' | 'green' | 'amber' | 'violet' }) {
  return (
    <Card className={`sakhtyar-overview-stat sakhtyar-overview-${tone}`}>
      <div className="sakhtyar-overview-stat-row">
        <span className="sakhtyar-overview-stat-icon">{icon}</span>
        <div>
          <Typography.Text type="secondary">{title}</Typography.Text>
          <Typography.Title level={3}>{value}</Typography.Title>
          {subtitle ? <Typography.Text type="secondary">{subtitle}</Typography.Text> : null}
        </div>
      </div>
    </Card>
  )
}

export function CaseOverview({ caseId, caseItem }: { caseId: string; caseItem: CaseItem }) {
  const property = useQuery({
    queryKey: ['property', caseId],
    queryFn: async (): Promise<PropertyItem | null> => {
      try { return await api<PropertyItem>(`/api/v1/cases/${caseId}/property`) }
      catch (error) { if (error instanceof ApiError && error.status === 404) return null; throw error }
    }, retry: false,
  })
  const owners = useQuery({
    queryKey: ['owners', caseId],
    queryFn: async (): Promise<OwnerItem[]> => {
      try { return await api<OwnerItem[]>(`/api/v1/cases/${caseId}/owners`) }
      catch (error) { if (error instanceof ApiError && error.status === 404) return []; throw error }
    }, retry: false,
  })
  const documents = useQuery({ queryKey: ['documents', caseId], queryFn: () => api<DocumentItem[]>(`/api/v1/cases/${caseId}/documents`) })

  const ownerItems = owners.data ?? []
  const ownership = sharePercent(ownerItems)
  const primaryOwner = ownerItems.find((owner) => owner.primaryContact)
  const area = property.data?.landAreaM2 ?? caseItem.landAreaM2 ?? null
  const anyError = property.isError || owners.isError || documents.isError
  const completionSteps = [Boolean(property.data), ownerItems.length > 0, (documents.data?.length ?? 0) > 0]
  const completion = (completionSteps.filter(Boolean).length / completionSteps.length) * 100

  return (
    <Space direction="vertical" size={20} style={{ width: '100%' }}>
      {anyError ? <Alert type="warning" showIcon message="بخشی از اطلاعات خلاصه پرونده دریافت نشد." /> : null}
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} xl={6}><StatCard title="مساحت زمین" value={area == null ? 'ثبت نشده' : `${faNumber(area)} متر مربع`} icon={<BuildOutlined />} tone="blue" /></Col>
        <Col xs={24} sm={12} xl={6}><StatCard title="مالکین" value={`${ownerItems.length.toLocaleString('fa-IR')} نفر`} subtitle={primaryOwner ? `رابط: ${primaryOwner.firstName} ${primaryOwner.lastName}` : 'رابط اصلی تعیین نشده'} icon={<TeamOutlined />} tone="green" /></Col>
        <Col xs={24} sm={12} xl={6}><StatCard title="سهم ثبت‌شده" value={`${faNumber(ownership)}٪`} subtitle={ownership < 99.999 ? `${faNumber(Math.max(0, 100 - ownership))}٪ باقی‌مانده` : 'مالکیت کامل ثبت شده'} icon={<PieChartOutlined />} tone="amber" /></Col>
        <Col xs={24} sm={12} xl={6}><StatCard title="مدارک" value={`${(documents.data?.length ?? 0).toLocaleString('fa-IR')} فایل`} icon={<FileTextOutlined />} tone="violet" /></Col>
      </Row>
      <Card className="sakhtyar-completion-card">
        <Row gutter={[24, 16]} align="middle">
          <Col xs={24} lg={7}><Typography.Title level={4}>وضعیت تکمیل پرونده</Typography.Title><Typography.Text type="secondary">میزان تکمیل اطلاعات اصلی پرونده</Typography.Text></Col>
          <Col xs={24} lg={9}><Progress percent={Math.round(completion)} strokeLinecap="round" /></Col>
          <Col xs={24} lg={8}>
            <Space direction="vertical" size={6}>
              <Typography.Text>مشخصات ملک: <strong>{property.data ? 'ثبت شده' : 'نیازمند تکمیل'}</strong></Typography.Text>
              <Typography.Text>مالکین: <strong>{ownerItems.length > 0 ? `${ownerItems.length.toLocaleString('fa-IR')} مالک` : 'هنوز ثبت نشده'}</strong></Typography.Text>
              <Typography.Text>مدارک: <strong>{(documents.data?.length ?? 0) > 0 ? `${documents.data?.length.toLocaleString('fa-IR')} فایل` : 'هنوز ثبت نشده'}</strong></Typography.Text>
            </Space>
          </Col>
        </Row>
      </Card>
    </Space>
  )
}
