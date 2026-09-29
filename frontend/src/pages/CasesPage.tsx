import {
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

type CreateCase = {
  title: string
  city?: string
  district?: string
  address?: string
  landAreaM2?: number
}

const localCovers = [
  '/assets/sakhtyar/projects/project-01.jpg',
  '/assets/sakhtyar/projects/project-02.jpg',
  '/assets/sakhtyar/projects/project-03.jpg',
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
    const q = search.trim().toLocaleLowerCase('fa')
    let result = [...(cases.data ?? [])]

    if (q) {
      result = result.filter((item) =>
        [item.title, item.city, item.district, item.address]
          .filter(Boolean)
          .some((value) =>
            String(value).toLocaleLowerCase('fa').includes(q),
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

  return (
    <div className="sakhtyar-page-stack">
      <section className="sakhtyar-hero sakhtyar-hero-cases">
        <div className="sakhtyar-hero-overlay" />
        <div className="sakhtyar-hero-copy">
          <span className="sakhtyar-hero-icon">
            <FolderOpenOutlined />
          </span>
          <div>
            <Typography.Title level={1}>
              پرونده‌های مشارکت
            </Typography.Title>
            <Typography.Paragraph>
              مدیریت پرونده‌های مشارکت در ساخت، املاک و فرآیندهای اجرایی
            </Typography.Paragraph>
          </div>
        </div>
      </section>

      <Row gutter={[16, 16]} className="sakhtyar-stats-row">
        <Col xs={24} sm={12} xl={6}>
          <Card className="sakhtyar-stat-card sakhtyar-stat-blue">
            <Statistic
              title="کل پرونده‌ها"
              value={all.length}
              prefix={<FolderOpenOutlined />}
            />
          </Card>
        </Col>

        <Col xs={24} sm={12} xl={6}>
          <Card className="sakhtyar-stat-card sakhtyar-stat-amber">
            <Statistic
              title="در حال ساخت"
              value={construction}
              prefix={<ClockCircleOutlined />}
            />
          </Card>
        </Col>

        <Col xs={24} sm={12} xl={6}>
          <Card className="sakhtyar-stat-card sakhtyar-stat-green">
            <Statistic
              title="در قرارداد"
              value={contract}
              prefix={<CheckCircleOutlined />}
            />
          </Card>
        </Col>

        <Col xs={24} sm={12} xl={6}>
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
        <div className="sakhtyar-filter-row">
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

          {canWrite && (
            <Button
              type="primary"
              size="large"
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

      <Row gutter={[20, 20]}>
        {items.map((item, index) => {
          const status = statusMeta(item.status)
          const fallback = localCovers[index % localCovers.length]
          const cover = demoCover(item, index)

          return (
            <Col key={item.id} xs={24} md={12} xl={8}>
              <Card
                hoverable
                className="sakhtyar-project-card"
                cover={
                  <div className="sakhtyar-project-cover">
                    <img
                      src={cover}
                      alt={item.title}
                      onError={(e) => {
                        if (e.currentTarget.src.endsWith(fallback)) return
                        e.currentTarget.src = fallback
                      }}
                    />
                    <Tag color={status.color}>
                      {status.label}
                    </Tag>
                  </div>
                }
                onClick={() => navigate(`/cases/${item.id}`)}
              >
                <Space
                  direction="vertical"
                  size={13}
                  style={{ width: '100%' }}
                >
                  <Typography.Title level={4} style={{ margin: 0 }}>
                    {item.title}
                  </Typography.Title>

                  <Typography.Text type="secondary">
                    <EnvironmentOutlined />{' '}
                    {[item.district, item.city]
                      .filter(Boolean)
                      .join('، ') || 'موقعیت ثبت نشده'}
                  </Typography.Text>

                  <div className="sakhtyar-project-meta">
                    <span>
                      <ExpandOutlined />
                      <small>مساحت زمین</small>
                      <strong>
                        {item.landAreaM2
                          ? `${item.landAreaM2.toLocaleString('fa-IR')} متر مربع`
                          : 'ثبت نشده'}
                      </strong>
                    </span>

                    <span>
                      <ClockCircleOutlined />
                      <small>آخرین بروزرسانی</small>
                      <strong>
                        {new Date(item.updatedAt).toLocaleDateString('fa-IR')}
                      </strong>
                    </span>
                  </div>

                  <Button block size="large">
                    مشاهده پرونده
                  </Button>
                </Space>
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

          <Form.Item
            label="مساحت زمین (متر مربع)"
            name="landAreaM2"
          >
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
            loading={createCase.isPending}
          >
            ایجاد پرونده
          </Button>
        </Form>
      </Modal>
    </div>
  )
}
