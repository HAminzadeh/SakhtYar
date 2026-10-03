import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Progress,
  Row,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd'
import {
  CheckCircleOutlined,
  CloudDownloadOutlined,
  ReloadOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery } from '@tanstack/react-query'
import { api } from '../api/client'
import { useI18n } from '../i18n/LanguageProvider'

type ImportItem = {
  dataset: string
  sourceUrl?: string | null
  status: string
  recordCount: number
  startedAt?: string | null
  finishedAt?: string | null
  errorMessage?: string | null
}

type MasterDataStatus = {
  running: boolean
  stage?: string | null
  mode?: string | null
  lastError?: string | null
  startedAt?: string | null
  finishedAt?: string | null
  countryCount: number
  divisionCount: number
  cityCount: number
  currencyCount: number
  recentImports: ImportItem[]
}

const stageOrder = ['STARTING', 'COUNTRIES', 'DIVISIONS', 'CITIES', 'COMPLETED']

function stagePercent(stage?: string | null) {
  if (!stage) return 0
  if (stage === 'FAILED') return 100
  const index = stageOrder.indexOf(stage)
  if (index < 0) return 0
  return Math.round((index / (stageOrder.length - 1)) * 100)
}

function statusTag(status?: string | null, fa = true) {
  if (status === 'COMPLETED') {
    return <Tag color="green">{fa ? 'تکمیل شد' : 'Completed'}</Tag>
  }
  if (status === 'FAILED') {
    return <Tag color="red">{fa ? 'ناموفق' : 'Failed'}</Tag>
  }
  if (status === 'RUNNING') {
    return <Tag color="blue">{fa ? 'در حال اجرا' : 'Running'}</Tag>
  }
  return <Tag>{status || '—'}</Tag>
}

function stageLabel(stage?: string | null, fa = true) {
  const faMap: Record<string, string> = {
    IDLE: 'آماده',
    STARTING: 'شروع',
    COUNTRIES: 'کشورها',
    DIVISIONS: 'تقسیمات کشوری',
    CITIES: 'شهرها',
    COMPLETED: 'تکمیل‌شده',
    FAILED: 'خطا',
  }
  const enMap: Record<string, string> = {
    IDLE: 'Idle',
    STARTING: 'Starting',
    COUNTRIES: 'Countries',
    DIVISIONS: 'Administrative divisions',
    CITIES: 'Cities',
    COMPLETED: 'Completed',
    FAILED: 'Failed',
  }
  const map = fa ? faMap : enMap
  return map[stage ?? ''] ?? stage ?? '—'
}

export function MasterDataAdminPanel() {
  const { language } = useI18n()
  const fa = language === 'fa'

  const status = useQuery({
    queryKey: ['global-master-data-status-v4'],
    queryFn: () => api<MasterDataStatus>('/api/v1/global/master-data/status'),
    refetchInterval: (query) => query.state.data?.running ? 1500 : 10000,
  })

  // explicit URL in the actual mutation below.
  const start = useMutation({
    mutationFn: (mode: 'quick' | 'full') =>
      api(`/api/v1/global/master-data/import/geonames/async?mode=${mode}`, {
        method: 'POST',
      }),
    onSuccess: () => {
      void status.refetch()
    },
  })

  const data = status.data
  const stage = data?.stage ?? 'IDLE'
  const failed = stage === 'FAILED' || Boolean(data?.lastError)
  const progress = data?.running ? stagePercent(stage) : stage === 'COMPLETED' ? 100 : 0

  return (
    <Card className="sakhtyar-settings-card sakhtyar-master-data-card">
      <div className="sakhtyar-section-actions">
        <div>
          <Typography.Title level={3} style={{ margin: 0 }}>
            {fa ? 'داده‌های مرجع جهانی' : 'Global Master Data'}
          </Typography.Title>
          <Typography.Paragraph type="secondary" style={{ marginTop: 6 }}>
            {fa
              ? 'کشورها، تقسیمات کشوری و شهرها را از GeoNames همگام‌سازی کنید. ارزها از کاتالوگ Java به‌صورت مستقل مدیریت می‌شوند.'
              : 'Synchronize countries, administrative divisions and cities from GeoNames. Currencies are managed independently from the Java catalog.'}
          </Typography.Paragraph>
        </div>

        <Button
          icon={<ReloadOutlined />}
          onClick={() => status.refetch()}
          loading={status.isFetching}
        >
          {fa ? 'تازه‌سازی' : 'Refresh'}
        </Button>
      </div>

      <Row gutter={[12, 12]} style={{ marginTop: 8 }}>
        <Col xs={12} md={6}>
          <Card className="sakhtyar-master-stat">
            <Statistic title={fa ? 'کشورها' : 'Countries'} value={data?.countryCount ?? 0} />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card className="sakhtyar-master-stat">
            <Statistic title={fa ? 'تقسیمات کشوری' : 'Divisions'} value={data?.divisionCount ?? 0} />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card className="sakhtyar-master-stat">
            <Statistic title={fa ? 'شهرها' : 'Cities'} value={data?.cityCount ?? 0} />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card className="sakhtyar-master-stat">
            <Statistic title={fa ? 'ارزها' : 'Currencies'} value={data?.currencyCount ?? 0} />
          </Card>
        </Col>
      </Row>

      <div className="sakhtyar-master-sync-panel">
        <div className="sakhtyar-master-sync-head">
          <div>
            <Typography.Text strong>
              {fa ? 'همگام‌سازی GeoNames' : 'GeoNames synchronization'}
            </Typography.Text>
            <div className="sakhtyar-chart-subtitle">
              {fa
                ? 'حالت سریع برای توسعه محلی از cities500.zip استفاده می‌کند؛ حالت کامل از allCountries.zip.'
                : 'Quick mode uses cities500.zip; full mode uses allCountries.zip.'}
            </div>
          </div>
          <Tag color={data?.running ? 'processing' : failed ? 'red' : stage === 'COMPLETED' ? 'green' : 'default'}>
            {stageLabel(stage, fa)}
          </Tag>
        </div>

        {data?.running ? (
          <Progress
            percent={Math.max(5, progress)}
            status="active"
            strokeLinecap="round"
          />
        ) : null}

        {failed ? (
          <Alert
            type="error"
            showIcon
            icon={<WarningOutlined />}
            message={fa ? 'آخرین همگام‌سازی ناموفق بود' : 'Last synchronization failed'}
            description={data?.lastError || data?.recentImports?.find((x) => x.status === 'FAILED')?.errorMessage || '—'}
          />
        ) : stage === 'COMPLETED' ? (
          <Alert
            type="success"
            showIcon
            icon={<CheckCircleOutlined />}
            message={fa ? 'آخرین همگام‌سازی با موفقیت تکمیل شد' : 'Last synchronization completed successfully'}
          />
        ) : null}

        <Space wrap>
          <Button
            type="primary"
            icon={<CloudDownloadOutlined />}
            loading={start.isPending || data?.running}
            disabled={Boolean(data?.running)}
            onClick={() => start.mutate('quick')}
          >
            {fa ? 'شروع سریع' : 'Start quick sync'}
          </Button>
          <Button
            icon={<CloudDownloadOutlined />}
            loading={start.isPending || data?.running}
            disabled={Boolean(data?.running)}
            onClick={() => start.mutate('full')}
          >
            {fa ? 'همگام‌سازی کامل' : 'Start full sync'}
          </Button>
        </Space>

        <Descriptions column={{ xs: 1, md: 3 }} size="small">
          <Descriptions.Item label={fa ? 'مرحله جاری' : 'Current stage'}>
            {stageLabel(stage, fa)}
          </Descriptions.Item>
          <Descriptions.Item label={fa ? 'حالت' : 'Mode'}>
            {data?.mode === 'full' ? (fa ? 'کامل' : 'Full') : (fa ? 'سریع' : 'Quick')}
          </Descriptions.Item>
          <Descriptions.Item label={fa ? 'آخرین شروع' : 'Last started'}>
            {data?.startedAt ? new Date(data.startedAt).toLocaleString(fa ? 'fa-IR' : 'en-GB') : '—'}
          </Descriptions.Item>
        </Descriptions>
      </div>

      <Card
        size="small"
        title={fa ? 'تاریخچه همگام‌سازی' : 'Synchronization history'}
        className="sakhtyar-master-history"
      >
        <Table
          size="small"
          rowKey={(row) => `${row.dataset}-${row.startedAt ?? 'none'}`}
          pagination={{ pageSize: 6, hideOnSinglePage: true }}
          dataSource={data?.recentImports ?? []}
          locale={{ emptyText: fa ? 'هنوز هیچ اجرای GeoNames ثبت نشده است.' : 'No GeoNames run has been recorded yet.' }}
          columns={[
            {
              title: fa ? 'داده' : 'Dataset',
              dataIndex: 'dataset',
            },
            {
              title: fa ? 'وضعیت' : 'Status',
              dataIndex: 'status',
              render: (value: string) => statusTag(value, fa),
            },
            {
              title: fa ? 'رکورد' : 'Records',
              dataIndex: 'recordCount',
              render: (value: number) => Number(value ?? 0).toLocaleString(fa ? 'fa-IR' : 'en-US'),
            },
            {
              title: fa ? 'شروع' : 'Started',
              dataIndex: 'startedAt',
              render: (value?: string | null) =>
                value ? new Date(value).toLocaleString(fa ? 'fa-IR' : 'en-GB') : '—',
            },
            {
              title: fa ? 'خطا' : 'Error',
              dataIndex: 'errorMessage',
              render: (value?: string | null) => value || '—',
            },
          ]}
        />
      </Card>
    </Card>
  )
}