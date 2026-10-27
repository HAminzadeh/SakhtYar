import { useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert, App as AntdApp, Button, Card, Col, Descriptions, Drawer, Empty,
  Input, Modal, Progress, Row, Select, Space, Statistic, Table, Tabs, Tag,
  Tooltip, Typography,
} from 'antd'
import {
  CheckCircleOutlined, ClockCircleOutlined, CloudDownloadOutlined,
  DatabaseOutlined, ExperimentOutlined, FileSearchOutlined, FilterOutlined,
  InfoCircleOutlined, LockOutlined, PlusOutlined, ReloadOutlined,
  SafetyCertificateOutlined, SearchOutlined, ThunderboltOutlined,
} from '@ant-design/icons'
import { api } from '../api/client'

type Workspace = { id: string; code: string; title: string; state: string; owner_username?: string; created_at: string }
type Scenario = { id: string; title: string; phase: string; objective: string }
type RunRow = { id: string; workspace_id: string; state: string; requested_at: string; scenario_count: number; git_commit?: string }
type ResultRow = { scenario_id: string; phase: string; title: string; state: string; summary: string }
type StepRow = { scenario_id: string; step_no: number; step_code: string; title: string; state: string; detail?: string; started_at?: string; finished_at?: string }
type RunProgress = { percent: number; totalSteps: number; completedSteps: number; kind: string; businessWorkflowsExecuted: boolean; currentStep?: StepRow | null }
type Run = { id: string; state: string; workspace_id: string; gate_reason: string; requested_at?: string; git_commit?: string; results: ResultRow[]; steps?: StepRow[]; progress?: RunProgress }
type Readiness = {
  migration58Installed: boolean; taggedBusinessTables: number; taggedBusinessTablesExpected?: number;
  rlsAndCrossScopeFkEnforcementVerified: boolean; outboundGuardVerified: boolean;
  allBusinessQueriesScopeGuarded: boolean; automatedTestBusinessWritesPermitted: boolean;
  gateReason: string
}
type Evidence = { rowsByTable: Record<string, number>; workspaceId: string; warning: string }
type Capability = {
  businessDataWriteEnabled: boolean; isolationVerified: boolean;
  sessionProfileSwitchEnabled: boolean; gitCommit?: string;
  phaseStatus: Array<{ phase: string; title: string; status: string }>
}
type ScenarioCheck = {
  id: string; phase: string; title: string;
  requiredTables: string[]; missingTables: string[];
  requiredRoutes: string[]; missingRoutes: string[];
  schemaRoutesReady: boolean; businessE2EEnabled: boolean
}
type RunComparison = { left: Run; right: Run; note: string }
type BusinessE2EResult = { scenario_id: string; state: string; summary: string; evidence?: unknown }
type BusinessE2EStep = { scenario_id: string; step_no: number; title: string; state: string; detail?: string }
type DetailedE2EPlan = { id: string; phase: string; title: string; description: string;
  dependencies: string[]; requiredTables: string[]; requiredRoutes: string[];
  preflightSteps: string[]; businessSteps: string; businessStatus: string }
type BusinessE2ERun = { id: string; state: string; scenario_count: number; completedScenarios: number; progressPercent: number; totalSteps?: number; completedSteps?: number;
  requested_at: string; results: BusinessE2EResult[]; steps: BusinessE2EStep[] }
type BusinessE2ECoverage = { implementedBusinessE2e: string[]; completeBusinessE2eCount: number; limitedBusinessE2eCount?: number; totalBusinessE2eCount: number; note: string }

type CoreFixtureReadiness = {
  coreFixturePermitted: boolean; runtimeDbRoleNonBypass: boolean;
  corePolicyCoverage: boolean; migration60StructureAvailable: boolean;
  fullApplicationIsolationVerified: boolean; note: string;
  blockers?: string[]; runtimeDbUser?: string
}
type CoreFixture = {
  id: string; case_id: string; project_code: string; title: string;
  property_id: string; owner_a_id: string; owner_b_id: string;
  owner_count: number; created_at: string; status: string
}


const endpoint = '/api/v1/admin/simulations'
const stageNames: Record<string, string> = {
  T01: 'زیرساخت تفکیک داده', T02: 'کاتالوگ ۲۴ سناریو',
  T03: 'شواهد و گزارش', T04: 'قابلیت‌های پیشرفته',
  S02: 'امنیت و یکپارچگی', S03: 'چرخه دانش',
  S04: 'گردش‌کار ساخت', S05: 'عملیات و هوش مصنوعی',
}
const phases = ['S02', 'S03', 'S04', 'S05']
const statusMeta: Record<string, { color: string; text: string }> = {
  BLOCKED: { color: 'warning', text: 'مسدود - اجرا نشده' },
  COMPLETED: { color: 'success', text: 'ثبت بررسی کامل شد' },
  PASSED: { color: 'success', text: 'موفق' },
  FAILED: { color: 'error', text: 'ناموفق' },
  INTERRUPTED: { color: 'error', text: 'ناتمام' },
  RUNNING: { color: 'processing', text: 'در حال اجرا' },
  QUEUED: { color: 'default', text: 'در صف' },
}
function status(value?: string) {
  const item = statusMeta[value ?? ''] ?? { color: 'default', text: value || 'نامشخص' }
  return <Tag color={item.color}>{item.text}</Tag>
}
function dateFa(value?: string) { return value ? new Date(value).toLocaleString('fa-IR') : '—' }
function safeMessage(error: unknown) { return error instanceof Error ? error.message : 'خطا در ارتباط با سرور' }
function downloadJson(payload: unknown, filename: string) {
  const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}

export function SimulationCenterPage() {
  const { message } = AntdApp.useApp()
  const q = useQueryClient()
  const [workspaceId, setWorkspaceId] = useState<string>()
  const [runId, setRunId] = useState<string>()
  const [selectedIds, setSelectedIds] = useState<string[] | null>(null)
  const [phase, setPhase] = useState('ALL')
  const [search, setSearch] = useState('')
  const [tab, setTab] = useState('scenarios')
  const [drawer, setDrawer] = useState<Scenario | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [newCode, setNewCode] = useState('')
  const [newTitle, setNewTitle] = useState('')
  const [compareId, setCompareId] = useState<string>()
  const [stepScenarioId, setStepScenarioId] = useState<string>()
  const [downloading, setDownloading] = useState(false)
  const [resultModalOpen, setResultModalOpen] = useState(false)
  const [e2eRunId, setE2eRunId] = useState<string>()
  const [e2eModalOpen, setE2eModalOpen] = useState(false)
  const [e2eSelectedCode, setE2eSelectedCode] = useState<string>()

  const capabilities = useQuery({ queryKey: ['sim-capabilities'], queryFn: () => api<Capability>(`${endpoint}/capabilities`) })
  const readiness = useQuery({ queryKey: ['sim-readiness'], queryFn: () => api<Readiness>(`${endpoint}/readiness`) })
  const workspaces = useQuery({ queryKey: ['sim-workspaces'], queryFn: () => api<Workspace[]>(`${endpoint}/workspaces`) })
  const catalog = useQuery({ queryKey: ['sim-catalog'], queryFn: () => api<Scenario[]>(`${endpoint}/catalog`) })
  const checks = useQuery({ queryKey: ['sim-checks'], queryFn: () => api<ScenarioCheck[]>(`${endpoint}/scenario-checks`) })
  const runs = useQuery({ queryKey: ['sim-runs'], queryFn: () => api<RunRow[]>(`${endpoint}/runs?limit=100`), refetchInterval: 3500 })
  const evidence = useQuery({ queryKey: ['sim-evidence', workspaceId], enabled: !!workspaceId, queryFn: () => api<Evidence>(`${endpoint}/workspaces/${workspaceId}/evidence`) })
  const current = useQuery({
    queryKey: ['sim-run', runId], enabled: !!runId,
    queryFn: () => api<Run>(`${endpoint}/runs/${runId}`),
    refetchInterval: query => {
      const item = query.state.data as Run | undefined
      return !item || ['QUEUED', 'RUNNING'].includes(item.state) ? 650 : false
    },
  })
  const coreReadiness = useQuery({
    queryKey: ['sim-core-readiness'],
    queryFn: () => api<CoreFixtureReadiness>(`${endpoint}/core-fixture-readiness`),
  })
  // Restore a useful workspace after reload; changing it always re-keys evidence queries.
  useEffect(() => {
    if (!workspaceId && workspaces.data?.length) {
      const latestActive = workspaces.data.find(w => w.state === 'ACTIVE')
      if (latestActive) setWorkspaceId(latestActive.id)
    }
  }, [workspaceId, workspaces.data])
  const coreFixtures = useQuery({
    queryKey: ['sim-core-fixtures', workspaceId],
    enabled: Boolean(workspaceId) && Boolean(coreReadiness.data?.coreFixturePermitted),
    queryFn: () => api<CoreFixture[]>(`${endpoint}/workspaces/${workspaceId}/core-fixtures`),
  })
  const createCoreFixture = useMutation({
    mutationFn: () => api<CoreFixture>(`${endpoint}/workspaces/${workspaceId}/core-fixtures`, {
      method: 'POST', body: JSON.stringify({ requestKey: crypto.randomUUID().replaceAll('-', '') }),
    }),
    onSuccess: () => {
      void q.invalidateQueries({ queryKey: ['sim-core-fixtures'] })
      void q.invalidateQueries({ queryKey: ['sim-evidence'] })
      message.success('پرونده، ملک و دو مالک مصنوعی ثبت شدند و جداسازی REAL/TEST بررسی شد.')
    },
    onError: e => message.error(safeMessage(e)),
  })
  // Business-E2E results are independent of the previous read-only preflight runs.
  const detailedPlans = useQuery({
    queryKey: ['sim-business-e2e-plans'],
    queryFn: () => api<DetailedE2EPlan[]>(`${endpoint}/e2e/plans`),
  })
  const businessE2ECoverage = useQuery({
    queryKey: ['sim-business-e2e-coverage'],
    queryFn: () => api<BusinessE2ECoverage>(`${endpoint}/e2e/coverage`),
  })
  const businessE2ERuns = useQuery({
    queryKey: ['sim-business-e2e-runs', workspaceId], enabled: Boolean(workspaceId),
    queryFn: () => api<Array<{id:string; state:string; requested_at:string; scenario_count:number}>>(`${endpoint}/e2e/runs?workspaceId=${workspaceId}`),
    refetchInterval: 2500,
  })
  const activeE2ERunId = e2eRunId ?? businessE2ERuns.data?.[0]?.id
  const activeBusinessE2E = useQuery({
    queryKey: ['sim-business-e2e-detail', activeE2ERunId], enabled: Boolean(activeE2ERunId),
    queryFn: () => api<BusinessE2ERun>(`${endpoint}/e2e/runs/${activeE2ERunId}`),
    refetchInterval: query => {
      const value = query.state.data as BusinessE2ERun | undefined
      return !value || ['QUEUED','RUNNING'].includes(value.state) ? 750 : false
    },
  })
  const startBusinessE2E = useMutation({
    mutationFn: (scenarioIds: string[]) => api<BusinessE2ERun>(`${endpoint}/e2e/runs`, {
      method: 'POST', body: JSON.stringify({ workspaceId, requestKey: crypto.randomUUID().replaceAll('-', ''), scenarioIds }),
    }),
    onSuccess: value => {
      setE2eRunId(value.id)
      setE2eSelectedCode(undefined)
      setE2eModalOpen(true)
      void q.invalidateQueries({ queryKey: ['sim-business-e2e-runs'] })
      void q.invalidateQueries({ queryKey: ['sim-evidence'] })
      message.info('زنجیره پیش‌نیازهای سناریوها آغاز شد؛ اجرای تجاری فقط پس از عبور از گیت ایمنی انجام می‌شود.')
    },
    onError: e => message.error(safeMessage(e)),
  })
  useEffect(() => {
    if (activeBusinessE2E.data?.state === 'COMPLETED') {
      void q.invalidateQueries({ queryKey: ['sim-evidence'] })
      void q.invalidateQueries({ queryKey: ['sim-core-fixtures'] })
    }
  }, [activeBusinessE2E.data?.id, activeBusinessE2E.data?.state, q])
  const comparison = useQuery({ queryKey: ['sim-compare', runId, compareId], enabled: !!runId && !!compareId && runId !== compareId,
    queryFn: () => api<RunComparison>(`${endpoint}/runs/${runId}/compare?other=${compareId}`) })

  const selectedE2ePlan = detailedPlans.data?.find(x => x.id === e2eSelectedCode)
  const focusedE2eResults = e2eSelectedCode
    ? (activeBusinessE2E.data?.results ?? []).filter(x => x.scenario_id === e2eSelectedCode)
    : activeBusinessE2E.data?.results ?? []
  const focusedE2eSteps = e2eSelectedCode
    ? (activeBusinessE2E.data?.steps ?? []).filter(x => x.scenario_id === e2eSelectedCode)
    : activeBusinessE2E.data?.steps ?? []
  const all = catalog.data ?? []
  const effectiveIds = selectedIds ?? all.map(s => s.id)
  const filtered = useMemo(() => all.filter(s => {
    const matchedPhase = phase === 'ALL' || s.phase === phase
    const needle = search.trim().toLocaleLowerCase('fa-IR')
    return matchedPhase && (!needle || `${s.id} ${s.title} ${s.objective} ${stageNames[s.phase] ?? ''}`.toLocaleLowerCase('fa-IR').includes(needle))
  }), [all, phase, search])
  const checkMap = useMemo(() => new Map((checks.data ?? []).map(c => [c.id, c])), [checks.data])
  const selectedWorkspace = workspaces.data?.find(w => w.id === workspaceId)
  const visibleSelected = filtered.filter(s => effectiveIds.includes(s.id)).length
  const totalTestRecords = Object.values(evidence.data?.rowsByTable ?? {}).reduce((a, b) => a + Number(b), 0)
  const progress = current.data?.progress
  const stepRows = current.data?.steps ?? []
  const progressIsActive = current.data?.state === 'QUEUED' || current.data?.state === 'RUNNING'
  const chosenStepScenario = stepScenarioId ?? current.data?.results?.find(r => stepRows.some(x => x.scenario_id === r.scenario_id))?.scenario_id
  const shownStepRows = stepRows.filter(x => x.scenario_id === chosenStepScenario)
  const finishedSteps = stepRows.filter(x => ['PASSED', 'FAILED', 'BLOCKED'].includes(x.state))
  const passedChecks = finishedSteps.filter(x => x.state === 'PASSED').length
  const failedChecks = finishedSteps.filter(x => x.state === 'FAILED').length
  const blockedChecks = finishedSteps.filter(x => x.state === 'BLOCKED').length

  const createWorkspace = useMutation({
    mutationFn: (value: { code: string; title: string }) => api<Workspace>(`${endpoint}/workspaces`, {
      method: 'POST', body: JSON.stringify(value),
    }),
    onSuccess: w => {
      setWorkspaceId(w.id)
      setCreateOpen(false)
      setNewCode('')
      setNewTitle('')
      void q.invalidateQueries({ queryKey: ['sim-workspaces'] })
      message.success('فضای آزمایشی ثبت شد. داده کسب‌وکار ایجاد نشده است.')
    },
    onError: e => message.error(safeMessage(e)),
  })
  const preflight = useMutation({
    mutationFn: async (id: string) => api<Run>(`${endpoint}/runs`, {
      method: 'POST',
      body: JSON.stringify({ workspaceId: id, requestKey: crypto.randomUUID().replaceAll('-', ''), scenarioIds: effectiveIds }),
    }),
    onSuccess: result => {
      setRunId(result.id)
      setResultModalOpen(true)
      setStepScenarioId(undefined)
      setCompareId(undefined)
      setTab('runs')
      void q.invalidateQueries({ queryKey: ['sim-runs'] })
      void q.invalidateQueries({ queryKey: ['sim-evidence'] })
      message.info('اجرای بررسی واقعیِ فقط‌خواندنی شروع شد؛ وضعیت زیرفرایندها را در همین صفحه ببین.')
    },
    onError: e => message.error(safeMessage(e)),
  })
  const busy = createWorkspace.isPending || preflight.isPending
  async function quickStart() {
    if (!effectiveIds.length) { message.warning('حداقل یک سناریو انتخاب کن.'); return }
    if (workspaceId) { preflight.mutate(workspaceId); return }
    try {
      const code = `sim_${Date.now()}_${crypto.randomUUID().slice(0, 8)}`
      const w = await api<Workspace>(`${endpoint}/workspaces`, {
        method: 'POST', body: JSON.stringify({ code, title: `فضای بررسی ${new Date().toLocaleDateString('fa-IR')}` }),
      })
      setWorkspaceId(w.id)
      await q.invalidateQueries({ queryKey: ['sim-workspaces'] })
      preflight.mutate(w.id)
    } catch (e) { message.error(safeMessage(e)) }
  }
  function refresh() {
    void Promise.all([
      q.invalidateQueries({ queryKey: ['sim-capabilities'] }),
      q.invalidateQueries({ queryKey: ['sim-readiness'] }),
      q.invalidateQueries({ queryKey: ['sim-checks'] }),
      q.invalidateQueries({ queryKey: ['sim-workspaces'] }),
      q.invalidateQueries({ queryKey: ['sim-runs'] }),
      q.invalidateQueries({ queryKey: ['sim-evidence'] }),
      q.invalidateQueries({ queryKey: ['sim-run'] }),
    ])
  }
  async function exportReport(id: string) {
    setDownloading(true)
    try {
      const report = await api<Record<string, unknown>>(`${endpoint}/runs/${id}/report`)
      downloadJson(report, `sakhtyar-simulation-${id}.json`)
      message.success('گزارش JSON دریافت شد.')
    } catch (e) { message.error(safeMessage(e)) }
    finally { setDownloading(false) }
  }

  const gateItems = [
    { title: 'Migration V58', passed: Boolean(readiness.data?.migration58Installed), detail: 'ساختار ذخیره فضای آزمایشی' },
    { title: 'برچسب منشأ داده', passed: (readiness.data?.taggedBusinessTables ?? 0) >= (readiness.data?.taggedBusinessTablesExpected ?? 20), detail: 'وجود ستون‌های REAL/TEST در جداول منتخب' },
    { title: 'جداسازی و RLS', passed: Boolean(readiness.data?.rlsAndCrossScopeFkEnforcementVerified), detail: 'جلوگیری از دسترسی متقاطع و اتصال رکوردها' },
    { title: 'محافظ عملیات خارجی', passed: Boolean(readiness.data?.outboundGuardVerified), detail: 'ایمیل، پیامک، ذخیره‌سازی و سایر اثرات جانبی' },
    { title: 'پوشش Queryهای تجاری', passed: Boolean(readiness.data?.allBusinessQueriesScopeGuarded), detail: 'تمام خواندن و نوشتن‌ها با Scope معتبر' },
    { title: 'مجوز اجرای واقعی E2E', passed: Boolean(readiness.data?.automatedTestBusinessWritesPermitted), detail: 'گیت نهایی اجرای سناریوی دارای نوشتن' },
  ]
  const passedGates = gateItems.filter(g => g.passed).length
  const readinessError = readiness.error || checks.error || catalog.error || workspaces.error

  return <div dir="rtl" style={{ maxWidth: 1540, margin: '0 auto', padding: '4px 0 36px' }}>
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card style={{ borderRadius: 18, background: 'linear-gradient(125deg, #f3f7ff 0%, #ffffff 53%, #f2f7fc 100%)' }}>
        <Row justify="space-between" align="middle" gutter={[16, 16]}>
          <Col xs={24} lg={16}>
            <Space direction="vertical" size={8}>
              <Space wrap>
                <Tag color="blue" icon={<ExperimentOutlined />}>مرکز شبیه‌سازی ساخت‌یار</Tag>
                <Tag color="orange" icon={<LockOutlined />}>E2E واقعی هنوز مسدود است</Tag>
                {selectedWorkspace && <Tag color="cyan">{selectedWorkspace.title}</Tag>}
              </Space>
              <Typography.Title level={3} style={{ margin: 0 }}>آزمون، شواهد دیتابیس و آمادگی انتشار</Typography.Title>
              <Typography.Text type="secondary">
                ۲۴ سناریوی کسب‌وکار را بررسی کن، فضای آزمایشی بساز، موانع را مشاهده کن و گزارش قابل ارسال دریافت کن.
              </Typography.Text>
            </Space>
          </Col>
          <Col xs={24} lg={8}>
            <Space wrap style={{ width: '100%', justifyContent: 'flex-end' }}>
              <Button icon={<ReloadOutlined />} onClick={refresh}>به‌روزرسانی</Button>
              <Button type="primary" icon={<ThunderboltOutlined />} loading={preflight.isPending} disabled={busy || !effectiveIds.length || !all.length}
                onClick={() => void quickStart()}>
                {workspaceId ? `شروع بررسی فقط‌خواندنی (${effectiveIds.length})` : 'ایجاد فضا و شروع بررسی'}
              </Button>
            </Space>
          </Col>
        </Row>
        <Alert style={{ marginTop: 16 }} showIcon type="warning"
          message="بررسی ایمن قابل اجراست؛ اما ۲۴ سناریوی واقعی هنوز به محافظت کامل REAL/TEST نیاز دارند."
          description="اکنون بررسی ارتباط واقعی PostgreSQL، وجود جدول‌های موردنیاز و ثبت مسیرهای Spring MVC اجرا می‌شود و هر زیرفرایند در دیتابیس ثبت می‌شود. اما آزمونِ دارای نوشتن روی پرونده، قرارداد و داده تجاری تا تکمیل جداسازی REAL/TEST مسدود است." />
         {runId && <Card size="small" style={{ marginTop: 16, borderRadius: 12, background: '#f7faff', borderColor: '#d8e6ff' }}
           title={<Space><ClockCircleOutlined /> پیشرفت اجرای بررسی‌های واقعی و ایمن {status(current.data?.state)}</Space>}
           extra={<Button size="small" onClick={() => setTab('runs')}>زیرفرایندها و نتایج</Button>}>
           <Progress percent={progress?.percent ?? 0} status={progressIsActive ? 'active' : 'normal'} />
           <Row gutter={[12, 8]}>
             <Col xs={12} sm={6}><Statistic title="گام‌های پایان‌یافته" value={progress?.completedSteps ?? 0} suffix={`/ ${progress?.totalSteps ?? 0}`} /></Col>
             <Col xs={12} sm={6}><Statistic title="بررسی‌های موفق" value={passedChecks} /></Col>
             <Col xs={12} sm={6}><Statistic title="بررسی‌های ناموفق" value={failedChecks} /></Col>
             <Col xs={12} sm={6}><Statistic title="گیت‌های مسدود" value={blockedChecks} /></Col>
           </Row>
           <Typography.Text type="secondary" style={{ display: 'block', marginTop: 8 }}>
             {progress?.currentStep ? `در حال بررسی: ${progress.currentStep.scenario_id} · ${progress.currentStep.title}` :
               progressIsActive ? 'در حال صف‌بندی یا پردازش زیرفرایندها...' :
               (progress?.totalSteps ? 'بررسی‌های فقط‌خواندنی پایان یافتند؛ E2E تجاری اجرا نشده است.' : 'این اجرای قدیمی، زیرفرایند ثبت‌شده ندارد.')}
           </Typography.Text>
         </Card>}
      </Card>

      {readinessError && <Alert type="error" showIcon message="بارگذاری بعضی اطلاعات ناموفق بود" description={safeMessage(readinessError)} />}

      <Row gutter={[12, 12]}>
        <Col xs={12} md={6}><Card size="small" style={{ borderRadius: 14 }}><Statistic title="سناریوهای تعریف‌شده" value={all.length} prefix={<FileSearchOutlined />} /></Card></Col>
        <Col xs={12} md={6}><Card size="small" style={{ borderRadius: 14 }}><Statistic title="فضاهای آزمایشی" value={workspaces.data?.length ?? 0} prefix={<DatabaseOutlined />} /></Card></Col>
        <Col xs={12} md={6}><Card size="small" style={{ borderRadius: 14 }}><Statistic title="بررسی‌های ثبت‌شده" value={runs.data?.length ?? 0} prefix={<ClockCircleOutlined />} /></Card></Col>
        <Col xs={12} md={6}><Card size="small" style={{ borderRadius: 14 }}><Statistic title="گیت‌های آماده" value={passedGates} suffix={`/ ${gateItems.length}`} prefix={<SafetyCertificateOutlined />} /></Card></Col>
      </Row>

      <Card size="small" title="وضعیت پیشرفت چهار فاز" style={{ borderRadius: 14 }}>
        <Row gutter={[12, 12]}>{(capabilities.data?.phaseStatus ?? []).map(item =>
          <Col xs={24} sm={12} xl={6} key={item.phase}>
            <Card size="small" style={{ height: '100%', borderRadius: 12, background: '#fafcff' }}>
              <Space direction="vertical" size={5}>
                <Tag>{item.phase}</Tag>
                <Typography.Text strong>{stageNames[item.phase] ?? item.title}</Typography.Text>
                <Tag color={item.status === 'PLANNED' ? 'orange' : 'blue'}>{item.status === 'PLANNED' ? 'در برنامه توسعه' : 'زیرساخت ثبت‌شده'}</Tag>
              </Space>
            </Card>
          </Col>
        )}</Row>
      </Card>

      <Card style={{ borderRadius: 16 }}>
        <Tabs activeKey={tab} onChange={setTab} items={[
          { key: 'business-e2e', label: <Space><ExperimentOutlined /> اجرای واقعی E2E</Space>, children: <>
            <Alert showIcon type="warning" style={{ marginBottom: 12 }}
              message="E2E واقعی با بررسی مقدماتی تفاوت دارد"
              description="برای هر ۲۴ سناریو، وابستگی‌ها مرتب شده و بررسی‌های واقعی PostgreSQL/Schema/API پیش از گیت اجرا می‌شوند. فقط E2E-14 اجازه درج محدود داده TEST دارد؛ ۲۳ گردش‌کار تجاری دیگر تا ایزوله‌سازی کامل مسدود می‌مانند. درصد پیشرفت، درصد پایان گام‌هاست نه درصد موفقیت آزمون تجاری." />
            <Row gutter={[12, 12]} style={{ marginBottom: 14 }}>
              <Col xs={24} sm={12}><Card size="small"><Statistic title="سناریوهای دارای اجرای محدود واقعی" value={businessE2ECoverage.data?.limitedBusinessE2eCount ?? 0} suffix="از ۲۴" /></Card></Col>
              <Col xs={24} sm={12}><Card size="small"><Statistic title="سناریوهای بدون موتور E2E تجاری" value={24-(businessE2ECoverage.data?.limitedBusinessE2eCount ?? 0)} /></Card></Col>
            </Row>
            <Space wrap style={{ marginBottom: 14 }}>
              <Button type="primary" icon={<ExperimentOutlined />} loading={startBusinessE2E.isPending}
                disabled={!workspaceId || !coreReadiness.data?.coreFixturePermitted}
                onClick={() => startBusinessE2E.mutate(['E2E-14'])}>
                اجرای واقعی E2E-14
              </Button>
              <Button loading={startBusinessE2E.isPending} disabled={!workspaceId}
                onClick={() => startBusinessE2E.mutate(Array.from({length:24}, (_,i) => `E2E-${String(i+1).padStart(2,'0')}`))}>
                اجرای زنجیره پیش‌نیازهای ۲۴ سناریو
              </Button>
            </Space>
            {!coreReadiness.data?.coreFixturePermitted && <Alert style={{ marginBottom: 12 }} showIcon type="info"
              message="E2E-14 تا تأیید گیت PostgreSQL قابل اجرا نیست"
              description={(coreReadiness.data?.blockers ?? ['نقش محدود دیتابیس و FORCE RLS چهار جدول لازم است.']).join(' | ')} />}
            {!workspaceId && <Alert showIcon type="info" message="ابتدا یک فضای آزمایشی فعال انتخاب کن" />}
            <Card size="small" style={{ marginBottom: 12, borderRadius: 12 }} title="برنامه اجرایی و وابستگی‌های ۲۴ سناریو">
              <Typography.Paragraph type="secondary">روی نام هر سناریو کلیک کن تا هدف، پیش‌نیازها، مراحل قابل اجرا و قسمت‌های هنوز مسدود را دقیق ببینی.</Typography.Paragraph>
              <Space wrap>
                {(detailedPlans.data ?? []).map(plan =>
                  <Button key={plan.id} size="small" onClick={() => { setE2eSelectedCode(plan.id);setE2eModalOpen(true) }}>
                    {plan.id} · {plan.title}
                  </Button>)}
              </Space>
            </Card>
            <Table size="small" rowKey="id" style={{ marginBottom: 14 }} pagination={{ pageSize: 5 }}
              dataSource={businessE2ERuns.data ?? []} loading={businessE2ERuns.isLoading} columns={[
                { title: 'زمان', dataIndex: 'requested_at', render: (v:string) => dateFa(v) },
                { title: 'وضعیت', dataIndex: 'state', render: (v:string) => status(v) },
                { title: 'تعداد سناریو', dataIndex: 'scenario_count' },
                { title: 'نتیجه', render: (_:unknown, r:{id:string}) => <Button size="small" onClick={() => { setE2eRunId(r.id); setE2eSelectedCode(undefined); setE2eModalOpen(true) }}>مشاهده نتیجه</Button> },
              ]} />
            {activeBusinessE2E.data && <Card size="small" title={`نتایج واقعی E2E · ${activeBusinessE2E.data.id.slice(0,8)}`}>
              <Progress percent={activeBusinessE2E.data.progressPercent} status="active" />
              <Typography.Text type="secondary">{activeBusinessE2E.data.completedScenarios} از {activeBusinessE2E.data.scenario_count} سناریو تعیین تکلیف شده‌اند. {activeBusinessE2E.data.completedSteps ?? 0} از {activeBusinessE2E.data.totalSteps ?? 0} زیرفرایند ثبت شده؛ PASS فقط به عملیات اثبات‌شده اختصاص دارد.</Typography.Text>
              <Table<BusinessE2EResult> rowKey="scenario_id" size="small" pagination={{pageSize:10}}
                style={{ marginTop: 12 }} dataSource={activeBusinessE2E.data.results} columns={[
                  { title:'سناریو',dataIndex:'scenario_id' },
                  { title:'وضعیت اجرایی',dataIndex:'state',render:(v:string)=>status(v) },
                  { title:'شواهد / علت',dataIndex:'summary' },
                  { title:'جزئیات',render:(_:unknown,row:BusinessE2EResult)=><Button size="small" onClick={()=>{ setE2eSelectedCode(row.scenario_id);setE2eModalOpen(true) }}>مشاهده مراحل</Button> },
                ]} />
              <Typography.Text strong>زیرفرایندهای ثبت‌شده همه سناریوها</Typography.Text>
              <Table<BusinessE2EStep> rowKey={r => `${r.scenario_id}-${r.step_no}`} size="small" pagination={false}
                dataSource={activeBusinessE2E.data.steps} columns={[
                  { title:'مرحله',dataIndex:'step_no' }, { title:'عملیات',dataIndex:'title' },
                  { title:'وضعیت',dataIndex:'state',render:(v:string)=>status(v) }, { title:'جزئیات',dataIndex:'detail' },
                ]} />
                <Button size="small" icon={<CloudDownloadOutlined />} onClick={() => downloadJson(activeBusinessE2E.data, `business-e2e-${activeBusinessE2E.data.id}.json`)}>دانلود شواهد JSON</Button>
            </Card>}
          </> },
          { key: 'scenarios', label: <Space><FileSearchOutlined /> سناریوها</Space>, children: <>
            <Card size="small" style={{ marginBottom: 14, background: '#f8fafd', borderRadius: 12 }}>
              <Row gutter={[12, 12]} align="bottom">
                <Col xs={24} lg={11}>
                  <Typography.Text strong>فضای آزمایشی</Typography.Text>
                  <Space.Compact style={{ display: 'flex', marginTop: 6 }}>
                    <Select style={{ flex: 1, minWidth: 0 }} value={workspaceId} placeholder="انتخاب فضای موجود (اختیاری برای شروع سریع)"
                      onChange={id => setWorkspaceId(id)} showSearch optionFilterProp="label"
                      options={(workspaces.data ?? []).map(w => ({ label: `${w.title} · ${w.code}`, value: w.id, disabled: w.state !== 'ACTIVE' }))} />
                    <Tooltip title="ایجاد فضای آزمایشی با کد و عنوان دلخواه">
                      <Button icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>جدید</Button>
                    </Tooltip>
                  </Space.Compact>
                </Col>
                <Col xs={24} sm={12} lg={7}>
                  <Typography.Text strong>جستجو در کاتالوگ</Typography.Text>
                  <Input style={{ marginTop: 6 }} allowClear value={search} prefix={<SearchOutlined />} placeholder="موضوع، شناسه یا هدف آزمون" onChange={e => setSearch(e.target.value)} />
                </Col>
                <Col xs={24} sm={12} lg={6}>
                  <Typography.Text strong>فیلتر مرحله</Typography.Text>
                  <Select style={{ width: '100%', marginTop: 6 }} value={phase} onChange={setPhase} suffixIcon={<FilterOutlined />}
                    options={[{ value: 'ALL', label: 'همه فازها' }, ...phases.map(p => ({ value: p, label: `${p} · ${stageNames[p]}` }))]} />
                </Col>
              </Row>
              <Space wrap style={{ marginTop: 12 }}>
                <Button size="small" onClick={() => setSelectedIds(all.map(s => s.id))}>انتخاب همه ۲۴ سناریو</Button>
                <Button size="small" onClick={() => setSelectedIds(Array.from(new Set([...effectiveIds, ...filtered.map(s => s.id)])))}>انتخاب نتایج فیلتر</Button>
                <Button size="small" onClick={() => setSelectedIds(effectiveIds.filter(id => !filtered.some(s => s.id === id)))}>حذف انتخاب فیلتر</Button>
                <Button size="small" onClick={() => setSelectedIds([])}>پاک‌کردن انتخاب‌ها</Button>
                <Tag color="geekblue">انتخاب‌شده: {effectiveIds.length} / {all.length}</Tag>
                <Tag>نمایش: {filtered.length}</Tag>
                <Tag color={visibleSelected ? 'blue' : 'default'}>انتخاب‌شده در این صفحه: {visibleSelected}</Tag>
              </Space>
            </Card>
            <Table<Scenario> rowKey="id" size="middle" loading={catalog.isLoading} dataSource={filtered}
              scroll={{ x: 790 }} pagination={{ pageSize: 8, showSizeChanger: true, pageSizeOptions: ['8', '16', '24'], showTotal: total => `${total} سناریو` }}
              rowSelection={{ selectedRowKeys: effectiveIds, preserveSelectedRowKeys: true, onChange: keys => setSelectedIds(keys.map(String)) }}
              columns={[
                { title: 'شناسه', dataIndex: 'id', width: 115, render: (v: string) => <Typography.Text code>{v}</Typography.Text> },
                { title: 'فاز', dataIndex: 'phase', width: 92, render: (v: string) => <Tag color="blue">{v}</Tag> },
                { title: 'عنوان آزمون', dataIndex: 'title', render: (_: string, s: Scenario) => <Space direction="vertical" size={0}><Typography.Text strong>{s.title}</Typography.Text><Typography.Text type="secondary" style={{ fontSize: 12 }}>{stageNames[s.phase] ?? s.phase}</Typography.Text></Space> },
                { title: 'پیش‌نیازها', width: 165, render: (_: unknown, s: Scenario) => {
                  const check = checkMap.get(s.id)
                  if (!check) return <Tag>در حال بررسی</Tag>
                  return <Tag color={check.schemaRoutesReady ? 'blue' : 'orange'}>{check.schemaRoutesReady ? 'ساختار موجود' : `${check.missingTables.length + check.missingRoutes.length} مورد ناقص`}</Tag>
                } },
                { title: 'اجرای تجاری', width: 240, render: (_: unknown, scenario: Scenario) =>
                   scenario.id === 'E2E-14' && (coreFixtures.data?.length ?? 0) > 0
                     ? <Tooltip title="فقط درج پرونده، ملک و دو مالک در PostgreSQL تأیید شده؛ گردش‌کار کامل E2E-14 هنوز اجرا نشده است.">
                         <Tag color="green">Fixture واقعی محدود · E2E ناتمام</Tag>
                       </Tooltip>
                     : <Tag color="warning">BLOCKED · اجرای تجاری انجام نشده</Tag> },
                { title: 'جزئیات', width: 90, render: (_: unknown, s: Scenario) => <Button size="small" icon={<InfoCircleOutlined />} onClick={() => setDrawer(s)}>مشاهده</Button> },
              ]} />
            <Space wrap style={{ marginTop: 12, width: '100%', justifyContent: 'space-between' }}>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>خالی‌کردن انتخاب به معنی اجرای همه نیست؛ باید حداقل یک سناریو را انتخاب کنی.</Typography.Text>
              <Button type="primary" size="large" icon={<SafetyCertificateOutlined />} loading={preflight.isPending}
                disabled={busy || !effectiveIds.length || !all.length} onClick={() => void quickStart()}>
                {workspaceId ? `شروع بررسی واقعیِ فقط‌خواندنی ${effectiveIds.length} سناریو` : 'ایجاد فضا و شروع بررسی'}
              </Button>
            </Space>
          </> },
          { key: 'runs', label: <Space><ClockCircleOutlined /> تاریخچه و نتایج</Space>, children: <>
            <Typography.Paragraph type="secondary">اجراهای جدید دارای زیرفرایندهای قابل پایش هستند. درصد پیشرفت فقط مربوط به بررسی‌های اتصال، Schema و API است؛ نه تکمیل E2E تجاری.</Typography.Paragraph>
            <Table<RunRow> rowKey="id" size="small" scroll={{ x: 760 }} dataSource={runs.data ?? []} loading={runs.isLoading}
              pagination={{ pageSize: 7, showSizeChanger: true }}
              columns={[
                { title: 'زمان', dataIndex: 'requested_at', width: 200, render: (v: string) => dateFa(v) },
                { title: 'شناسه اجرا', dataIndex: 'id', render: (v: string) => <Typography.Text code copyable={{ text: v }}>{v.slice(0, 12)}…</Typography.Text> },
                { title: 'تعداد', dataIndex: 'scenario_count', width: 75 },
                { title: 'نتیجه', dataIndex: 'state', width: 180, render: (v: string) => status(v) },
                { title: 'اقدام', width: 115, render: (_: unknown, r: RunRow) => <Button type={runId === r.id ? 'primary' : 'default'} size="small" onClick={() => { setRunId(r.id); setCompareId(undefined); setResultModalOpen(true) }}>مشاهده نتیجه</Button> },
              ]} />
            {runId ? <Modal open={resultModalOpen} onCancel={() => setResultModalOpen(false)} footer={null}
              width={1100} title="نتایج و زیرفرایندهای اجرای انتخاب‌شده" destroyOnHidden>
              <div dir="rtl" style={{ maxHeight: '72vh', overflowY: 'auto', padding: '4px 6px' }}>
              <Card size="small" style={{ background: '#fafcff', borderRadius: 12 }} title="جزئیات اجرای انتخاب‌شده"
              extra={<Button type="primary" size="small" icon={<CloudDownloadOutlined />} loading={downloading} onClick={() => void exportReport(runId)}>دانلود JSON</Button>}>
              <Card size="small" style={{ marginBottom: 14, background: '#ffffff' }}>
                 <Space direction="vertical" style={{ width: '100%' }}>
                   <Typography.Text strong>پیشرفت زیرفرایندهای قابل اندازه‌گیری</Typography.Text>
                   <Progress percent={progress?.percent ?? 0} status={progressIsActive ? 'active' : 'normal'} />
                   <Space wrap>
                     <Tag color="blue">پایان‌یافته: {progress?.completedSteps ?? 0} / {progress?.totalSteps ?? 0}</Tag>
                     <Tag color="green">موفق: {passedChecks}</Tag>
                     <Tag color="red">ناموفق: {failedChecks}</Tag>
                     <Tag color="warning">گیت E2E مسدود: {blockedChecks}</Tag>
                   </Space>
                   <Typography.Text type="secondary">{progress?.currentStep ? `گام جاری: ${progress.currentStep.scenario_id} · ${progress.currentStep.title}` :
                     stepRows.length ? 'تمام زیرفرایندهای بررسی ایمن تعیین‌تکلیف شدند.' : 'این اجرا متعلق به نسخه قبلی است و زیرفرایند ندارد.'}</Typography.Text>
                 </Space>
               </Card>
               <Descriptions size="small" bordered column={{ xs: 1, sm: 2 }} items={[
                { key: 'id', label: 'شناسه', children: <Typography.Text copyable={{ text: runId }}>{runId.slice(0, 18)}…</Typography.Text> },
                { key: 'state', label: 'نتیجه', children: status(current.data?.state) },
                { key: 'date', label: 'زمان', children: dateFa(current.data?.requested_at) },
                { key: 'revision', label: 'نسخه کد', children: current.data?.git_commit ?? '—' },
                { key: 'reason', label: 'علت مسدودشدن', span: 2, children: current.data?.gate_reason ?? '—' },
              ]} />
              <Table<ResultRow> style={{ marginTop: 14 }} size="small" rowKey="scenario_id" pagination={{ pageSize: 8 }} dataSource={current.data?.results ?? []}
                columns={[
                   { title: 'شناسه', dataIndex: 'scenario_id' },
                   { title: 'موضوع', dataIndex: 'title' },
                   { title: 'وضعیت E2E', dataIndex: 'state', render: (v: string) => status(v) },
                   { title: 'زیرفرایندها', render: (_: unknown, row: ResultRow) => {
                     const parts = stepRows.filter(x => x.scenario_id === row.scenario_id)
                     const completed = parts.filter(x => ['PASSED','FAILED','BLOCKED'].includes(x.state)).length
                     return <Button size="small" onClick={() => setStepScenarioId(row.scenario_id)}>
                       {parts.length ? `${completed}/${parts.length} · جزئیات` : 'بدون داده'}
                     </Button>
                   } },
                 ]} />
               {stepRows.length > 0 && <Card size="small" style={{ marginTop: 12, background: '#fff' }}
                 title={<Space><FileSearchOutlined /> زیرفرایندهای {chosenStepScenario ?? 'سناریو'}</Space>}
                 extra={<Select showSearch style={{ width: 270 }} value={chosenStepScenario} onChange={setStepScenarioId}
                   options={(current.data?.results ?? []).map(r => ({ label: `${r.scenario_id} · ${r.title}`, value: r.scenario_id }))} />}>
                 <Table<StepRow> size="small" pagination={false} rowKey={r => `${r.scenario_id}:${r.step_no}`}
                   dataSource={shownStepRows} scroll={{ x: 660 }} columns={[
                     { title: 'مرحله', dataIndex: 'step_no', width: 76 },
                     { title: 'زیرپردازش', dataIndex: 'title', width: 250 },
                     { title: 'وضعیت', dataIndex: 'state', width: 150, render: (v: string) => status(v) },
                     { title: 'شاهد', dataIndex: 'detail' },
                   ]} />
               </Card>}
              <Space wrap>
                <Typography.Text strong>مقایسه با اجرای دیگر:</Typography.Text>
                <Select style={{ minWidth: 265 }} allowClear placeholder="یک اجرای قبلی انتخاب کن" value={compareId} onChange={setCompareId}
                  options={(runs.data ?? []).filter(r => r.id !== runId).map(r => ({ value: r.id, label: `${dateFa(r.requested_at)} · ${r.id.slice(0, 8)}` }))} />
              </Space>
              {comparison.data && <Alert style={{ marginTop: 12 }} type="info" showIcon message={`اجرای فعلی: ${comparison.data.left.results.length} نتیجه · اجرای مقایسه: ${comparison.data.right.results.length} نتیجه`} description="مقایسه فعلی فقط وضعیت و شواهد پایه را نمایش می‌دهد؛ هنوز مقایسه خروجی مالی یا پرونده‌های واقعی نیست." />}
              {comparison.isError && <Alert style={{ marginTop: 12 }} type="error" message={safeMessage(comparison.error)} />}
              </Card></div></Modal> : <Empty description="از جدول بالا یک اجرا را انتخاب کن یا یک بررسی جدید ثبت کن." />}
          </> },
          { key: 'data', label: <Space><DatabaseOutlined /> داده‌ها و شواهد</Space>, children: <>
            <Card size="small" style={{ marginBottom: 16, background: '#f6f8fc' }} title="T05 · اثبات واقعی ثبت پرونده آزمایشی">
              <Alert type="info" showIcon style={{ marginBottom: 12 }}
                message="آزمون محدود دیتابیس: یک پرونده، یک ملک و دو مالک مصنوعی"
                description="این آزمون چهار جدول اصلی را واقعاً در PostgreSQL تغییر می‌دهد، اما هنوز اجرای ۲۴ گردش‌کار E2E و پروفایل سراسری TEST نیست. هیچ سند یا پیام خارجی ساخته یا ارسال نمی‌شود." />
              <Space direction="vertical" style={{ width: '100%', marginBottom: 12 }}>
                <Typography.Text strong>فضای آزمایشی برای ثبت و نمایش پرونده‌ها</Typography.Text>
                <Select style={{ width: '100%', maxWidth: 540 }} value={workspaceId} showSearch optionFilterProp="label"
                  placeholder="یک فضای TEST فعال انتخاب کن" onChange={setWorkspaceId}
                  options={(workspaces.data ?? []).map(w => ({ value: w.id, label: `${w.title} · ${w.code}`, disabled: w.state !== 'ACTIVE' }))} />
                {!workspaceId && <Alert showIcon type="warning" message="فضای آزمایشی انتخاب نشده است"
                  description="از فهرست بالا یک فضای موجود انتخاب کن یا در زبانه سناریوها فضای جدید بساز." />}
              </Space>
              <Space wrap style={{ marginBottom: 12 }}>
                <Tag color={coreReadiness.data?.coreFixturePermitted ? 'green' : 'orange'}>
                  {coreReadiness.data?.coreFixturePermitted ? 'جداسازی چهار جدول آماده' : 'مجوز ایجاد داده آزمایشی برقرار نیست'}
                </Tag>
                <Tag color={coreReadiness.data?.runtimeDbRoleNonBypass ? 'green' : 'red'}>
                  نقش PostgreSQL: {coreReadiness.data?.runtimeDbRoleNonBypass ? 'محدود' : 'نیازمند بررسی'}</Tag>
                <Tag color={coreReadiness.data?.migration60StructureAvailable ? 'green' : 'orange'}>
                  Migration V60: {coreReadiness.data?.migration60StructureAvailable ? 'ثبت شده' : 'نامشخص'}</Tag>
              </Space>
              {!coreReadiness.data?.coreFixturePermitted && !coreReadiness.isLoading &&
                <Alert style={{ marginBottom: 12 }} showIcon type="warning" message="چرا ساخت پرونده TEST هنوز مجاز نیست؟"
                  description={<Space direction="vertical" size={3}>
                    {(coreReadiness.data?.blockers?.length ? coreReadiness.data.blockers : ['ابتدا ارتباط بک‌اند، V60 و نقش اتصال دیتابیس را بررسی کن.']).map((reason, i) =>
                      <Typography.Text key={i} style={{ direction: 'ltr', display: 'block' }}>{reason}</Typography.Text>)}
                    <Typography.Text type="secondary">نقش فعلی DB: {coreReadiness.data?.runtimeDbUser ?? 'نامشخص'}</Typography.Text>
                  </Space>} />}
              <div><Button type="primary" icon={<ExperimentOutlined />} loading={createCoreFixture.isPending}
                  disabled={!workspaceId || !coreReadiness.data?.coreFixturePermitted}
                  onClick={() => createCoreFixture.mutate()}>
                    ایجاد و اعتبارسنجی پرونده TEST
                  </Button></div>
              {coreReadiness.isError && <Alert style={{ marginTop: 10 }} type="warning" message={safeMessage(coreReadiness.error)} />}
              <Table<CoreFixture> style={{ marginTop: 12 }} size="small" rowKey="id"
                dataSource={coreFixtures.data ?? []} loading={coreFixtures.isLoading}
                pagination={{ pageSize: 5 }} scroll={{ x: 730 }}
                columns={[
                  { title: 'پرونده TEST', dataIndex: 'project_code', render: (v:string) => <Typography.Text code>{v}</Typography.Text> },
                  { title: 'تاریخ', dataIndex: 'created_at', render: (v:string) => dateFa(v) },
                  { title: 'ملک', dataIndex: 'property_id', render: (v:string) => <Typography.Text copyable={{ text:v }}>{v.slice(0,8)}…</Typography.Text> },
                  { title: 'مالکان', dataIndex: 'owner_count', render: (v:number) => <Tag>{v}</Tag> },
                ]} />
            </Card>

            {!workspaceId ? <Empty description="برای مشاهده شواهد، ابتدا فضای آزمایشی را از زبانه سناریوها انتخاب کن." /> : <>
              <Row gutter={[12, 12]} style={{ marginBottom: 14 }}>
                <Col xs={24} sm={12}><Card size="small"><Statistic title="تعداد رکوردهای TEST در جداول پایش‌شده" value={totalTestRecords} /></Card></Col>
                <Col xs={24} sm={12}><Card size="small"><Statistic title="جداول تحت بررسی" value={Object.keys(evidence.data?.rowsByTable ?? {}).length} /></Card></Col>
              </Row>
              <Alert type={totalTestRecords > 0 ? 'success' : 'info'} showIcon style={{ marginBottom: 12 }}
                message={totalTestRecords > 0 ? `${totalTestRecords} رکورد TEST در فضای انتخاب‌شده مشاهده شد` : 'در این فضای آزمایشی هنوز رکورد TEST وجود ندارد'}
                description={totalTestRecords > 0 ? (evidence.data?.warning ?? 'رکوردها از همان فضای TEST شمارش شده‌اند.') :
                  '۹۶ بررسی مقدماتی فقط‌خواندنی هیچ رکورد کسب‌وکاری ایجاد نمی‌کنند. برای ساخت پرونده TEST، گیت چهار جدول و نقش محدود PostgreSQL باید آماده باشد؛ سپس دکمه «ایجاد و اعتبارسنجی پرونده TEST» را بزن.'} />
              {evidence.isError && <Alert showIcon type="error" style={{ marginBottom: 12 }} message="شمارش شواهد ناموفق بود" description={safeMessage(evidence.error)} />}
              <Button size="small" icon={<ReloadOutlined />} onClick={() => { void q.invalidateQueries({ queryKey: ['sim-evidence', workspaceId] }); void q.invalidateQueries({ queryKey: ['sim-core-fixtures', workspaceId] }) }} style={{ marginBottom: 10 }}>بازخوانی شواهد از PostgreSQL</Button>
              <Table size="small" rowKey="table" loading={evidence.isLoading} pagination={{ pageSize: 10 }}
                dataSource={Object.entries(evidence.data?.rowsByTable ?? {}).map(([table, count]) => ({ table, count }))}
                columns={[{ title: 'نام جدول PostgreSQL', dataIndex: 'table' }, { title: 'رکوردهای TEST', dataIndex: 'count', sorter: (a, b) => a.count - b.count, render: (v: number) => <Tag color={v ? 'blue' : 'default'}>{v}</Tag> }]} />
            </>}
          </> },
          { key: 'gates', label: <Space><SafetyCertificateOutlined /> گیت‌های امنیتی</Space>, children: <>
            <Alert showIcon type={readiness.data?.automatedTestBusinessWritesPermitted ? 'success' : 'warning'}
              message={readiness.data?.automatedTestBusinessWritesPermitted ? 'مجوز اجرای تجاری فعال شده است' : 'اجرای دارای نوشتن فعال نیست'}
              description={readiness.data?.gateReason || 'جداسازی واقعی داده‌ها باید تأیید شود.'} />
            <Space direction="vertical" size="large" style={{ width: '100%', marginTop: 18 }}>
              <Alert type="info" showIcon message="گیت‌های شش‌گانه مربوط به کل سامانه‌اند، نه فقط چهار جدول T05"
                description="حتی اگر آزمون پرونده مصنوعی موفق شود، جداسازی تمام Queryها و عملیات خارجی سایر ماژول‌ها تأیید نشده است. بنابراین سناریوهای کسب‌وکار به‌صورت خودکار PASSED نمی‌شوند." />
              <div><Typography.Text strong>پیشرفت گیت‌های زیرساختی</Typography.Text><Progress percent={Math.round(passedGates / gateItems.length * 100)} status="active" style={{ marginTop: 8 }} /></div>
              <Row gutter={[12, 12]}>{gateItems.map(item => <Col xs={24} md={12} key={item.title}>
                <Card size="small" style={{ background: item.passed ? '#f6fbf8' : '#fffaf4', height: '100%' }}>
                  <Space align="start"><span>{item.passed ? <CheckCircleOutlined style={{ color: '#31956a', fontSize: 19 }} /> : <LockOutlined style={{ color: '#c88a32', fontSize: 19 }} />}</span>
                    <Space direction="vertical" size={2}><Typography.Text strong>{item.title}</Typography.Text><Typography.Text type="secondary" style={{ fontSize: 12 }}>{item.detail}</Typography.Text></Space></Space>
                </Card>
              </Col>)}</Row>
              <Typography.Text type="secondary">برچسب‌گذاری جداول، اثبات جداسازی سراسری نیست. نتیجه بررسی واقعی باید همراه با تست مجوز، SQL و عملیات خارجی تأیید شود.</Typography.Text>
            </Space>
          </> },
        ]} />
      </Card>
    </Space>

    <Modal open={e2eModalOpen} onCancel={() => setE2eModalOpen(false)} footer={null}
      title={e2eSelectedCode ? `جزئیات سناریو ${e2eSelectedCode}` : 'نتیجه اجرای E2E و زیرفرایندها'}
      width={1060} destroyOnHidden>
      <Space direction="vertical" style={{ width: '100%' }} size="middle">
        {detailedPlans.isError && <Alert type="error" message="بارگذاری برنامه سناریوها ناموفق بود" description={safeMessage(detailedPlans.error)} />}
        {selectedE2ePlan && <Card size="small" title={`${selectedE2ePlan.id} · ${selectedE2ePlan.title}`}>
          <Typography.Paragraph>{selectedE2ePlan.description}</Typography.Paragraph>
          <Typography.Text strong>وابستگی‌هایی که ابتدا بررسی می‌شوند:</Typography.Text>
          <Space wrap style={{ display: 'flex', marginTop: 6, marginBottom: 10 }}>
            {selectedE2ePlan.dependencies.length === 0 ? <Tag>وابستگی قبلی ندارد</Tag> : selectedE2ePlan.dependencies.map(x => <Tag key={x} color="blue">{x}</Tag>)}
          </Space>
          <Typography.Text strong>زیرفرایندهای بررسی پیش‌نیاز:</Typography.Text>
          <ol>{selectedE2ePlan.preflightSteps.map((x,i) => <li key={i}>{x}</li>)}</ol>
          <Typography.Paragraph><Typography.Text strong>گردش‌کار نهایی مورد انتظار:</Typography.Text> {selectedE2ePlan.businessSteps}</Typography.Paragraph>
          <Space wrap>{selectedE2ePlan.requiredTables.map(x => <Tag key={x}>{x}</Tag>)}</Space>
          <Space wrap>{selectedE2ePlan.requiredRoutes.map(x => <Tag key={x} color="geekblue">{x}</Tag>)}</Space>
          <Alert style={{ marginTop: 12 }} type={selectedE2ePlan.businessStatus === 'LIMITED_CORE_FIXTURE' ? 'info' : 'warning'} showIcon
            message={selectedE2ePlan.businessStatus === 'LIMITED_CORE_FIXTURE' ? 'فقط Fixture چهارجدولی دارای پیاده‌سازی محدود است' : 'گردش‌کار کامل تجاری هنوز ایزوله و پیاده‌سازی نشده است'}
            description="موفقیت بررسی جدول و Route به معنی ارسال واقعی درخواست API یا اجرای گردش‌کار کسب‌وکار نیست." />
        </Card>}
        <Row gutter={[12,12]}>
          <Col xs={24} md={12}><Statistic title="پیشرفت زیرفرایندها" value={activeBusinessE2E.data?.progressPercent ?? 0} suffix="٪" /></Col>
          <Col xs={24} md={12}><Statistic title="گام‌های پایان‌یافته / کل" value={`${activeBusinessE2E.data?.completedSteps ?? 0} / ${activeBusinessE2E.data?.totalSteps ?? 0}`} /></Col>
        </Row>
        {activeBusinessE2E.data && <Progress percent={activeBusinessE2E.data.progressPercent} />}
        <Table<BusinessE2EResult> size="small" rowKey="scenario_id" pagination={{ pageSize: 8 }} scroll={{ x: 680 }}
          dataSource={focusedE2eResults} columns={[
            { title:'شناسه',dataIndex:'scenario_id' },
            { title:'نتیجه واقعی',dataIndex:'state',render:(v:string)=>status(v) },
            { title:'شرح',dataIndex:'summary' },
            { title:'بررسی',render:(_:unknown,x:BusinessE2EResult)=><Button size="small" onClick={()=>setE2eSelectedCode(x.scenario_id)}>جزئیات</Button> },
          ]} />
        <Typography.Text strong>ریز مراحل اجرا و علت هر وضعیت</Typography.Text>
        <Table<BusinessE2EStep> size="small" rowKey={x=>`${x.scenario_id}-${x.step_no}`} pagination={{pageSize:12}} scroll={{x:750}}
          dataSource={focusedE2eSteps} columns={[
            {title:'سناریو',dataIndex:'scenario_id'},
            {title:'مرحله',dataIndex:'step_no'},
            {title:'عملیات',dataIndex:'title'},
            {title:'وضعیت',dataIndex:'state',render:(v:string)=>status(v)},
            {title:'شواهد و خطا',dataIndex:'detail'},
          ]} />
        <Space wrap>
          {e2eSelectedCode && <Button onClick={()=>setE2eSelectedCode(undefined)}>نمایش نتیجه همه سناریوها</Button>}
          {activeBusinessE2E.data && <Button icon={<CloudDownloadOutlined />} onClick={()=>downloadJson(activeBusinessE2E.data,`business-e2e-${activeBusinessE2E.data!.id}.json`)}>دریافت JSON</Button>}
        </Space>
      </Space>
    </Modal>

    <Drawer title={drawer ? `${drawer.id} · ${drawer.title}` : 'جزئیات سناریو'} placement="left" width={500} open={!!drawer} onClose={() => setDrawer(null)}>
      {drawer && <Space direction="vertical" size="large" style={{ width: '100%' }}>
        <Space wrap><Tag color="blue">{drawer.phase} · {stageNames[drawer.phase] ?? drawer.phase}</Tag><Tag color="warning">BLOCKED</Tag></Space>
        <Typography.Paragraph><Typography.Text strong>هدف سناریو:</Typography.Text> {drawer.objective}</Typography.Paragraph>
        {(() => {
          const plan = detailedPlans.data?.find(p => p.id === drawer.id)
          if (!plan) return <Alert type="info" showIcon message="جزئیات اجرایی سناریو از موتور جدید دریافت نشده است" />
          return <Card size="small" title="مراحل کامل سناریو و وابستگی‌ها">
            <Typography.Paragraph>{plan.description}</Typography.Paragraph>
            <Typography.Text strong>ابتدا وابستگی‌ها:</Typography.Text>
            <Space wrap>{plan.dependencies.length ? plan.dependencies.map(d => <Tag color="geekblue" key={d}>{d}</Tag>) : <Tag>مستقل</Tag>}</Space>
            <Typography.Text strong>چهار بررسی اجرایی پیش‌نیاز:</Typography.Text>
            <ol>{plan.preflightSteps.map((v,i) => <li key={i}>{v}</li>)}</ol>
            <Typography.Paragraph><Typography.Text strong>عملیات تجاری هدف:</Typography.Text> {plan.businessSteps}</Typography.Paragraph>
            <Alert type="warning" showIcon message={plan.businessStatus==='LIMITED_CORE_FIXTURE' ? 'تنها ایجاد Fixture چهارجدولی قابل انجام است' : 'گردش‌کار تجاری کامل هنوز قابل اجرا نیست'} />
          </Card>
        })()}
        {(() => {
          const check = checkMap.get(drawer.id)
          if (!check) return <Alert type="info" message="نتایج پیش‌نیازها هنوز دریافت نشده است." />
          return <>
            <Alert type={check.schemaRoutesReady ? 'info' : 'warning'} showIcon
              message={check.schemaRoutesReady ? 'ساختارهای موردنیاز اولیه موجودند' : 'بعضی پیش‌نیازهای اولیه در سورس اجراشده موجود نیستند'}
              description="این بررسی فقط وجود جدول و Route را می‌سنجد؛ نه موفقیت اجرای فرایند تجاری." />
            <Typography.Text strong>جداول موردنیاز</Typography.Text>
            <Space wrap>{check.requiredTables.map(t => <Tag key={t} color={check.missingTables.includes(t) ? 'red' : 'green'}>{t}</Tag>)}</Space>
            <Typography.Text strong>مسیرهای API موردنیاز</Typography.Text>
            <Space direction="vertical">{check.requiredRoutes.map(r => <Tag key={r} color={check.missingRoutes.includes(r) ? 'red' : 'green'}>{r}</Tag>)}</Space>
          </>
        })()}
        <Alert type="warning" showIcon message="اجرای این سناریو هنوز فعال نشده است" description="پس از تکمیل گیت‌های امنیتی، همین سناریو با داده‌های واقعیِ آزمایشی در PostgreSQL اجرا خواهد شد." />
        <Button type="primary" onClick={() => { setSelectedIds([drawer.id]); setDrawer(null); setTab('scenarios') }}>انتخاب این سناریو برای بررسی ایمن</Button>
      </Space>}
    </Drawer>

    <Modal title="ساخت فضای آزمایشی جدید" open={createOpen} onCancel={() => setCreateOpen(false)}
      okText="ایجاد فضای آزمایشی" cancelText="انصراف" okButtonProps={{ disabled: !/^[A-Za-z0-9_-]{3,80}$/.test(newCode) || !newTitle.trim() || busy, loading: createWorkspace.isPending }}
      onOk={() => createWorkspace.mutate({ code: newCode, title: newTitle.trim() })}>
      <Space direction="vertical" style={{ width: '100%' }}>
        <Alert type="info" showIcon message="این عملیات فقط یک رکورد مدیریتی فضای آزمایشی ایجاد می‌کند؛ هنوز پرونده کسب‌وکار تولید نمی‌شود." />
        <Typography.Text>کد یکتای لاتین</Typography.Text>
        <Input value={newCode} onChange={e => setNewCode(e.target.value)} placeholder="example_finance_tests" maxLength={80} />
        <Typography.Text>عنوان فضای آزمایشی</Typography.Text>
        <Input value={newTitle} onChange={e => setNewTitle(e.target.value)} placeholder="آزمون مشارکت در ساخت - پاییز" maxLength={250} />
      </Space>
    </Modal>
  </div>
}
