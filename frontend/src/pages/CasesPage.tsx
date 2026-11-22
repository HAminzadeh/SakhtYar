import {
  AppstoreOutlined,
  BarsOutlined,
  BorderOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  EnvironmentOutlined,
  ExpandOutlined,
  FilterOutlined,
  FolderOpenOutlined,
  PlusOutlined,
  SearchOutlined,
  SortAscendingOutlined,
  StopOutlined,
} from '@ant-design/icons'
import {
  Alert,
  Button,
  Card,
  Col,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Space,
  Statistic,
  Tag,
  Typography,
} from 'antd'
import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { api } from '../api/client'
import type { CaseItem, CaseStatus } from '../api/types'
import { useAuth } from '../auth/AuthProvider'
import { PageHero } from '../ui/PageHero'

type CreateCase = {
  title: string
  city?: string
  district?: string
  address?: string
  landAreaM2?: number
}

type ViewMode = 'cards' | 'compact' | 'list'
type ColumnCount = 2 | 3 | 4

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

function demoCover(item: CaseItem, index: number) {
  if (item.id.startsWith('10000000-0000-0000-0000-0000000000')) {
    const raw = Number(item.id.slice(-2))
    const n = Number.isFinite(raw) && raw > 0 ? raw : index + 1
    return localCovers[(n - 1) % localCovers.length]
  }

  return item.coverImageUrl || localCovers[index % localCovers.length]
}

function statusMeta(status: CaseStatus) {
  switch (status) {
    case 'CONTRACT':
      return { label: 'در قرارداد', color: 'green' }
    case 'CONSTRUCTION':
      return { label: 'در حال ساخت', color: 'blue' }
    case 'NEGOTIATION':
      return { label: 'در مذاکره', color: 'gold' }
    case 'COMPLETED':
      return { label: 'تکمیل شده', color: 'cyan' }
    case 'ON_HOLD':
      return { label: 'متوقف', color: 'red' }
    case 'ACTIVE':
      return { label: 'فعال', color: 'green' }
    case 'ARCHIVED':
      return { label: 'بایگانی', color: 'default' }
    default:
      return { label: 'پیش‌نویس', color: 'default' }
  }
}

export function CasesPage() {
  const navigate = useNavigate()
  const { hasPermission } = useAuth()
  const canWrite = hasPermission('CASE_WRITE')
  const queryClient = useQueryClient()

  const [open, setOpen] = useState(false)
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState<string>('ALL')
  const [sort, setSort] = useState<'UPDATED' | 'AREA'>('UPDATED')
  const [viewMode, setViewMode] = useState<ViewMode>('cards')
  const [columnCount, setColumnCount] = useState<ColumnCount>(3)

  const cases = useQuery({
    queryKey: ['cases'],
    queryFn: () => api<CaseItem[]>('/api/v1/cases'),
  })

  const createCase = useMutation({
    mutationFn: (data: CreateCase) =>
      api<CaseItem>('/api/v1/cases', {
        method: 'POST',
        body: JSON.stringify({
          ...data,
          status: 'DRAFT',
          description: '',
          coverImageUrl: localCovers[0],
        }),
      }),
    onSuccess: (created) => {
      queryClient.invalidateQueries({ queryKey: ['cases'] })
      setOpen(false)
      navigate(`/cases/${created.id}`)
    },
  })

  const items = useMemo(() => {
    const q = search.trim().toLocaleLowerCase(document.documentElement.lang === 'fa' ? 'fa-IR' : 'en-US')
    let result = [...(cases.data ?? [])]

    if (q) {
      result = result.filter((item) =>
        [item.title, item.city, item.district, item.address]
          .filter(Boolean)
          .some((value) =>
            String(value).toLocaleLowerCase(document.documentElement.lang === 'fa' ? 'fa-IR' : 'en-US').includes(q),
          ),
      )
    }

    if (statusFilter !== 'ALL') {
      result = result.filter((item) => item.status === statusFilter)
    }

    result.sort((a, b) => {
      if (sort === 'AREA') {
        return (b.landAreaM2 ?? 0) - (a.landAreaM2 ?? 0)
      }

      return (
        new Date(b.updatedAt).getTime() -
        new Date(a.updatedAt).getTime()
      )
    })

    return result
  }, [cases.data, search, statusFilter, sort])

  const all = cases.data ?? []
  const construction = all.filter((x) => x.status === 'CONSTRUCTION').length
  const contract = all.filter((x) => x.status === 'CONTRACT').length
  const onHold = all.filter((x) => x.status === 'ON_HOLD').length

  const layout = useMemo(() => {
    if (viewMode === 'list') return { md: 24, lg: 24, xl: 24 }
    if (viewMode === 'compact') return { md: 12, lg: 8, xl: 6 }
    if (columnCount === 2) return { md: 12, lg: 12, xl: 12 }
    if (columnCount === 4) return { md: 12, lg: 8, xl: 6 }
    return { md: 12, lg: 8, xl: 8 }
  }, [viewMode, columnCount])

  return (
    <div className="sakhtyar-page-stack sakhtyar-cases-page">
      <PageHero
        image="/assets/sakhtyar/heroes/cases-hero.jpg"
        title="پرونده‌های مشارکت"
        subtitle="مدیریت پرونده‌های مشارکت در ساخت، ملک و فرآیندهای اجرایی"
        icon={<FolderOpenOutlined />}
      />

      <Row gutter={[12, 12]} className="sakhtyar-stats-row">
        <Col xs={12} md={6}>
          <Card className="sakhtyar-stat-card sakhtyar-stat-blue">
            <Statistic
              title="کل پرونده‌ها"
              value={all.length}
              prefix={<FolderOpenOutlined />}
            />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card className="sakhtyar-stat-card sakhtyar-stat-amber">
            <Statistic
              title="در حال ساخت"
              value={construction}
              prefix={<ClockCircleOutlined />}
            />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card className="sakhtyar-stat-card sakhtyar-stat-green">
            <Statistic
              title="در قرارداد"
              value={contract}
              prefix={<CheckCircleOutlined />}
            />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card className="sakhtyar-stat-card sakhtyar-stat-red">
            <Statistic
              title="متوقف شده"
              value={onHold}
              prefix={<StopOutlined />}
            />
          </Card>
        </Col>
      </Row>

      <Card className="sakhtyar-filter-card">
        <div className="sakhtyar-filter-row sakhtyar-filter-row-v53">
          <Input
            allowClear
            size="large"
            prefix={<SearchOutlined />}
            placeholder="جستجو در نام پروژه، موقعیت یا آدرس..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />

          <Select
            size="large"
            value={statusFilter}
            onChange={setStatusFilter}
            suffixIcon={<FilterOutlined />}
            options={[
              { value: 'ALL', label: 'همه وضعیت‌ها' },
              { value: 'NEGOTIATION', label: 'در مذاکره' },
              { value: 'CONTRACT', label: 'در قرارداد' },
              { value: 'CONSTRUCTION', label: 'در حال ساخت' },
              { value: 'ACTIVE', label: 'فعال' },
              { value: 'COMPLETED', label: 'تکمیل شده' },
              { value: 'ON_HOLD', label: 'متوقف' },
              { value: 'DRAFT', label: 'پیش‌نویس' },
            ]}
          />

          <Select
            size="large"
            value={sort}
            onChange={setSort}
            suffixIcon={<SortAscendingOutlined />}
            options={[
              { value: 'UPDATED', label: 'آخرین بروزرسانی' },
              { value: 'AREA', label: 'بیشترین مساحت' },
            ]}
          />

          <div className="sakhtyar-view-controls">
            <Button
              type={viewMode === 'cards' ? 'primary' : 'default'}
              icon={<AppstoreOutlined />}
              onClick={() => setViewMode('cards')}
            >
              کارتی
            </Button>
            <Button
              type={viewMode === 'compact' ? 'primary' : 'default'}
              icon={<BorderOutlined />}
              onClick={() => setViewMode('compact')}
            >
              فشرده
            </Button>
            <Button
              type={viewMode === 'list' ? 'primary' : 'default'}
              icon={<BarsOutlined />}
              onClick={() => setViewMode('list')}
            >
              لیستی
            </Button>
          </div>

          {viewMode === 'cards' ? (
            <Select
              className="sakhtyar-column-selector"
              size="large"
              value={columnCount}
              onChange={(value) => setColumnCount(value as ColumnCount)}
              options={[
                { value: 2, label: '۲ کارت در ردیف' },
                { value: 3, label: '۳ کارت در ردیف' },
                { value: 4, label: '۴ کارت در ردیف' },
              ]}
            />
          ) : null}

          {canWrite && (
            <Button
              type="primary"
              size="large"
              className="sakhtyar-primary-action"
              icon={<PlusOutlined />}
              onClick={() => setOpen(true)}
            >
              پرونده جدید
            </Button>
          )}
        </div>
      </Card>

      {cases.isError ? (
        <Alert
          type="error"
          showIcon
          message="دریافت پرونده‌ها ناموفق بود."
        />
      ) : null}

      {items.length === 0 && !cases.isLoading ? <Empty /> : null}

      <Row
        gutter={[16, 16]}
        className={`sakhtyar-project-grid sakhtyar-view-${viewMode}`}
      >
        {items.map((item, index) => {
          const status = statusMeta(item.status)
          const fallback = localCovers[index % localCovers.length]
          const cover = demoCover(item, index)

          return (
            <Col
              key={item.id}
              xs={24}
              md={layout.md}
              lg={layout.lg}
              xl={layout.xl}
            >
              <Card
                hoverable
                className={`sakhtyar-project-card sakhtyar-project-card-${viewMode}`}
                cover={
                  <div className="sakhtyar-project-cover">
                    <img
                      src={cover}
                      alt={item.title}
                      loading="lazy"
                      onError={(e) => {
                        if (e.currentTarget.src.endsWith(fallback)) return
                        e.currentTarget.src = fallback
                      }}
                    />
                    <Tag className={`sakhtyar-status-badge sakhtyar-status-${item.status.toLowerCase()}`} color={status.color}>
                      {status.label}
                    </Tag>
                  </div>
                }
                onClick={() => navigate(`/cases/${item.id}`)}
              >
                <div className="sakhtyar-project-card-content">
                  <div className="sakhtyar-project-title-block">
                    <Typography.Title level={4}>
                      {item.title}
                    </Typography.Title>
                    <Typography.Text type="secondary">
                      <EnvironmentOutlined />{' '}
                      {[item.district, item.city]
                        .filter(Boolean)
                        .join('، ') || 'موقعیت ثبت نشده'}
                    </Typography.Text>
                  </div>

                  <div className="sakhtyar-project-meta">
                    <span>
                      <ExpandOutlined />
                      <small>مساحت زمین</small>
                      <strong>
                        {item.landAreaM2
                          ? `${item.landAreaM2.toLocaleString(document.documentElement.lang === 'fa' ? 'fa-IR' : 'en-US')} متر مربع`
                          : 'ثبت نشده'}
                      </strong>
                    </span>
                    <span>
                      <ClockCircleOutlined />
                      <small>آخرین بروزرسانی</small>
                      <strong>
                        {new Date(item.updatedAt).toLocaleDateString(document.documentElement.lang === 'fa' ? 'fa-IR' : 'en-US')}
                      </strong>
                    </span>
                  </div>

                  <Button
                    block={viewMode !== 'list'}
                    className="sakhtyar-card-cta"
                    onClick={(event) => {
                      event.stopPropagation()
                      navigate(`/cases/${item.id}`)
                    }}
                  >
                    مشاهده پرونده
                  </Button>
                </div>
              </Card>
            </Col>
          )
        })}
      </Row>

      <Modal
        open={open && canWrite}
        title="ایجاد پرونده جدید"
        onCancel={() => setOpen(false)}
        footer={null}
        destroyOnHidden
      >
        <Form<CreateCase>
          layout="vertical"
          onFinish={(values) => createCase.mutate(values)}
          requiredMark={false}
        >
          <Form.Item
            label="عنوان پرونده"
            name="title"
            rules={[
              {
                required: true,
                message: 'عنوان پرونده الزامی است',
              },
            ]}
          >
            <Input size="large" />
          </Form.Item>

          <Row gutter={12}>
            <Col span={12}>
              <Form.Item label="شهر" name="city">
                <Input size="large" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="منطقه" name="district">
                <Input size="large" />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item label="آدرس" name="address">
            <Input.TextArea rows={3} />
          </Form.Item>

          <Form.Item label="مساحت زمین (متر مربع)" name="landAreaM2">
            <InputNumber
              style={{ width: '100%' }}
              size="large"
              min={1}
            />
          </Form.Item>

          <Button
            type="primary"
            htmlType="submit"
            size="large"
            block
            className="sakhtyar-primary-action"
            loading={createCase.isPending}
          >
            ایجاد پرونده
          </Button>
        </Form>
      </Modal>
    </div>
  )
}
