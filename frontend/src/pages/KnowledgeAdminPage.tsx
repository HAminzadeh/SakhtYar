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
  Upload,
} from 'antd'
import {
  BookOutlined,
  CopyOutlined,
  DatabaseOutlined,
  PlayCircleOutlined,
  QuestionCircleOutlined,
  UploadOutlined,
  CloudUploadOutlined,
  CheckCircleOutlined,
  SafetyCertificateOutlined,
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

type ExportKit = {
  scriptName: string
  script: string
  runCommand: string
  sendCommand: string
  expectedZip: string
  sourcePath: string
  repository: string
  branch: string
}

type DatasetPackage = {
  id: string
  originalFilename: string
  sha256: string
  sizeBytes: number
  contentType?: string | null
  datasetVersion?: string | null
  status: string
  manifest?: Record<string, unknown>
  validationReport?: Record<string, unknown>
  dryRunReport?: Record<string, unknown>
  importedDatasetId?: string | null
  errorMessage?: string | null
  uploadedBy?: string | null
  uploadedAt?: string | null
  validatedAt?: string | null
  importStartedAt?: string | null
  importedAt?: string | null
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
  exportPackage: {
    title: 'راهنمای بسته استخراج برای ChatGPT',
    body: 'از این تب اسکریپت مکانیکی Export را دانلود کنید. اسکریپت فایل‌های PDF/PPT/PPTX را به متن و JSONL تبدیل می‌کند، Duplicateها را با Hash شناسایی می‌کند و یک ZIP می‌سازد. هیچ تحلیل AI یا استخراج دانش روی سیستم شما انجام نمی‌شود.',
  },  datasets: {
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
  exportPackage: { title: 'ChatGPT export package help', body: 'Download the mechanical corpus export script, run it locally, then send the resulting ZIP and prepared command to ChatGPT. No local AI analysis is performed.' },
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

  const exportKit = useQuery({
    queryKey: ['knowledge-export-kit'],
    queryFn: () => api<ExportKit>('/api/v1/knowledge/admin/export-kit'),
  })

  const downloadExportScript = () => {
    if (!exportKit.data) return
    const blob = new Blob([`\uFEFF${exportKit.data.script}`], {
      type: 'text/plain;charset=utf-8',
    })
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = exportKit.data.scriptName
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    URL.revokeObjectURL(url)
    message.success(
      fa ? 'اسکریپت Export دانلود شد.' : 'Export script downloaded.',
    )
  }

  const datasetPackages = useQuery({
    queryKey: ['knowledge-dataset-packages'],
    queryFn: () => api<DatasetPackage[]>('/api/v1/knowledge/admin/dataset-packages'),
    refetchInterval: (query) => {
      const rows = query.state.data as DatasetPackage[] | undefined
      return rows?.some((x) => ['IMPORT_QUEUED', 'IMPORTING'].includes(x.status)) ? 3000 : false
    },
  })

  const uploadDataset = useMutation({
    mutationFn: async (file: File) => {
      const body = new FormData()
      body.append('file', file)
      return api<DatasetPackage>('/api/v1/knowledge/admin/dataset-packages', {
        method: 'POST',
        body,
      })
    },
    onSuccess: () => {
      void datasetPackages.refetch()
      message.success(fa ? 'بسته Dataset ذخیره شد.' : 'Dataset package stored.')
    },
    onError: (e) => message.error(e instanceof Error ? e.message : 'Upload failed'),
  })

  const packageAction = useMutation({
    mutationFn: async ({ id, action }: { id: string; action: 'validate' | 'dry-run' | 'import' }) =>
      api<Record<string, unknown>>(`/api/v1/knowledge/admin/dataset-packages/${id}/${action}`, {
        method: 'POST',
      }),
    onSuccess: (_result, vars) => {
      void datasetPackages.refetch()
      message.success(
        fa
          ? vars.action === 'validate'
            ? 'اعتبارسنجی انجام شد.'
            : vars.action === 'dry-run'
              ? 'Dry Run با موفقیت انجام شد.'
              : 'Import در صف اجرا قرار گرفت.'
          : 'Operation completed.',
      )
    },
    onError: (e) => message.error(e instanceof Error ? e.message : 'Operation failed'),
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
      key: 'exportPackage',
      label: fa ? 'بسته استخراج' : 'Export Package',
      children: (
        <div className="sakhtyar-page-stack">
          <Card
            title={fa ? 'بسته استخراج برای ChatGPT' : 'ChatGPT Corpus Export Package'}
            extra={
              <Button
                icon={<QuestionCircleOutlined />}
                onClick={() => setHelpKey('exportPackage')}
              >
                {fa ? 'راهنما' : 'Help'}
              </Button>
            }
          >
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 18 }}
              message={
                fa
                  ? 'این مرحله فقط فایل‌های باینری را برای ارسال به ChatGPT قابل خواندن می‌کند.'
                  : 'This step only converts binary source files into a ChatGPT-readable package.'
              }
              description={
                fa
                  ? 'هیچ Classification، Summary، Rule Extraction یا Embedding روی سیستم شما انجام نمی‌شود.'
                  : 'No classification, summarization, rule extraction or embedding generation runs locally.'
              }
            />

            {exportKit.isLoading ? (
              <Card loading />
            ) : exportKit.data ? (
              <>
                <Row gutter={[16, 16]}>
                  <Col xs={24} lg={8}>
                    <Card size="small" title={fa ? '۱. دریافت اسکریپت' : '1. Download script'}>
                      <Typography.Paragraph type="secondary">
                        {fa
                          ? 'اسکریپت را در ریشه Repository ذخیره کنید.'
                          : 'Save the script in the repository root.'}
                      </Typography.Paragraph>
                      <Button
                        type="primary"
                        block
                        onClick={downloadExportScript}
                      >
                        {fa ? 'دانلود اسکریپت Export' : 'Download Export Script'}
                      </Button>
                      <Typography.Text code style={{ display: 'block', marginTop: 10 }}>
                        {exportKit.data.scriptName}
                      </Typography.Text>
                    </Card>
                  </Col>

                  <Col xs={24} lg={8}>
                    <Card size="small" title={fa ? '۲. اجرای Export' : '2. Run export'}>
                      <Typography.Paragraph type="secondary">
                        {fa
                          ? 'فرمان زیر را در PowerShell از ریشه پروژه اجرا کنید.'
                          : 'Run this command from the repository root.'}
                      </Typography.Paragraph>
                      <Button
                        block
                        icon={<CopyOutlined />}
                        onClick={() => copy(exportKit.data!.runCommand)}
                      >
                        {fa ? 'کپی دستور اجرا' : 'Copy Run Command'}
                      </Button>
                      <Typography.Paragraph style={{ marginTop: 10 }}>
                        <Typography.Text type="secondary">
                          {fa ? 'خروجی مورد انتظار:' : 'Expected output:'}
                        </Typography.Text>
                        <br />
                        <Typography.Text code>{exportKit.data.expectedZip}</Typography.Text>
                      </Typography.Paragraph>
                    </Card>
                  </Col>

                  <Col xs={24} lg={8}>
                    <Card size="small" title={fa ? '۳. ارسال برای ChatGPT' : '3. Send to ChatGPT'}>
                      <Typography.Paragraph type="secondary">
                        {fa
                          ? 'ZIP ساخته‌شده را همراه با این دستور برای ChatGPT بفرستید.'
                          : 'Send the generated ZIP together with this command.'}
                      </Typography.Paragraph>
                      <Button
                        block
                        icon={<CopyOutlined />}
                        onClick={() => copy(exportKit.data!.sendCommand)}
                      >
                        {fa ? 'کپی دستور ارسال' : 'Copy ChatGPT Command'}
                      </Button>
                    </Card>
                  </Col>
                </Row>

                <Card
                  size="small"
                  title={fa ? 'مشخصات Source' : 'Source details'}
                  style={{ marginTop: 16 }}
                >
                  <Descriptions column={{ xs: 1, md: 3 }} size="small">
                    <Descriptions.Item label="Repository">{exportKit.data.repository}</Descriptions.Item>
                    <Descriptions.Item label="Branch">{exportKit.data.branch}</Descriptions.Item>
                    <Descriptions.Item label="Source Path">{exportKit.data.sourcePath}</Descriptions.Item>
                  </Descriptions>
                </Card>

                <Card
                  size="small"
                  title={fa ? 'دستور ارسال به ChatGPT' : 'ChatGPT handoff command'}
                  style={{ marginTop: 16 }}
                  extra={
                    <Button
                      size="small"
                      icon={<CopyOutlined />}
                      onClick={() => copy(exportKit.data!.sendCommand)}
                    >
                      {fa ? 'کپی' : 'Copy'}
                    </Button>
                  }
                >
                  <Input.TextArea
                    readOnly
                    value={exportKit.data.sendCommand}
                    autoSize={{ minRows: 10, maxRows: 18 }}
                  />
                </Card>
              </>
            ) : (
              <Alert
                type="error"
                showIcon
                message={fa ? 'اطلاعات Export Kit دریافت نشد.' : 'Export kit could not be loaded.'}
              />
            )}
          </Card>
        </div>
      ),
    },
    {
      key: 'datasetImport',
      label: fa ? 'ورود Dataset' : 'Dataset Import',
      children: (
        <div className="sakhtyar-page-stack">
          <Card title={fa ? 'مرکز ورود و نگه‌داری Dataset' : 'Dataset Import & Archive Center'}>
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 18 }}
              message={fa ? 'فایل اصلی در MinIO و تاریخچه و Metadata در PostgreSQL نگه‌داری می‌شود.' : 'Package binary is stored in MinIO; metadata and history are stored in PostgreSQL.'}
              description={fa
                ? 'جریان امن: Upload → Validate → Dry Run → Import → Verify. Import هیچ Datasetی را خودکار ACTIVE نمی‌کند.'
                : 'Safe flow: Upload → Validate → Dry Run → Import → Verify. Import never auto-activates a dataset.'}
            />

            <Upload.Dragger
              accept=".zip,application/zip"
              maxCount={1}
              showUploadList={false}
              disabled={uploadDataset.isPending}
              beforeUpload={(file) => {
                uploadDataset.mutate(file as File)
                return false
              }}
            >
              <p className="ant-upload-drag-icon"><CloudUploadOutlined /></p>
              <p className="ant-upload-text">
                {fa ? 'Knowledge Dataset ZIP را اینجا رها کنید یا کلیک کنید' : 'Drop or select a Knowledge Dataset ZIP'}
              </p>
              <p className="ant-upload-hint">
                {fa ? 'فایل پس از Upload در MinIO آرشیو می‌شود و SHA256 آن در DB ثبت می‌شود.' : 'The original package is archived in MinIO and its SHA256 is recorded in the database.'}
              </p>
            </Upload.Dragger>

            <Card
              size="small"
              style={{ marginTop: 18 }}
              title={fa ? 'تاریخچه بسته‌های Dataset' : 'Dataset Package History'}
              extra={
                <Button onClick={() => datasetPackages.refetch()} loading={datasetPackages.isFetching}>
                  {fa ? 'تازه‌سازی' : 'Refresh'}
                </Button>
              }
            >
              <Table
                rowKey="id"
                loading={datasetPackages.isLoading}
                dataSource={datasetPackages.data ?? []}
                scroll={{ x: 1200 }}
                pagination={{ pageSize: 8 }}
                columns={[
                  {
                    title: fa ? 'فایل' : 'Package',
                    dataIndex: 'originalFilename',
                    width: 230,
                    render: (v: string, row: DatasetPackage) => (
                      <Space direction="vertical" size={0}>
                        <Typography.Text strong>{v}</Typography.Text>
                        <Typography.Text type="secondary" style={{ fontSize: 11 }}>
                          {(row.sizeBytes / 1024 / 1024).toFixed(2)} MB
                        </Typography.Text>
                      </Space>
                    ),
                  },
                  {
                    title: fa ? 'نسخه' : 'Version',
                    dataIndex: 'datasetVersion',
                    width: 100,
                    render: (v?: string | null) => v || '—',
                  },
                  {
                    title: fa ? 'وضعیت' : 'Status',
                    dataIndex: 'status',
                    width: 150,
                    render: statusTag,
                  },
                  {
                    title: 'SHA256',
                    dataIndex: 'sha256',
                    width: 150,
                    render: (v: string) => <Typography.Text code>{v.slice(0, 12)}…</Typography.Text>,
                  },
                  {
                    title: fa ? 'آپلود' : 'Uploaded',
                    dataIndex: 'uploadedAt',
                    width: 170,
                    render: (v?: string | null) => v ? new Date(v).toLocaleString(fa ? 'fa-IR' : 'en-GB') : '—',
                  },
                  {
                    title: fa ? 'عملیات' : 'Actions',
                    key: 'actions',
                    fixed: 'right' as const,
                    width: 430,
                    render: (_: unknown, row: DatasetPackage) => (
                      <Space wrap>
                        <Button
                          size="small"
                          icon={<SafetyCertificateOutlined />}
                          loading={packageAction.isPending}
                          onClick={() => packageAction.mutate({ id: row.id, action: 'validate' })}
                        >
                          Validate
                        </Button>
                        <Button
                          size="small"
                          icon={<CheckCircleOutlined />}
                          loading={packageAction.isPending}
                          onClick={() => packageAction.mutate({ id: row.id, action: 'dry-run' })}
                        >
                          Dry Run
                        </Button>
                        <Button
                          size="small"
                          type="primary"
                          icon={<UploadOutlined />}
                          disabled={['IMPORT_QUEUED', 'IMPORTING'].includes(row.status)}
                          loading={packageAction.isPending}
                          onClick={() => packageAction.mutate({ id: row.id, action: 'import' })}
                        >
                          Import
                        </Button>
                        <Button
                          size="small"
                          onClick={() => {
                            window.location.href = `/api/v1/knowledge/admin/dataset-packages/${row.id}/content`
                          }}
                        >
                          {fa ? 'دانلود مجدد' : 'Download'}
                        </Button>
                      </Space>
                    ),
                  },
                ]}
                expandable={{
                  expandedRowRender: (row: DatasetPackage) => (
                    <Descriptions size="small" bordered column={{ xs: 1, md: 2 }}>
                      <Descriptions.Item label={fa ? 'کاربر' : 'Uploaded by'}>{row.uploadedBy || '—'}</Descriptions.Item>
                      <Descriptions.Item label={fa ? 'Dataset ID' : 'Dataset ID'}>{row.importedDatasetId || '—'}</Descriptions.Item>
                      <Descriptions.Item label={fa ? 'خطا' : 'Error'} span={2}>{row.errorMessage || '—'}</Descriptions.Item>
                    </Descriptions>
                  ),
                }}
              />
            </Card>
          </Card>
        </div>
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