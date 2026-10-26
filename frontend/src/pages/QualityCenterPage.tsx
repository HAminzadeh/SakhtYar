import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Divider,
  Drawer,
  Empty,
  Input,
  List,
  Modal,
  Progress,
  Row,
  Select,
  Space,
  Statistic,
  Steps,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd'
import {
  AuditOutlined,
  BarChartOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  DatabaseOutlined,
  DownloadOutlined,
  HistoryOutlined,
  PlayCircleOutlined,
  ReloadOutlined,
  SearchOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import { api } from '../api/client'
import '../styles/quality-center-v3.css'

type Scenario = {
  id: string
  module: string
  title: string
  description: string
  expected: string
  kind: 'ROUTE' | 'SQL' | 'EXTERNAL'
  target: string
  risk: string
  coverage: string
  source: string
}

type Result = {
  test_id: string
  module: string
  title: string
  status: string
  actual?: string
  expected?: string
  detail?: string
  duration_ms?: number | null
}

type Run = {
  id: string
  scope: string
  requested_by: string
  environment: string
  git_commit: string
  status: string
  total: number
  passed: number
  failed: number
  blocked: number
  started_at: string
  finished_at?: string
  results?: Result[]
}

type Overview = {
  totalScenarios: number
  runnableSafeScenarios: number
  requiresIsolatedRunner: number
  totalRuns: number
  latestRun?: Run | null
  recentRuns: Run[]
}

const moduleNames: Record<string, string> = {
  infrastructure: 'زیرساخت و دیتابیس',
  security: 'امنیت و کاربران',
  casefile: 'پرونده و اسناد',
  knowledge: 'دانش و استخراج',
  calculations: 'موتورهای محاسباتی',
  agents: 'هوش مصنوعی و عامل‌ها',
  frontend: 'فرانت‌اند و تجربه کاربری',
  integration: 'یکپارچگی و انتها‌به‌انتها',
  quality: 'مرکز آزمون',
  monitoring: 'پایش و اعلان',
  automation: 'اتوماسیون',
}

const statusMap: Record<string, { color: string; label: string }> = {
  PASSED: { color: 'success', label: 'موفق' },
  FAILED: { color: 'error', label: 'ناموفق' },
  BLOCKED: { color: 'warning', label: 'اجرانشده / نیازمند رانر' },
  QUEUED: { color: 'default', label: 'در صف اجرا' },
  RUNNING: { color: 'processing', label: 'در حال اجرا' },
  COMPLETED: { color: 'success', label: 'پایان اجرا' },
  INTERRUPTED: { color: 'error', label: 'ناتمام' },
}

function StatusTag({ value }: { value: string }) {
  const meta = statusMap[value] ?? { color: 'default', label: value }
  return <Tag color={meta.color}>{meta.label}</Tag>
}

function toFaDate(value?: string) {
  return value ? new Date(value).toLocaleString('fa-IR') : '—'
}

function scopeLabel(scope: string) {
  if (scope === 'all') return 'کل نرم‌افزار'
  if (scope === 'selected') return 'انتخابی'
  if (scope.startsWith('module:')) return moduleNames[scope.slice(7)] ?? scope
  return scope
}

function runPercent(run?: Run | null) {
  return run?.total ? Math.round((run.passed / run.total) * 100) : 0
}

function snippet(text?: string, max = 100) {
  if (!text) return '—'
  return text.length > max ? `${text.slice(0, max)}…` : text
}

function progressStep(run?: Run | null) {
  if (!run) return 0
  if (run.status === 'QUEUED') return 0
  if (run.status === 'RUNNING') return 2
  return 3
}

export function QualityCenterPage() {
  const qc = useQueryClient()
  const [module, setModule] = useState('all')
  const [search, setSearch] = useState('')
  const [selected, setSelected] = useState<string[]>([])
  const [detail, setDetail] = useState<Scenario | null>(null)
  const [activeId, setActiveId] = useState<string | null>(null)
  const [compareId, setCompareId] = useState<string | null>(null)
  const [busyExport, setBusyExport] = useState(false)
  const [progressVisible, setProgressVisible] = useState(false)
  const [pendingRequest, setPendingRequest] = useState<{ ids?: string[]; module?: string } | null>(null)
  const [currentTab, setCurrentTab] = useState('scenarios')
  const [msg, contextHolder] = message.useMessage()

  const catalog = useQuery({
    queryKey: ['quality-catalog'],
    queryFn: () => api<Scenario[]>('/api/v1/admin/quality/catalog'),
  })

  const overview = useQuery({
    queryKey: ['quality-overview'],
    queryFn: () => api<Overview>('/api/v1/admin/quality/overview'),
    refetchInterval: 5000,
  })

  const history = useQuery({
    queryKey: ['quality-history'],
    queryFn: () => api<Run[]>('/api/v1/admin/quality/runs?limit=60'),
    refetchInterval: 5000,
  })

  const active = useQuery({
    queryKey: ['quality-run', activeId],
    queryFn: () => api<Run>(`/api/v1/admin/quality/runs/${activeId}`),
    enabled: !!activeId,
    refetchInterval: (query) => {
      const run = query.state.data as Run | undefined
      return run?.status === 'COMPLETED' || run?.status === 'INTERRUPTED' ? false : 2200
    },
  })

  const compared = useQuery({
    queryKey: ['quality-run', compareId],
    queryFn: () => api<Run>(`/api/v1/admin/quality/runs/${compareId}`),
    enabled: !!compareId,
  })

  const scenarioHistory = useQuery({
    queryKey: ['quality-scenario-history', detail?.id],
    queryFn: () =>
      api<{ run_id: string; started_at: string; status: string; actual?: string; scope: string }[]>(
        `/api/v1/admin/quality/tests/${detail?.id}/history`,
      ),
    enabled: !!detail?.id,
  })

  const launch = useMutation({
    mutationFn: (payload: { ids?: string[]; module?: string }) =>
      api<Run>('/api/v1/admin/quality/runs', {
        method: 'POST',
        body: JSON.stringify({ ...payload, requestKey: crypto.randomUUID() }),
      }),
    onSuccess: (run) => {
      setActiveId(run.id)
      setProgressVisible(true)
      void qc.invalidateQueries({ queryKey: ['quality-history'] })
      void qc.invalidateQueries({ queryKey: ['quality-overview'] })
      msg.success('اجرای آزمون ثبت شد و وضعیت آن در حال به‌روزرسانی است.')
    },
    onError: (error) => {
      msg.error(error instanceof Error ? error.message : 'خطا در شروع اجرای آزمون')
    },
  })

  const allScenarios = catalog.data ?? []
  const modules = useMemo(() => Array.from(new Set(allScenarios.map((item) => item.module))), [allScenarios])

  const filtered = allScenarios.filter((item) => {
    const matchesModule = module === 'all' || item.module === module
    const q = search.trim().toLocaleLowerCase()
    const haystack = `${item.title} ${item.id} ${item.description}`.toLocaleLowerCase()
    return matchesModule && (!q || haystack.includes(q))
  })

  const runs = history.data ?? []
  const latestRun = active.data ?? (activeId ? runs.find((run) => run.id === activeId) : null) ?? overview.data?.latestRun ?? null
  const selectedRun = activeId ? active.data ?? null : latestRun
  const currentFailures = (selectedRun?.results ?? []).filter((item) => item.status === 'FAILED')
  const previousFailures = (compared.data?.results ?? []).filter((item) => item.status === 'FAILED')
  const fixedFailures = previousFailures.filter((previous) =>
    (selectedRun?.results ?? []).some((current) => current.test_id === previous.test_id && current.status === 'PASSED'),
  )
  const newFailures = currentFailures.filter(
    (current) => !(compared.data?.results ?? []).some((previous) => previous.test_id === current.test_id && previous.status === 'FAILED'),
  )

  // The run summary counters may be populated only after completion; item statuses are authoritative while running.
  const countsFromResults = (selectedRun?.results ?? []).reduce(
    (acc, item) => {
      if (item.status === 'PASSED') acc.passed += 1
      if (item.status === 'FAILED') acc.failed += 1
      if (item.status === 'BLOCKED') acc.blocked += 1
      if (item.status === 'RUNNING') acc.running += 1
      if (item.status === 'QUEUED') acc.queued += 1
      return acc
    },
    { passed: 0, failed: 0, blocked: 0, running: 0, queued: 0 },
  )
  const completedCount = countsFromResults.passed + countsFromResults.failed + countsFromResults.blocked
  const runningProgress = selectedRun ? Math.round((completedCount / Math.max(1, selectedRun.total)) * 100) : 0
  const visibleResults = selectedRun?.results ?? []
  const resultSummary = [
    { key: 'PASSED', label: 'موفق', value: countsFromResults.passed, color: 'green' },
    { key: 'FAILED', label: 'ناموفق', value: countsFromResults.failed, color: 'red' },
    { key: 'BLOCKED', label: 'نیازمند رانر', value: countsFromResults.blocked, color: 'gold' },
    { key: 'RUNNING', label: 'در حال بررسی', value: countsFromResults.running, color: 'blue' },
    { key: 'QUEUED', label: 'در صف', value: countsFromResults.queued, color: 'default' },
  ]

  const runInProgress = !!selectedRun && ['QUEUED', 'RUNNING'].includes(selectedRun.status)
  const pendingIds = pendingRequest?.ids ?? []
  const pendingModule = pendingRequest?.module ?? 'all'
  const pendingScenarios = !pendingRequest
    ? []
    : pendingIds.length > 0
      ? allScenarios.filter(item => pendingIds.includes(item.id))
      : allScenarios.filter(item => pendingModule === 'all' || item.module === pendingModule)
  const pendingSafeCount = pendingScenarios.filter(item => item.kind !== 'EXTERNAL').length
  const pendingBlockedCount = pendingScenarios.length - pendingSafeCount


  const openDetailAndTrack = (scenario: Scenario) => {
    setDetail(scenario)
  }

  const runSelectedScenarios = () => {
    if (!selected.length) {
      msg.warning('ابتدا یک یا چند سناریو را انتخاب کنید.')
      return
    }
    setPendingRequest({ ids: selected })
  }

  const exportReport = async (runId: string, format: 'json' | 'csv' | 'html') => {
    setBusyExport(true)
    try {
      const extension = format === 'json' ? '' : `.${format}`
      const response = await fetch(`/api/v1/admin/quality/runs/${runId}/report${extension}`, {
        credentials: 'include',
        cache: 'no-store',
      })
      if (!response.ok) throw new Error(`HTTP ${response.status}`)
      const blob = await response.blob()
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `sakhtyar-quality-${runId}.${format}`
      link.click()
      URL.revokeObjectURL(url)
    } catch (error) {
      msg.error(error instanceof Error ? error.message : 'دریافت گزارش ناموفق بود')
    } finally {
      setBusyExport(false)
    }
  }

  const historyColumns = [
    {
      title: 'تاریخ',
      dataIndex: 'started_at',
      render: (value: string) => toFaDate(value),
    },
    {
      title: 'دامنه اجرا',
      dataIndex: 'scope',
      render: (value: string) => scopeLabel(value),
    },
    {
      title: 'نسخه',
      dataIndex: 'git_commit',
      render: (value: string) => <code dir="ltr">{value && value !== 'unknown' ? value.slice(0, 9) : 'نامشخص'}</code>,
    },
    {
      title: 'نتیجه',
      render: (_: unknown, run: Run) => (
        <Space size={4} wrap>
          <Tag color="success">{run.passed} موفق</Tag>
          <Tag color="error">{run.failed} ناموفق</Tag>
          <Tag color="warning">{run.blocked} اجرانشده</Tag>
        </Space>
      ),
    },
    {
      title: 'وضعیت',
      dataIndex: 'status',
      render: (value: string) => <StatusTag value={value} />,
    },
    {
      title: 'عملیات',
      render: (_: unknown, run: Run) => (
        <Space wrap>
          <Button size="small" onClick={() => { setActiveId(run.id); setProgressVisible(true) }}>
            جزئیات
          </Button>
          <Button size="small" onClick={() => setCompareId(run.id)}>
            مقایسه
          </Button>
          <Button size="small" loading={busyExport} onClick={() => void exportReport(run.id, 'json')} icon={<DownloadOutlined />}>
            JSON
          </Button>
        </Space>
      ),
    },
  ]

  return (
    <div className="sakhtyar-page-stack sy-quality-v3" dir="rtl">
      {contextHolder}

      <Card
        style={{
          borderRadius: 24,
          border: '1px solid #d9ecf7',
          background: 'linear-gradient(135deg, #103d57 0%, #1a5570 55%, #1f7382 100%)',
        }}
      >
        <Row gutter={[16, 16]} align="middle" justify="space-between">
          <Col xs={24} lg={17}>
            <Space direction="vertical" size={4} style={{ width: '100%' }}>
              <Typography.Text style={{ color: '#bfe4ef' }}>SakhtYar Quality & Test Center</Typography.Text>
              <Typography.Title level={2} style={{ color: 'white', margin: 0 }}>
                مرکز آزمون و تضمین کیفیت ساخت‌یار
              </Typography.Title>
              <Typography.Paragraph style={{ color: '#def2f8', marginBottom: 0 }}>
                این بخش برای اجرای سناریوهای آزمون، مشاهده تاریخچه، مقایسه اجراها و دریافت گزارش ساخته شده است. اجرای گروهی،
                اجرای تک‌سناریو، پیگیری وضعیت و تحلیل تغییرات همگی از همین مرکز انجام می‌شوند.
              </Typography.Paragraph>
            </Space>
          </Col>
          <Col xs={24} lg={7}>
            <Space wrap style={{ width: '100%', justifyContent: 'flex-end' }}>
              <Button
                size="large"
                icon={<ReloadOutlined />}
                onClick={() => {
                  void qc.invalidateQueries({ queryKey: ['quality-overview'] })
                  void qc.invalidateQueries({ queryKey: ['quality-history'] })
                  if (activeId) void qc.invalidateQueries({ queryKey: ['quality-run', activeId] })
                }}
              >
                به‌روزرسانی
              </Button>
              {selectedRun ? (
                <Button type="primary" size="large" icon={<BarChartOutlined />} onClick={() => setProgressVisible(true)}>
                  وضعیت اجرای اخیر
                </Button>
              ) : null}
            </Space>
          </Col>
        </Row>
      </Card>

      <Alert
        showIcon
        type="info"
        message="راهنمای مهم"
        description="آزمون‌های ROUTE و SQL فقط سلامت فنی مسیرها یا ساختار داده را بررسی می‌کنند. سناریوهای عملکردی، امنیتی و مرورگری تا زمان آماده‌شدن رانر ایزوله با وضعیت «اجرانشده / نیازمند رانر» ثبت می‌شوند و نباید موفق تلقی شوند."
      />

      {catalog.isError || overview.isError ? (
        <Alert
          type="error"
          showIcon
          message="ارتباط با API مرکز آزمون برقرار نشد."
          description="Migrationها، سطح دسترسی مدیر، راه‌اندازی دوباره بک‌اند و دسترس‌پذیری مسیرهای /api/v1/admin/quality را بررسی کنید."
        />
      ) : null}

      <Row gutter={[12, 12]}>
        <Col xs={12} lg={6}>
          <Card bordered>
            <Statistic title="کل سناریوها" value={overview.data?.totalScenarios ?? 0} prefix={<AuditOutlined />} />
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card bordered>
            <Statistic title="کنترل‌های قابل اجرا" value={overview.data?.runnableSafeScenarios ?? 0} prefix={<CheckCircleOutlined />} />
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card bordered>
            <Statistic title="نیازمند رانر ایزوله" value={overview.data?.requiresIsolatedRunner ?? 0} prefix={<WarningOutlined />} />
          </Card>
        </Col>
        <Col xs={12} lg={6}>
          <Card bordered>
            <Statistic title="تعداد اجراهای ثبت‌شده" value={overview.data?.totalRuns ?? 0} prefix={<HistoryOutlined />} />
          </Card>
        </Col>
      </Row>

      <Tabs
        activeKey={currentTab}
        onChange={setCurrentTab}
        items={[
          {
            key: 'scenarios',
            label: 'سناریوها و اجرای آزمون',
            children: (
              <Space direction="vertical" size={16} style={{ width: '100%' }}>
                <Row gutter={[12, 12]}>
                  <Col xs={24} xl={8}>
                    <Card title="انتخاب دامنه اجرا" extra={<DatabaseOutlined />}>
                      <Space direction="vertical" size={12} style={{ width: '100%' }}>
                        <Select
                          style={{ width: '100%' }}
                          value={module}
                          onChange={setModule}
                          options={[
                            { value: 'all', label: 'تمام بخش‌های نرم‌افزار' },
                            ...modules.map((item) => ({ value: item, label: moduleNames[item] ?? item })),
                          ]}
                        />
                        <Input
                          value={search}
                          onChange={(event) => setSearch(event.target.value)}
                          placeholder="جستجو در عنوان، شناسه یا توضیحات"
                          prefix={<SearchOutlined />}
                        />
                        <Typography.Text type="secondary">
                          می‌توانی همه سامانه، یک حوزه مشخص یا چند سناریوی منتخب را اجرا کنی.
                        </Typography.Text>
                      </Space>
                    </Card>
                  </Col>
                  <Col xs={24} xl={16}>
                    <Row gutter={[12, 12]}>
                      <Col xs={24} md={8}>
                        <Card hoverable>
                          <Space direction="vertical" size={10} style={{ width: '100%' }}>
                            <Typography.Title level={5} style={{ margin: 0 }}>
                              اجرای کل سامانه
                            </Typography.Title>
                            <Typography.Text type="secondary">
                              تمام سناریوهای موجود را با دامنه فعلی اجرا می‌کند و نتیجه را در تاریخچه ثبت می‌کند.
                            </Typography.Text>
                            <Button type="primary" icon={<PlayCircleOutlined />} loading={launch.isPending} onClick={() => setPendingRequest({ module })}>
                              اجرای کل {module === 'all' ? 'نرم‌افزار' : moduleNames[module] ?? module}
                            </Button>
                          </Space>
                        </Card>
                      </Col>
                      <Col xs={24} md={8}>
                        <Card hoverable>
                          <Space direction="vertical" size={10} style={{ width: '100%' }}>
                            <Typography.Title level={5} style={{ margin: 0 }}>
                              اجرای موارد انتخابی
                            </Typography.Title>
                            <Typography.Text type="secondary">
                              برای تست سریع یا بازبینی هدفمند چند سناریوی خاص مناسب است.
                            </Typography.Text>
                            <Button loading={launch.isPending} disabled={!selected.length} onClick={runSelectedScenarios}>
                              اجرای {selected.length} سناریوی انتخاب‌شده
                            </Button>
                          </Space>
                        </Card>
                      </Col>
                      <Col xs={24} md={8}>
                        <Card hoverable>
                          <Space direction="vertical" size={10} style={{ width: '100%' }}>
                            <Typography.Title level={5} style={{ margin: 0 }}>
                              وضعیت اجرای اخیر
                            </Typography.Title>
                            <Typography.Text type="secondary">
                              اگر اجرا شروع شده باشد، از اینجا روند کار و تعداد موارد بررسی‌شده را می‌بینی.
                            </Typography.Text>
                            <Button icon={<ClockCircleOutlined />} disabled={!selectedRun} onClick={() => setProgressVisible(true)}>
                              مشاهده وضعیت اجرا
                            </Button>
                          </Space>
                        </Card>
                      </Col>
                    </Row>
                  </Col>
                </Row>

                <Card title={`فهرست سناریوها (${filtered.length})`} extra={<Typography.Text type="secondary">برای مشاهده جزئیات روی نام سناریو بزن</Typography.Text>}>
                  <Table<Scenario>
                    rowKey="id"
                    dataSource={filtered}
                    pagination={{ pageSize: 15, showSizeChanger: true }}
                    scroll={{ x: 1200 }}
                    rowSelection={{
                      selectedRowKeys: selected,
                      onChange: (keys) => setSelected(keys.map(String)),
                      preserveSelectedRowKeys: true,
                    }}
                    columns={[
                      {
                        title: 'شناسه',
                        dataIndex: 'id',
                        width: 120,
                        render: (value: string) => <code dir="ltr">{value}</code>,
                      },
                      {
                        title: 'نام سناریو',
                        dataIndex: 'title',
                        width: 240,
                        render: (value: string, row: Scenario) => (
                          <Button type="link" onClick={() => openDetailAndTrack(row)} style={{ paddingInline: 0 }}>
                            {value}
                          </Button>
                        ),
                      },
                      {
                        title: 'ماژول',
                        dataIndex: 'module',
                        width: 180,
                        render: (value: string) => moduleNames[value] ?? value,
                      },
                      {
                        title: 'نوع آزمون',
                        dataIndex: 'kind',
                        width: 160,
                        render: (value: Scenario['kind']) => (
                          <Tag color={value === 'EXTERNAL' ? 'gold' : value === 'SQL' ? 'purple' : 'blue'}>
                            {value === 'EXTERNAL' ? 'نیازمند رانر' : value === 'SQL' ? 'کنترل SQL' : 'کنترل Route'}
                          </Tag>
                        ),
                      },
                      {
                        title: 'شرح کوتاه',
                        dataIndex: 'description',
                        width: 360,
                        render: (value: string) => <Typography.Text type="secondary">{snippet(value, 95)}</Typography.Text>,
                      },
                      {
                        title: 'عملیات',
                        width: 180,
                        render: (_: unknown, row: Scenario) => (
                          <Space>
                            <Button size="small" onClick={() => openDetailAndTrack(row)}>
                              جزئیات
                            </Button>
                            <Button size="small" type="primary" loading={launch.isPending} onClick={() => setPendingRequest({ ids: [row.id] })}>
                              اجرای تست
                            </Button>
                          </Space>
                        ),
                      },
                    ]}
                  />
                </Card>
              </Space>
            ),
          },
          {
            key: 'history',
            label: 'تاریخچه و روند',
            children: (
              <Space direction="vertical" size={16} style={{ width: '100%' }}>
                <Row gutter={[12, 12]}>
                  <Col xs={24} lg={16}>
                    <Card title="روند تعداد مشکلات در اجراهای اخیر">
                      {runs.length ? (
                        <div style={{ display: 'flex', alignItems: 'flex-end', gap: 12, minHeight: 165, overflowX: 'auto', paddingBottom: 10 }}>
                          {runs
                            .slice(0, 14)
                            .reverse()
                            .map((run, index) => {
                              const maxFailed = Math.max(1, ...runs.map((item) => item.failed))
                              return (
                                <div
                                  key={run.id}
                                  onClick={() => { setActiveId(run.id); setProgressVisible(true) }}
                                  title={`${toFaDate(run.started_at)} | ${run.failed} ناموفق | ${run.blocked} بلاک‌شده`}
                                  style={{ minWidth: 38, cursor: 'pointer', textAlign: 'center' }}
                                >
                                  <div style={{ fontSize: 11, marginBottom: 6 }}>{run.failed}</div>
                                  <div
                                    style={{
                                      height: Math.max(6, Math.round((110 * run.failed) / maxFailed)),
                                      background: run.failed ? '#e05d64' : '#53a487',
                                      borderRadius: 6,
                                    }}
                                  />
                                  <Typography.Text type="secondary" style={{ fontSize: 10 }}>
                                    {index + 1}
                                  </Typography.Text>
                                </div>
                              )
                            })}
                        </div>
                      ) : (
                        <Empty description="هنوز اجرایی ثبت نشده است" />
                      )}
                      <Typography.Text type="secondary">
                        هر ستون نشان‌دهنده تعداد سناریوهای ناموفق در یک اجرا است. برای قضاوت درست، دامنه اجرا و تعداد موارد «اجرانشده» را نیز در جدول پایین بررسی کن.
                      </Typography.Text>
                    </Card>
                  </Col>
                  <Col xs={24} lg={8}>
                    <Card title="آخرین نتیجه">
                      <Statistic title="موفق از مجموع" value={overview.data?.latestRun?.passed ?? 0} suffix={`/ ${overview.data?.latestRun?.total ?? 0}`} />
                      <Progress percent={runPercent(overview.data?.latestRun ?? undefined)} status="normal" />
                      <Space wrap>
                        <Tag color="red">{overview.data?.latestRun?.failed ?? 0} خطا</Tag>
                        <Tag color="gold">{overview.data?.latestRun?.blocked ?? 0} اجرا‌نشده</Tag>
                      </Space>
                    </Card>
                  </Col>
                </Row>

                <Table<Run> rowKey="id" dataSource={runs} scroll={{ x: 1050 }} pagination={{ pageSize: 12 }} columns={historyColumns} />
              </Space>
            ),
          },
          {
            key: 'results',
            label: 'نتایج و مقایسه',
            children: (
              <Space direction="vertical" size={16} style={{ width: '100%' }}>
                <Card title="انتخاب اجرا برای بررسی و مقایسه">
                  <Space wrap>
                    <Select
                      style={{ width: 360 }}
                      placeholder="اجرای اصلی"
                      value={activeId ?? selectedRun?.id}
                      onChange={setActiveId}
                      options={runs.map((run) => ({ value: run.id, label: `${toFaDate(run.started_at)} | ${scopeLabel(run.scope)} | ${run.failed} خطا` }))}
                    />
                    <Select
                      style={{ width: 360 }}
                      allowClear
                      placeholder="مقایسه با اجرای دیگر"
                      value={compareId}
                      onChange={setCompareId}
                      options={runs
                        .filter((run) => run.id !== activeId)
                        .map((run) => ({ value: run.id, label: `${toFaDate(run.started_at)} | ${scopeLabel(run.scope)} | ${run.failed} خطا` }))}
                    />
                  </Space>
                </Card>

                {selectedRun ? (
                  <Card
                    title={
                      <Space>
                        نتیجه اجرای انتخاب‌شده
                        <StatusTag value={selectedRun.status} />
                      </Space>
                    }
                    extra={
                      <Space wrap>
                        <Button disabled={busyExport} onClick={() => void exportReport(selectedRun.id, 'json')}>
                          JSON
                        </Button>
                        <Button disabled={busyExport} onClick={() => void exportReport(selectedRun.id, 'csv')}>
                          CSV
                        </Button>
                        <Button disabled={busyExport} onClick={() => void exportReport(selectedRun.id, 'html')}>
                          HTML / چاپ PDF
                        </Button>
                      </Space>
                    }
                  >
                    <Row gutter={[12, 12]}>
                      <Col xs={24} md={8}>
                        <Statistic title="موفق" value={selectedRun.passed} valueStyle={{ color: '#389e63' }} />
                      </Col>
                      <Col xs={24} md={8}>
                        <Statistic title="ناموفق" value={selectedRun.failed} valueStyle={{ color: '#cf444c' }} />
                      </Col>
                      <Col xs={24} md={8}>
                        <Statistic title="اجرانشده" value={selectedRun.blocked} />
                      </Col>
                    </Row>

                    <Descriptions size="small" column={{ xs: 1, lg: 2 }} style={{ marginTop: 16 }}>
                      <Descriptions.Item label="زمان اجرا">{toFaDate(selectedRun.started_at)}</Descriptions.Item>
                      <Descriptions.Item label="محیط">{selectedRun.environment}</Descriptions.Item>
                      <Descriptions.Item label="Commit">
                        <code dir="ltr">{selectedRun.git_commit ?? 'unknown'}</code>
                      </Descriptions.Item>
                      <Descriptions.Item label="مجری">{selectedRun.requested_by}</Descriptions.Item>
                    </Descriptions>

                    {compared.data ? (
                      <Alert
                        type="info"
                        showIcon
                        message={`در مقایسه با اجرای انتخابی، ${fixedFailures.length} مورد از خطاهای قبلی دیگر تکرار نشده و ${newFailures.length} خطای جدید ثبت شده است.`}
                        description="این مقایسه فقط روی سناریوهای مشترک معتبر است. موارد بلاک‌شده یا سناریوهای غایب، رفع قطعی خطا محسوب نمی‌شوند."
                        style={{ marginTop: 16 }}
                      />
                    ) : null}

                    <Divider />

                    <Table<Result>
                      rowKey="test_id"
                      dataSource={selectedRun.results ?? []}
                      pagination={{ pageSize: 15 }}
                      scroll={{ x: 1100 }}
                      columns={[
                        { title: 'شناسه', dataIndex: 'test_id', width: 110, render: (value: string) => <code dir="ltr">{value}</code> },
                        { title: 'آزمون', dataIndex: 'title', width: 230 },
                        { title: 'نتیجه', dataIndex: 'status', width: 140, render: (value: string) => <StatusTag value={value} /> },
                        { title: 'خروجی واقعی', dataIndex: 'actual', width: 320 },
                        {
                          title: 'جزئیات فنی',
                          dataIndex: 'detail',
                          width: 460,
                          render: (value: string) => <Typography.Text type="secondary">{value || '—'}</Typography.Text>,
                        },
                      ]}
                    />
                  </Card>
                ) : (
                  <Empty description="یک اجرا را برای مشاهده نتیجه انتخاب کنید" />
                )}
              </Space>
            ),
          },
          {
            key: 'help',
            label: 'راهنما و دامنه آزمون‌ها',
            children: (
              <Card title="نحوه استفاده از این مرکز">
                <List
                  dataSource={[
                    'از تب «سناریوها و اجرای آزمون» دامنه مناسب را انتخاب کن؛ می‌توانی کل سامانه، یک حوزه خاص یا چند سناریوی منتخب را اجرا کنی.',
                    'پس از شروع اجرا، پنجره وضعیت باز می‌شود و روند ثبت، اجرا و جمع‌بندی آزمون‌ها را نشان می‌دهد.',
                    'در تب «نتایج و مقایسه» می‌توانی خروجی هر سناریو، جزئیات فنی و تفاوت با اجرای قبلی را بررسی کنی.',
                    'در تب «تاریخچه و روند»، مسیر کاهش یا افزایش خطاها در اجراهای مختلف ثبت شده و برای گزارش‌گیری قابل استفاده است.',
                    'برای دریافت PDF، گزارش HTML را دانلود کن و از مرورگر گزینه Print / Save as PDF را بزن.',
                    'اگر نسخه کد در گزارش با مقدار unknown نمایش داده می‌شود، متغیر APP_QUALITY_GIT_COMMIT را در زمان اجرای بک‌اند تنظیم کن.',
                  ]}
                  renderItem={(item, index) => (
                    <List.Item>
                      <Space align="start">
                        <Tag color="blue">{index + 1}</Tag>
                        <Typography.Text>{item}</Typography.Text>
                      </Space>
                    </List.Item>
                  )}
                />
                <Divider />
                <Alert
                  type="warning"
                  showIcon
                  message="تفسیر صحیح نتایج"
                  description="موفق‌بودن آزمون‌های SQL و ROUTE به‌معنی تأیید کامل رفتار محصول نیست. برای سناریوهای امنیتی، محاسباتی، یکپارچگی و مرورگر، رانر ایزوله و داده آزمایشی اختصاصی لازم است."
                />
              </Card>
            ),
          },
        ]}
      />

      <Drawer
        title={detail ? `${detail.id} — ${detail.title}` : 'جزئیات سناریو'}
        open={!!detail}
        onClose={() => setDetail(null)}
        width={Math.min(720, typeof window === 'undefined' ? 720 : window.innerWidth - 30)}
        destroyOnHidden
      >
        {detail ? (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Space wrap>
              <Tag color="blue">{moduleNames[detail.module] ?? detail.module}</Tag>
              <Tag>{detail.coverage}</Tag>
              <Tag>{detail.risk}</Tag>
              <Tag color={detail.kind === 'EXTERNAL' ? 'gold' : detail.kind === 'SQL' ? 'purple' : 'cyan'}>
                {detail.kind === 'EXTERNAL' ? 'نیازمند رانر' : detail.kind === 'SQL' ? 'SQL' : 'ROUTE'}
              </Tag>
            </Space>

            <Card size="small" title="شرح سناریو">
              <Typography.Paragraph style={{ marginBottom: 0 }}>{detail.description}</Typography.Paragraph>
            </Card>

            <Card size="small" title="نتیجه مورد انتظار">
              <Typography.Paragraph style={{ marginBottom: 0 }}>{detail.expected}</Typography.Paragraph>
            </Card>

            <Card size="small" title="اطلاعات فنی">
              <Descriptions column={1} size="small">
                <Descriptions.Item label="شناسه">
                  <code dir="ltr">{detail.id}</code>
                </Descriptions.Item>
                <Descriptions.Item label="دامنه فنی">{detail.coverage}</Descriptions.Item>
                <Descriptions.Item label="هدف فنی">
                  <code dir="ltr">{detail.target || 'نیازمند رانر و داده آزمایشی'}</code>
                </Descriptions.Item>
                <Descriptions.Item label="منبع تعریف">{detail.source || '—'}</Descriptions.Item>
              </Descriptions>
            </Card>

            <Card size="small" title="تاریخچه همین سناریو">
              <Table
                size="small"
                rowKey="run_id"
                dataSource={scenarioHistory.data ?? []}
                pagination={{ pageSize: 5 }}
                columns={[
                  { title: 'زمان', dataIndex: 'started_at', render: (value: string) => toFaDate(value) },
                  { title: 'دامنه', dataIndex: 'scope', render: (value: string) => scopeLabel(value) },
                  { title: 'وضعیت', dataIndex: 'status', render: (value: string) => <StatusTag value={value} /> },
                  {
                    title: 'عملیات',
                    render: (_: unknown, row: { run_id: string }) => (
                      <Button
                        size="small"
                        onClick={() => {
                          setActiveId(row.run_id)
                          setDetail(null)
                        }}
                      >
                        مشاهده اجرا
                      </Button>
                    ),
                  },
                ]}
              />
            </Card>

            <Alert
              type={detail.kind === 'EXTERNAL' ? 'warning' : 'info'}
              showIcon
              message={detail.kind === 'EXTERNAL' ? 'این سناریو برای اجرای واقعی به رانر ایزوله نیاز دارد.' : 'این سناریو یک کنترل فنی سبک است و معادل آزمون انتها‌به‌انتها نیست.'}
            />

            <Button
              type="primary"
              block
              loading={launch.isPending}
              icon={<PlayCircleOutlined />}
              onClick={() => {
                setPendingRequest({ ids: [detail.id] })
                setDetail(null)
              }}
            >
              اجرای همین سناریو
            </Button>
          </Space>
        ) : null}
      </Drawer>

      <Modal
        title="تأیید دامنه اجرای آزمون"
        open={!!pendingRequest}
        onCancel={() => setPendingRequest(null)}
        okText="ثبت و شروع اجرا"
        cancelText="بازگشت"
        okButtonProps={{ loading: launch.isPending, disabled: !pendingScenarios.length }}
        onOk={() => {
          if (!pendingRequest) return
          const request = pendingRequest
          setPendingRequest(null)
          launch.mutate(request)
        }}
        width={700}
      >
        <Space direction="vertical" size={14} style={{ width: '100%' }}>
          <Alert type="info" showIcon message="این عملیات روی داده‌های واقعی تغییری ایجاد نمی‌کند" description="فقط سناریوهای ثبت‌شده در مرکز آزمون انتخاب می‌شوند. سناریوهای عملیاتی بدون رانر ایزوله با وضعیت «اجرانشده» ثبت خواهند شد." />
          <Row gutter={[10, 10]}>
            <Col span={8}><Card size="small"><Statistic title="تعداد انتخاب‌شده" value={pendingScenarios.length} /></Card></Col>
            <Col span={8}><Card size="small"><Statistic title="کنترل‌های قابل اجرا" value={pendingSafeCount} /></Card></Col>
            <Col span={8}><Card size="small"><Statistic title="نیازمند رانر" value={pendingBlockedCount} /></Card></Col>
          </Row>
          <Card size="small" title="سناریوهای انتخاب‌شده">
            <div className="sy-quality-confirm-list">
              {pendingScenarios.map(item => <div key={item.id} className="sy-quality-confirm-item"><code dir="ltr">{item.id}</code><span>{item.title}</span><Tag color={item.kind === 'EXTERNAL' ? 'gold' : 'blue'}>{item.kind === 'EXTERNAL' ? 'اجرانشده' : 'قابل اجرا'}</Tag></div>)}
            </div>
          </Card>
          <Typography.Text type="secondary">نتیجه هر کنترل پس از اتمام در PostgreSQL ثبت خواهد شد و در تاریخچه باقی می‌ماند.</Typography.Text>
        </Space>
      </Modal>

      <Modal
        title="وضعیت اجرای آزمون"
        open={progressVisible}
        onCancel={() => setProgressVisible(false)}
        footer={
          <Space>
            <Button onClick={() => setProgressVisible(false)}>بستن</Button>
            {selectedRun ? (
              <Button type="primary" onClick={() => { setProgressVisible(false); setCurrentTab('results') }}>
                مشاهده در صفحه نتایج
              </Button>
            ) : null}
          </Space>
        }
        width={760}
      >
        {selectedRun ? (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            <Alert
              type={runInProgress ? 'info' : selectedRun.status === 'COMPLETED' ? 'success' : 'warning'}
              showIcon
              message={runInProgress ? 'اجرای آزمون در حال انجام است' : 'آخرین وضعیت اجرای انتخاب‌شده'}
              description={`شناسه اجرا: ${selectedRun.id} | دامنه: ${scopeLabel(selectedRun.scope)} | زمان شروع: ${toFaDate(selectedRun.started_at)}`}
            />

            <Steps
              current={progressStep(selectedRun)}
              items={[
                { title: 'ثبت درخواست', description: 'درخواست اجرا در سامانه ثبت شد.' },
                { title: 'صف و آماده‌سازی', description: 'رکوردهای آزمون ایجاد و برای اجرا آماده شدند.' },
                { title: 'اجرای کنترل‌ها', description: 'سناریوها در حال بررسی هستند.' },
                { title: 'جمع‌بندی', description: 'نتیجه نهایی و گزارش اجرا ثبت شده است.' },
              ]}
            />

            <Card>
              <Row gutter={[12, 12]}>
                <Col xs={24} md={10}>
                  <Typography.Title level={5}>پیشرفت ثبت نتیجه‌ها</Typography.Title>
                  <Progress percent={runningProgress} status={selectedRun.status === 'INTERRUPTED' ? 'exception' : 'active'} />
                  <Space wrap>
                    <StatusTag value={selectedRun.status} />
                    <Tag color="success">{countsFromResults.passed} موفق</Tag>
                    <Tag color="error">{countsFromResults.failed} ناموفق</Tag>
                    <Tag color="warning">{countsFromResults.blocked} اجرانشده</Tag>
                  </Space>
                </Col>
                <Col xs={24} md={14}>
                  <Descriptions size="small" column={1} bordered>
                    <Descriptions.Item label="محیط اجرا">{selectedRun.environment}</Descriptions.Item>
                    <Descriptions.Item label="مجری">{selectedRun.requested_by}</Descriptions.Item>
                    <Descriptions.Item label="نسخه">
                      <code dir="ltr">{selectedRun.git_commit || 'unknown'}</code>
                    </Descriptions.Item>
                  </Descriptions>
                </Col>
              </Row>
            </Card>

            <Card title="خلاصه آنچه تاکنون انجام شده">
              <List
                dataSource={[
                  `تعداد کل سناریوهای این اجرا: ${selectedRun.total}`,
                  `تعداد سناریوهای بررسی‌شده تا این لحظه: ${completedCount}`,
                  countsFromResults.failed > 0 ? `در حال حاضر ${countsFromResults.failed} مورد خطا ثبت شده است.` : 'تا این لحظه نتیجه شکست‌خورده‌ای ثبت نشده است.',
                  countsFromResults.blocked > 0 ? `${countsFromResults.blocked} سناریو نیازمند رانر ایزوله یا داده آزمایشی هستند.` : 'تا این لحظه سناریوی بلاک‌شده‌ای ثبت نشده است.',
                ]}
                renderItem={(item) => (
                  <List.Item>
                    <Space>
                      <CheckCircleOutlined style={{ color: '#389e63' }} />
                      <span>{item}</span>
                    </Space>
                  </List.Item>
                )}
              />
            </Card>
            <Card title="وضعیت سناریوها به تفکیک" extra={<Typography.Text type="secondary">به‌روزرسانی خودکار از دیتابیس</Typography.Text>}>
              <Space wrap style={{ marginBottom: 12 }}>
                {resultSummary.map(item => <Tag key={item.key} color={item.color}>{item.value} {item.label}</Tag>)}
              </Space>
              <div className="sy-quality-run-results">
                {visibleResults.length ? visibleResults.map(item => <div key={item.test_id} className="sy-quality-run-item">
                  <span className={`sy-quality-run-dot is-${item.status.toLowerCase()}`} aria-hidden="true" />
                  <div className="sy-quality-run-main">
                    <strong>{item.title}</strong>
                    <code dir="ltr">{item.test_id}</code>
                    {item.status === 'FAILED' ? <small>{snippet(item.actual, 140)}</small> : null}
                  </div>
                  <StatusTag value={item.status} />
                </div>) : <Empty description="دریافت جزئیات اجرا در حال انجام است" />}
              </div>
              <Typography.Text type="secondary" style={{ display: 'block', marginTop: 12 }}>
                تیک سبز تنها برای آزمون واقعاً موفق نمایش داده می‌شود؛ وضعیت «نیازمند رانر» به معنای تست‌شدن قابلیت نیست.
              </Typography.Text>
            </Card>
          </Space>
        ) : (
          <Empty description="هنوز اجرایی برای نمایش انتخاب نشده است" />
        )}
      </Modal>
    </div>
  )
}
