import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Col,
  Descriptions,
  Drawer,
  Form,
  Input,
  Progress,
  Row,
  Select,
  Space,
  Statistic,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd'
import {
  BookOutlined,
  CopyOutlined,
  DatabaseOutlined,
  PlayCircleOutlined,
  QuestionCircleOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { api } from '../api/client'
import { useI18n } from '../i18n/LanguageProvider'

type Profile = {
  id: string
  repository: string
  branch: string
  sourcePath: string
  runbookVersion: string
  runbookPath: string
  defaultMode: 'FULL' | 'INCREMENTAL'
  enabled: boolean
}

type Run = {
  runCode: string
  mode: string
  status: string
  stage: string
  baselineCommit?: string | null
  previousDatasetVersion?: string | null
  targetDatasetVersion?: string | null
  runbookVersion: string
  requestedBy?: string | null
  createdAt?: string | null
  errorMessage?: string | null
}

type Dataset = {
  datasetVersion: string
  taxonomyVersion?: string | null
  schemaVersion: string
  sourceCommit?: string | null
  status: string
}

type Status = {
  profile: Profile
  activeDataset?: Dataset | null
  latestDataset?: Dataset | null
  counts: {
    datasets: number
    runs: number
    pendingReview: number
    openConflicts: number
    openFeedback: number
    goldenQuestions: number
  }
  health: {
    pendingReviews: number
    openConflicts: number
    unresolvedFeedback: number
    watchedSources: number
    enabledGoldenQuestions: number
    latestCitationCoverage?: number | null
  }
  recentRuns: Run[]
}

type CommandResult = {
  runCode: string
  mode: string
  command: string
  previousDatasetVersion?: string | null
  baselineCommit?: string | null
  runbookVersion: string
  createdAt: string
}

const helpFa: Record<string, { title: string; body: string }> = {
  overview: {
    title: 'راهنمای نمای کلی',
    body: 'این بخش سلامت پایگاه دانش، Dataset فعال، بررسی‌های باز، تعارض‌ها و پوشش استناد را نشان می‌دهد. وارد شدن یک Dataset به دیتابیس به معنی فعال شدن آن نیست.',
  },
  build: {
    title: 'راهنمای ساخت Dataset',
    body: 'برای بار اول حالت Full و برای به‌روزرسانی‌های بعدی حالت Incremental را انتخاب کنید. دکمه تولید دستور استخراج یک فرمان نسخه‌دار می‌سازد که باید برای ChatGPT ارسال شود.',
  },
  datasets: {
    title: 'راهنمای Datasetها',
    body: 'هر Dataset یک نسخه مستقل از دانش ساخت‌یار است. فعال‌سازی مرحله‌ای جدا از Import است تا همیشه بتوان نسخه قبلی را نگه داشت یا Rollback کرد.',
  },
  review: {
    title: 'راهنمای بررسی',
    body: 'طبقه‌بندی‌ها به‌صورت گروهی قابل انتخاب و تأیید خواهند بود. Rule Candidateها باید محافظه‌کارانه‌تر بررسی شوند و تعارض یا منبع ضعیف نباید بدون بررسی تأیید شود.',
  },
  quality: {
    title: 'راهنمای کیفیت',
    body: 'Golden Questions، آزمون جست‌وجو، Citation Coverage، Staleness و Source Priority برای جلوگیری از افت کیفیت نسخه جدید استفاده می‌شوند.',
  },
  operations: {
    title: 'راهنمای عملیات',
    body: 'Dry Run، مقایسه نسخه‌ها، Import، فعال‌سازی، Rollback و Disaster Recovery در این بخش مدیریت می‌شوند.',
  },
  history: {
    title: 'راهنمای تاریخچه',
    body: 'هر دستور با Run ID و نسخه Runbook ذخیره می‌شود تا اجرای قبلی در آینده قابل تکرار و قابل Audit باشد.',
  },
  settings: {
    title: 'راهنمای تنظیمات',
    body: 'Repository، Branch، مسیر منابع و نسخه Runbook را از این بخش مدیریت می‌کنید.',
  },
}

const helpEn: typeof helpFa = {
  overview: { title: 'Overview help', body: 'Shows platform health, the active dataset, pending reviews, conflicts and citation coverage. Imported does not mean active.' },
  build: { title: 'Build help', body: 'Use Full for the first build and Incremental for later updates. The generated versioned command is sent to ChatGPT.' },
  datasets: { title: 'Datasets help', body: 'Each dataset is a separate version of SakhtYar knowledge. Activation is a separate controlled step.' },
  review: { title: 'Review help', body: 'Classifications support bulk review. Rule candidates require stricter source and conflict controls.' },
  quality: { title: 'Quality help', body: 'Golden questions, search regression, citation coverage, staleness and source priority protect quality.' },
  operations: { title: 'Operations help', body: 'Dry run, compare, import, activate, rollback and disaster recovery are managed here.' },
  history: { title: 'History help', body: 'Every generated command keeps a Run ID and runbook version for reproducibility and audit.' },
  settings: { title: 'Settings help', body: 'Configure repository, branch, source path and versioned runbook.' },
}

function statusTag(value?: string | null) {
  const color =
    value === 'ACTIVE' || value === 'COMPLETED'
      ? 'green'
      : value === 'FAILED'
        ? 'red'
        : value?.includes('REVIEW')
          ? 'gold'
          : 'blue'
  return <Tag color={color}>{value || '—'}</Tag>
}

export function KnowledgeAdminPage() {
  const { message } = AntdApp.useApp()
  const { language } = useI18n()
  const fa = language === 'fa'
  const queryClient = useQueryClient()
  const [helpKey, setHelpKey] = useState<string | null>(null)
  const [commandResult, setCommandResult] = useState<CommandResult | null>(null)
  const [mode, setMode] = useState<'FULL' | 'INCREMENTAL'>('INCREMENTAL')
  const [baselineCommit, setBaselineCommit] = useState('')
  const [form] = Form.useForm<Profile>()

  const status = useQuery({
    queryKey: ['knowledge-admin-status'],
    queryFn: () => api<Status>('/api/v1/knowledge/admin/status'),
  })

  const profile = useQuery({
    queryKey: ['knowledge-admin-profile'],
    queryFn: () => api<Profile>('/api/v1/knowledge/admin/profile'),
  })

  const runs = useQuery({
    queryKey: ['knowledge-admin-runs'],
    queryFn: () => api<Run[]>('/api/v1/knowledge/admin/runs?limit=50'),
  })

  const generate = useMutation({
    mutationFn: () =>
      api<CommandResult>('/api/v1/knowledge/admin/commands/generate', {
        method: 'POST',
        body: JSON.stringify({
          mode,
          baselineCommit: baselineCommit.trim() || null,
        }),
      }),
    onSuccess: (result) => {
      setCommandResult(result)
      void status.refetch()
      void runs.refetch()
      message.success(fa ? 'دستور استخراج تولید و در تاریخچه ثبت شد.' : 'Extraction command generated and recorded.')
    },
    onError: (e) => message.error(e instanceof Error ? e.message : 'Failed'),
  })

  const saveProfile = useMutation({
    mutationFn: (value: Profile) =>
      api<Profile>('/api/v1/knowledge/admin/profile', {
        method: 'PUT',
        body: JSON.stringify(value),
      }),
    onSuccess: (value) => {
      queryClient.setQueryData(['knowledge-admin-profile'], value)
      void status.refetch()
      message.success(fa ? 'تنظیمات ذخیره شد.' : 'Settings saved.')
    },
    onError: (e) => message.error(e instanceof Error ? e.message : 'Failed'),
  })

  const copy = async (text: string) => {
    await navigator.clipboard.writeText(text)
    message.success(fa ? 'در کلیپ‌بورد کپی شد.' : 'Copied to clipboard.')
  }

  const h = fa ? helpFa : helpEn
  const data = status.data
  const citation = data?.health.latestCitationCoverage
  const citationPercent =
    citation == null ? 0 : Math.round(Number(citation) * (Number(citation) <= 1 ? 100 : 1))
  const settingsInitial = useMemo(() => profile.data, [profile.data])

  const tabs = [
    {
      key: 'overview',
      label: fa ? 'نمای کلی' : 'Overview',
      children: (
        <div className="sakhtyar-page-stack">
          <Row gutter={[12, 12]}>
            <Col xs={12} lg={4}><Card><Statistic title={fa ? 'Datasetها' : 'Datasets'} value={data?.counts.datasets ?? 0} /></Card></Col>
            <Col xs={12} lg={4}><Card><Statistic title={fa ? 'اجراها' : 'Runs'} value={data?.counts.runs ?? 0} /></Card></Col>
            <Col xs={12} lg={4}><Card><Statistic title={fa ? 'بررسی باز' : 'Pending Review'} value={data?.counts.pendingReview ?? 0} /></Card></Col>
            <Col xs={12} lg={4}><Card><Statistic title={fa ? 'تعارض باز' : 'Conflicts'} value={data?.counts.openConflicts ?? 0} /></Card></Col>
            <Col xs={12} lg={4}><Card><Statistic title={fa ? 'بازخورد باز' : 'Feedback'} value={data?.counts.openFeedback ?? 0} /></Card></Col>
            <Col xs={12} lg={4}><Card><Statistic title="Golden Questions" value={data?.counts.goldenQuestions ?? 0} /></Card></Col>
          </Row>

          <Card title={fa ? 'سلامت پایگاه دانش' : 'Knowledge Health'}>
            <Row gutter={[16, 16]}>
              <Col xs={24} md={8}>
                <Typography.Text type="secondary">Citation Coverage</Typography.Text>
                <Progress percent={citationPercent} status={citationPercent >= 99 ? 'success' : 'normal'} />
              </Col>
              <Col xs={24} md={8}>
                <Descriptions size="small" column={1}>
                  <Descriptions.Item label={fa ? 'منابع تحت پایش' : 'Watched sources'}>{data?.health.watchedSources ?? 0}</Descriptions.Item>
                  <Descriptions.Item label={fa ? 'Golden Question فعال' : 'Enabled golden questions'}>{data?.health.enabledGoldenQuestions ?? 0}</Descriptions.Item>
                </Descriptions>
              </Col>
              <Col xs={24} md={8}>
                <Alert
                  showIcon
                  type={data?.activeDataset ? 'success' : 'warning'}
                  message={
                    data?.activeDataset
                      ? `${fa ? 'Dataset فعال' : 'Active dataset'}: ${data.activeDataset.datasetVersion}`
                      : fa ? 'هنوز Dataset فعالی وجود ندارد.' : 'No active dataset yet.'
                  }
                />
              </Col>
            </Row>
          </Card>
        </div>
      ),
    },
    {
      key: 'build',
      label: fa ? 'ساخت' : 'Build',
      children: (
        <Card title={fa ? 'تولید دستور استخراج دانش' : 'Generate knowledge extraction command'}>
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
            message={fa ? 'پردازش سنگین داخل ساخت‌یار انجام نمی‌شود' : 'Heavy processing does not run inside SakhtYar'}
            description={fa
              ? 'این صفحه فقط فرمان نسخه‌دار می‌سازد. خواندن PDF، OCR، تحلیل با LLM و تولید Embedding توسط ChatGPT انجام و Dataset آماده بعداً Import می‌شود.'
              : 'This page creates a versioned command. PDF/OCR/LLM/embeddings are prepared externally and imported later.'}
          />
          <Space wrap>
            <Select
              value={mode}
              onChange={setMode}
              style={{ width: 190 }}
              options={[
                { value: 'INCREMENTAL', label: fa ? 'Incremental - پیشنهادی' : 'Incremental - recommended' },
                { value: 'FULL', label: fa ? 'Full Build - ساخت کامل' : 'Full Build' },
              ]}
            />
            <Input
              style={{ width: 320 }}
              placeholder={fa ? 'Baseline Commit - اختیاری' : 'Baseline Commit - optional'}
              value={baselineCommit}
              onChange={(e) => setBaselineCommit(e.target.value)}
            />
            <Button type="primary" icon={<PlayCircleOutlined />} loading={generate.isPending} onClick={() => generate.mutate()}>
              {fa ? 'تولید دستور استخراج' : 'Generate Extraction Command'}
            </Button>
          </Space>

          {commandResult ? (
            <Card
              size="small"
              style={{ marginTop: 18 }}
              title={`${fa ? 'دستور آماده' : 'Prepared command'} — ${commandResult.runCode}`}
              extra={<Button icon={<CopyOutlined />} onClick={() => copy(commandResult.command)}>{fa ? 'کپی' : 'Copy'}</Button>}
            >
              <Input.TextArea value={commandResult.command} autoSize={{ minRows: 14, maxRows: 26 }} readOnly />
            </Card>
          ) : null}
        </Card>
      ),
    },
    {
      key: 'datasets',
      label: fa ? 'Datasetها' : 'Datasets',
      children: (
        <Card title={fa ? 'چرخه Dataset' : 'Dataset lifecycle'}>
          <Alert
            showIcon
            type="info"
            message={fa ? 'Import با فعال‌سازی متفاوت است.' : 'Import and activation are separate.'}
            description="PREPARED → VALIDATED → IMPORTED → REVIEW → READY_FOR_ACTIVATION → ACTIVE → ARCHIVED"
          />
          <Descriptions style={{ marginTop: 16 }} column={{ xs: 1, md: 2 }}>
            <Descriptions.Item label={fa ? 'آخرین Dataset' : 'Latest dataset'}>
              {data?.latestDataset ? statusTag(`${data.latestDataset.datasetVersion} / ${data.latestDataset.status}`) : '—'}
            </Descriptions.Item>
            <Descriptions.Item label={fa ? 'Dataset فعال' : 'Active dataset'}>
              {data?.activeDataset ? statusTag(data.activeDataset.datasetVersion) : '—'}
            </Descriptions.Item>
          </Descriptions>
        </Card>
      ),
    },
    {
      key: 'review',
      label: fa ? 'بررسی' : 'Review',
      children: (
        <Card title={fa ? 'بررسی گروهی و حاکمیت دانش' : 'Bulk Review & Governance'}>
          <Alert
            showIcon
            type="info"
            message={fa ? 'پس از اولین Import، Batchهای طبقه‌بندی و Rule در اینجا فعال می‌شوند.' : 'Classification/rule batches become active after the first prepared dataset import.'}
            description={fa
              ? 'Select All، تأیید/رد گروهی، تغییر Domain/Category/Authority و فیلتر Confidence روی همین مدل داده اجرا می‌شود.'
              : 'Select All, bulk approval/rejection, domain/category/authority changes and confidence filters use the review model.'}
          />
        </Card>
      ),
    },
    {
      key: 'quality',
      label: fa ? 'کیفیت' : 'Quality',
      children: (
        <Card title={fa ? 'کنترل کیفیت' : 'Quality Gates'}>
          <Space direction="vertical" style={{ width: '100%' }}>
            <Alert type="success" showIcon message={fa ? 'Golden Questions و Search Regression آماده است.' : 'Golden Questions / Search Regression schema ready'} />
            <Alert type="success" showIcon message={fa ? 'Citation Coverage و Quality Run آماده است.' : 'Citation Coverage / Quality Run schema ready'} />
            <Alert type="success" showIcon message={fa ? 'Source Priority، Staleness و Watchlist آماده است.' : 'Source Priority / Staleness / Watchlist schema ready'} />
            <Alert type="success" showIcon message={fa ? 'Feedback و Usage Analytics آماده است.' : 'Feedback / Usage Analytics schema ready'} />
            <Alert type="success" showIcon message={fa ? 'Document Diff و Impact Analysis آماده است.' : 'Document Diff / Impact Analysis schema ready'} />
          </Space>
        </Card>
      ),
    },
    {
      key: 'operations',
      label: fa ? 'عملیات' : 'Operations',
      children: (
        <Card title={fa ? 'عملیات Dataset' : 'Dataset Operations'}>
          <Alert
            type="warning"
            showIcon
            message={fa ? 'این عملیات بعد از تولید اولین Knowledge Seed Package فعال می‌شوند.' : 'These operations are activated after the first Knowledge Seed Package is produced.'}
            description={fa
              ? 'Dry Run، Verify Checksum، Compare، Import، Activate، Rollback و Disaster Recovery توسط اسکریپت Dataset بعدی کامل می‌شوند.'
              : 'Dry run, checksum verification, compare, import, activation, rollback and disaster recovery are completed by the next dataset importer.'}
          />
        </Card>
      ),
    },
    {
      key: 'history',
      label: fa ? 'تاریخچه' : 'History',
      children: (
        <Card title={fa ? 'تاریخچه دستورات و اجراها' : 'Command & run history'}>
          <Table
            size="small"
            rowKey="runCode"
            loading={runs.isLoading}
            dataSource={runs.data ?? []}
            pagination={{ pageSize: 10 }}
            columns={[
              { title: 'Run ID', dataIndex: 'runCode' },
              { title: fa ? 'حالت' : 'Mode', dataIndex: 'mode' },
              { title: fa ? 'وضعیت' : 'Status', dataIndex: 'status', render: statusTag },
              { title: 'Runbook', dataIndex: 'runbookVersion' },
              {
                title: fa ? 'زمان' : 'Created',
                dataIndex: 'createdAt',
                render: (v?: string | null) => v ? new Date(v).toLocaleString(fa ? 'fa-IR' : 'en-GB') : '—',
              },
              {
                title: fa ? 'دستور' : 'Command',
                key: 'command',
                render: (_: unknown, row: Run) => (
                  <Button
                    size="small"
                    icon={<CopyOutlined />}
                    onClick={async () => {
                      const result = await api<{ command: string }>(`/api/v1/knowledge/admin/runs/${row.runCode}/command`)
                      await copy(result.command)
                    }}
                  >
                    {fa ? 'کپی' : 'Copy'}
                  </Button>
                ),
              },
            ]}
          />
        </Card>
      ),
    },
    {
      key: 'settings',
      label: fa ? 'تنظیمات' : 'Settings',
      children: (
        <Card title={fa ? 'تنظیمات Pipeline' : 'Pipeline Settings'}>
          {settingsInitial ? (
            <Form
              form={form}
              layout="vertical"
              initialValues={settingsInitial}
              onFinish={(value) => saveProfile.mutate(value as Profile)}
            >
              <Row gutter={16}>
                <Col xs={24} md={12}><Form.Item label="Repository" name="repository" rules={[{ required: true }]}><Input /></Form.Item></Col>
                <Col xs={24} md={12}><Form.Item label="Branch" name="branch" rules={[{ required: true }]}><Input /></Form.Item></Col>
                <Col xs={24} md={12}><Form.Item label="Source Path" name="sourcePath" rules={[{ required: true }]}><Input /></Form.Item></Col>
                <Col xs={24} md={12}><Form.Item label="Runbook Version" name="runbookVersion" rules={[{ required: true }]}><Input /></Form.Item></Col>
                <Col xs={24} md={16}><Form.Item label="Runbook Path" name="runbookPath" rules={[{ required: true }]}><Input /></Form.Item></Col>
                <Col xs={24} md={8}>
                  <Form.Item label="Default Mode" name="defaultMode">
                    <Select options={[{ value: 'INCREMENTAL' }, { value: 'FULL' }]} />
                  </Form.Item>
                </Col>
              </Row>
              <Button type="primary" htmlType="submit" loading={saveProfile.isPending}>
                {fa ? 'ذخیره تنظیمات' : 'Save Settings'}
              </Button>
            </Form>
          ) : <Card loading />}
        </Card>
      ),
    },
  ]

  return (
    <div className="sakhtyar-page-stack sakhtyar-knowledge-admin">
      <Card>
        <div className="sakhtyar-section-actions">
          <div>
            <Space>
              <DatabaseOutlined style={{ fontSize: 28 }} />
              <div>
                <Typography.Title level={2} style={{ margin: 0 }}>
                  {fa ? 'مرکز مدیریت دانش ساخت‌یار' : 'SakhtYar Knowledge Administration Center'}
                </Typography.Title>
                <Typography.Text type="secondary">
                  {fa ? 'کنترل ساخت، نسخه‌بندی، بررسی و عملیات پایگاه دانش مرکزی' : 'Build, version, review and operate the central Knowledge Platform'}
                </Typography.Text>
              </div>
            </Space>
          </div>
          <Space>
            <Button icon={<QuestionCircleOutlined />} onClick={() => setHelpKey('overview')}>
              {fa ? 'راهنما' : 'Help'}
            </Button>
            <Button onClick={() => status.refetch()} loading={status.isFetching}>
              {fa ? 'تازه‌سازی' : 'Refresh'}
            </Button>
          </Space>
        </div>
      </Card>

      <Card>
        <Tabs
          items={tabs.map((tab) => ({
            ...tab,
            label: (
              <Space size={5}>
                <span>{tab.label}</span>
                <QuestionCircleOutlined
                  onClick={(event) => {
                    event.stopPropagation()
                    setHelpKey(tab.key)
                  }}
                />
              </Space>
            ),
          }))}
        />
      </Card>

      <Drawer
        open={Boolean(helpKey)}
        onClose={() => setHelpKey(null)}
        title={helpKey ? h[helpKey]?.title : ''}
        width={fa ? 520 : 500}
      >
        <Typography.Paragraph>{helpKey ? h[helpKey]?.body : ''}</Typography.Paragraph>
        <Typography.Title level={5}>{fa ? 'گردش کار پیشنهادی' : 'Recommended workflow'}</Typography.Title>
        <ol>
          <li>{fa ? 'منبع و تنظیمات را بررسی کنید.' : 'Review source and settings.'}</li>
          <li>{fa ? 'فرمان Full یا Incremental تولید کنید.' : 'Generate a Full or Incremental command.'}</li>
          <li>{fa ? 'فرمان را برای ChatGPT ارسال کنید.' : 'Send the command to ChatGPT.'}</li>
          <li>{fa ? 'Dataset آماده و اسکریپت Import را دریافت کنید.' : 'Receive the prepared dataset and import script.'}</li>
          <li>{fa ? 'Dry Run و Import را اجرا کنید.' : 'Run dry-run and import.'}</li>
          <li>{fa ? 'Review و کنترل‌های کیفیت را بررسی کنید.' : 'Review quality gates.'}</li>
          <li>{fa ? 'در پایان Dataset را فعال کنید.' : 'Activate the dataset only at the end.'}</li>
        </ol>
      </Drawer>
    </div>
  )
}