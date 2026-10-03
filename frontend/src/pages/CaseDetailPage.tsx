import {
  ApartmentOutlined,
  FileTextOutlined,
  HomeOutlined,
  PictureOutlined,
  RobotOutlined,
  TeamOutlined,
} from '@ant-design/icons'
import {
  Alert,
  Card,
  Space,
  Spin,
  Tabs,
  Tag,
  Typography,
} from 'antd'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { ApiError, api } from '../api/client'
import { useAuth } from '../auth/AuthProvider'
import type {
  CaseItem,
  CaseStatus,
} from '../api/types'
import { AssistantPanel } from '../features/case/AssistantPanel'
import { CaseOverview } from '../features/case/CaseOverview'
import { DocumentsPanel } from '../features/case/DocumentsPanel'
import { OwnersPanel } from '../features/case/OwnersPanel'
import { ProjectGalleryPanel } from '../features/case/ProjectGalleryPanel'
import { PropertyPanel } from '../features/case/PropertyPanel'
import { GlobalLocationSelector } from '../features/case/GlobalLocationSelector'

type CaseTab =
  | 'overview'
  | 'property'
  | 'owners'
  | 'documents'
  | 'gallery'
  | 'assistant'

const localCovers = [
  '/assets/sakhtyar/projects/project-01.jpg',
  '/assets/sakhtyar/projects/project-02.jpg',
  '/assets/sakhtyar/projects/project-03.jpg',
  '/assets/sakhtyar/projects/project-04.jpg',
  '/assets/sakhtyar/projects/project-05.jpg',
  '/assets/sakhtyar/projects/project-06.jpg',
  '/assets/sakhtyar/projects/project-07.jpg',
  '/assets/sakhtyar/projects/project-08.jpg',
  '/assets/sakhtyar/projects/project-09.jpg',
  '/assets/sakhtyar/projects/project-10.jpg',
]

function resolveCover(item: CaseItem) {
  if (
    item.id.startsWith(
      '10000000-0000-0000-0000-0000000000',
    )
  ) {
    const raw = Number(item.id.slice(-2))
    const n =
      Number.isFinite(raw) && raw > 0
        ? raw
        : 1
    return localCovers[
      (n - 1) % localCovers.length
    ]
  }

  return (
    item.coverImageUrl || localCovers[0]
  )
}

function statusMeta(
  status: CaseStatus,
) {
  switch (status) {
    case 'CONTRACT':
      return {
        label: 'در قرارداد',
        color: 'green',
      }
    case 'CONSTRUCTION':
      return {
        label: 'در حال ساخت',
        color: 'blue',
      }
    case 'NEGOTIATION':
      return {
        label: 'در مذاکره',
        color: 'gold',
      }
    case 'COMPLETED':
      return {
        label: 'تکمیل شده',
        color: 'cyan',
      }
    case 'ON_HOLD':
      return {
        label: 'متوقف',
        color: 'red',
      }
    case 'ACTIVE':
      return {
        label: 'فعال',
        color: 'green',
      }
    case 'ARCHIVED':
      return {
        label: 'بایگانی',
        color: 'default',
      }
    default:
      return {
        label: 'پیش‌نویس',
        color: 'default',
      }
  }
}

export function CaseDetailPage() {
  const { id = '' } = useParams()
  const { hasPermission } = useAuth()
  const canUseAgent =
    hasPermission('AGENT_USE')
  const [tab, setTab] =
    useState<CaseTab>('overview')

  const caseQuery = useQuery({
    queryKey: ['case', id],
    queryFn: () =>
      api<CaseItem>(
        `/api/v1/cases/${id}`,
      ),
    enabled: Boolean(id),
  })

  if (caseQuery.isLoading) {
    return (
      <div className="sakhtyar-loading">
        <Spin size="large" />
      </div>
    )
  }
  if (
    caseQuery.isError ||
    !caseQuery.data
  ) {
    const error = caseQuery.error

    let message = '\u062f\u0631\u06cc\u0627\u0641\u062a \u0627\u0637\u0644\u0627\u0639\u0627\u062a \u067e\u0631\u0648\u0646\u062f\u0647 \u0646\u0627\u0645\u0648\u0641\u0642 \u0628\u0648\u062f.'

    if (error instanceof ApiError) {
      if (error.status === 403) {
        message = '\u0634\u0645\u0627 \u0645\u062c\u0648\u0632 \u062f\u0633\u062a\u0631\u0633\u06cc \u0628\u0647 \u0627\u06cc\u0646 \u067e\u0631\u0648\u0646\u062f\u0647 \u0631\u0627 \u0646\u062f\u0627\u0631\u06cc\u062f.'
      } else if (error.status === 404) {
        message = '\u067e\u0631\u0648\u0646\u062f\u0647 \u067e\u06cc\u062f\u0627 \u0646\u0634\u062f.'
      } else if (error.status === 401) {
        message = '\u0646\u0634\u0633\u062a \u06a9\u0627\u0631\u0628\u0631\u06cc \u0645\u0646\u0642\u0636\u06cc \u0634\u062f\u0647 \u0627\u0633\u062a. \u0644\u0637\u0641\u0627\u064b \u062f\u0648\u0628\u0627\u0631\u0647 \u0648\u0627\u0631\u062f \u0634\u0648\u06cc\u062f.'
      } else {
        message = error.message
      }
    }

    return (
      <Alert
        type="error"
        showIcon
        message={message}
      />
    )
  }
const item = caseQuery.data
  const status =
    statusMeta(item.status)
  const cover = resolveCover(item)
  const statusClass =
    `sakhtyar-status-${item.status.toLowerCase()}`

  const tabs = [
    {
      key: 'overview',
      label: 'خلاصه',
      icon: <HomeOutlined />,
      children: (
        <CaseOverview
          caseId={id}
          caseItem={item}
        />
      ),
    },
    {
      key: 'property',
      label: 'مشخصات ملک',
      icon: <ApartmentOutlined />,
      children: (
        <>
          <GlobalLocationSelector caseId={id} />
          <PropertyPanel
            caseId={id}
            caseItem={item}
          />
        </>
      ),
    },
    {
      key: 'owners',
      label: 'مالکین',
      icon: <TeamOutlined />,
      children: (
        <OwnersPanel caseId={id} />
      ),
    },
    {
      key: 'documents',
      label: 'مدارک',
      icon: <FileTextOutlined />,
      children: (
        <DocumentsPanel
          caseId={id}
        />
      ),
    },
    {
      key: 'gallery',
      label: 'گالری تصاویر',
      icon: <PictureOutlined />,
      children: (
        <ProjectGalleryPanel
          caseId={id}
          caseTitle={item.title}
        />
      ),
    },
    ...(canUseAgent
      ? [
          {
            key: 'assistant',
            label: 'دستیار هوشمند',
            icon: <RobotOutlined />,
            children: (
              <AssistantPanel
                caseId={id}
              />
            ),
          },
        ]
      : []),
  ]

  return (
    <div className="sakhtyar-page-stack sakhtyar-case-page-v56">
      <section className="sakhtyar-case-split-hero sakhtyar-animated-card">
        <div className="sakhtyar-case-split-media">
          <img
            src={cover}
            alt={item.title}
            className="sakhtyar-case-split-media__image"
          />
        </div>

        <div className="sakhtyar-case-split-info">
          <Space wrap>
            <Tag
              color={status.color}
              className={`sakhtyar-status-chip ${statusClass}`}
            >
              {status.label}
            </Tag>

            <Typography.Text type="secondary">
              پرونده مشارکت در ساخت
            </Typography.Text>
          </Space>

          <Typography.Title>
            {item.title}
          </Typography.Title>

          <Typography.Paragraph>
            {item.description ||
              'پرونده مشارکت در ساخت'}
          </Typography.Paragraph>

          <div className="sakhtyar-case-meta-grid">
            <span>
              <small>موقعیت</small>
              <strong>
                {[
                  item.district,
                  item.city,
                ]
                  .filter(Boolean)
                  .join('، ') ||
                  'ثبت نشده'}
              </strong>
            </span>

            <span>
              <small>مساحت زمین</small>
              <strong>
                {item.landAreaM2
                  ? `${item.landAreaM2.toLocaleString(
                      'fa-IR',
                    )} متر مربع`
                  : 'ثبت نشده'}
              </strong>
            </span>

            <span>
              <small>
                آخرین بروزرسانی
              </small>
              <strong>
                {new Date(
                  item.updatedAt,
                ).toLocaleDateString(
                  'fa-IR',
                )}
              </strong>
            </span>
          </div>
        </div>
      </section>

      <Card className="sakhtyar-tabs-card sakhtyar-animated-card">
        <Tabs
          activeKey={tab}
          onChange={(key) =>
            setTab(key as CaseTab)
          }
          items={tabs}
          size="large"
        />
      </Card>
    </div>
  )
}
