import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Button, Card, Col, Descriptions, Divider, Input, Row, Select, Space, Statistic, Table, Tag, Typography } from 'antd'
import { api } from '../api/client'

type Workspace = { id: string; code: string; title: string; state: string; created_at: string }
type Scenario = { id: string; title: string; phase: string; objective: string }
type RunRow = { id: string; workspace_id: string; state: string; requested_at: string; scenario_count: number }
type ResultRow = { scenario_id: string; phase: string; title: string; state: string; summary: string }
type Run = { id: string; state: string; workspace_id: string; gate_reason: string; results: ResultRow[] }
type Readiness = { migration58Installed: boolean; taggedBusinessTables: number; rlsAndCrossScopeFkEnforcementVerified: boolean; outboundGuardVerified: boolean; allBusinessQueriesScopeGuarded: boolean; automatedTestBusinessWritesPermitted: boolean; gateReason: string }
type Evidence = { rowsByTable: Record<string, number>; workspaceId: string; warning: string }

type Capability = { businessDataWriteEnabled: boolean; isolationVerified: boolean; sessionProfileSwitchEnabled: boolean; phaseStatus: Array<{ phase: string; title: string; status: string }> }
const stageNames: Record<string,string> = {
  T01: 'زیرساخت تفکیک داده', T02: 'کاتالوگ ۲۴ سناریو', T03: 'شواهد و گزارش', T04: 'قابلیت‌های پیشرفته',
}
function safeMessage(error: unknown) { return error instanceof Error ? error.message : 'خطا در ارتباط با سرور' }

export function SimulationCenterPage() {
  const q = useQueryClient()
  const [code, setCode] = useState('')
  const [title, setTitle] = useState('')
  const [workspace, setWorkspace] = useState<string>()
  const [runId, setRunId] = useState<string>()
  const [selectedIds, setSelectedIds] = useState<string[]>([])
  const capabilities = useQuery({ queryKey: ['sim-capabilities'], queryFn: () => api<Capability>('/api/v1/admin/simulations/capabilities') })
  const readiness = useQuery({ queryKey: ['sim-readiness'], queryFn: () => api<Readiness>('/api/v1/admin/simulations/readiness') })
  const workspaces = useQuery({ queryKey: ['sim-workspaces'], queryFn: () => api<Workspace[]>('/api/v1/admin/simulations/workspaces') })
  const catalog = useQuery({ queryKey: ['sim-catalog'], queryFn: () => api<Scenario[]>('/api/v1/admin/simulations/catalog') })
  const runs = useQuery({ queryKey: ['sim-runs'], queryFn: () => api<RunRow[]>('/api/v1/admin/simulations/runs') })
  const evidence = useQuery({ queryKey: ['sim-evidence', workspace], enabled: Boolean(workspace), queryFn: () => api<Evidence>(`/api/v1/admin/simulations/workspaces/${workspace}/evidence`) })
  const current = useQuery({ queryKey: ['sim-run', runId], enabled: Boolean(runId), queryFn: () => api<Run>(`/api/v1/admin/simulations/runs/${runId}`) })
  const create = useMutation({
    mutationFn: () => api<Workspace>('/api/v1/admin/simulations/workspaces', { method: 'POST', body: JSON.stringify({ code, title }) }),
    onSuccess: v => { setWorkspace(v.id); setCode(''); setTitle(''); void q.invalidateQueries({ queryKey: ['sim-workspaces'] }) },
  })
  const preflight = useMutation({
    mutationFn: () => api<Run>('/api/v1/admin/simulations/runs', {
      method: 'POST',
      body: JSON.stringify({ workspaceId: workspace, requestKey: crypto.randomUUID().replaceAll('-', ''), scenarioIds: selectedIds }),
    }),
    onSuccess: v => { setRunId(v.id); void q.invalidateQueries({ queryKey: ['sim-runs'] }) },
  })
  const csv = catalog.data ?? []
  const selected = selectedIds.length ? selectedIds : csv.map(s => s.id)
  return <div dir="rtl" style={{ maxWidth: 1400, margin: '0 auto', padding: '14px 0' }}>
    <Space direction="vertical" style={{ width: '100%' }} size="large">
      <Card>
        <Typography.Title level={3} style={{ marginTop: 0 }}>مرکز شبیه‌سازی و داده‌های آزمایشی ساخت‌یار</Typography.Title>
        <Typography.Paragraph type="secondary">مدیریت فضای داده آزمایشی، تعریف ۲۴ سناریوی انتهابه‌انتها، بررسی شواهد دیتابیس و وضعیت گیت امنیتی</Typography.Paragraph>
        <Alert type="warning" showIcon message="اجرای واقعی گردش‌کارهای دارای نوشتن هنوز غیرفعال است" description="این بسته زیرساخت و ثبت کنترل‌شده سناریوها را پیاده می‌کند. تا پایان پیاده‌سازی و تأیید RLS، قیود ارتباطی و محافظ عملیات خارجی، اجرای سناریوهای تجاری با وضعیت مسدود ثبت می‌شود؛ اطلاعات REAL تغییر نمی‌کند." />
        {capabilities.isError && <Alert type="error" message={safeMessage(capabilities.error)} />}
      </Card>
      <Row gutter={[14, 14]}>
        <Col xs={12} md={6}><Card><Statistic title="سناریوهای ثبت‌شده" value={csv.length} /></Card></Col>
        <Col xs={12} md={6}><Card><Statistic title="فضاهای آزمایشی" value={workspaces.data?.length ?? 0} /></Card></Col>
        <Col xs={12} md={6}><Card><Statistic title="اجراهای ثبت‌شده" value={runs.data?.length ?? 0} /></Card></Col>
        <Col xs={12} md={6}><Card><Statistic title="اجرای واقعی فعال" value={0} suffix={<Tag color="red">خیر</Tag>} /></Card></Col>
      </Row>
      <Card title="فازهای بسته T01–T04">
        <Row gutter={[12,12]}>{(capabilities.data?.phaseStatus ?? []).map(item => <Col xs={24} sm={12} lg={6} key={item.phase}>
          <Card size="small" style={{ height: '100%' }}>
            <Space direction="vertical">
              <Tag>{item.phase}</Tag><Typography.Text strong>{stageNames[item.phase] ?? item.title}</Typography.Text>
              <Tag color={item.status === 'PLANNED' ? 'orange' : 'blue'}>{item.status === 'PLANNED' ? 'نیازمند تکمیل' : 'زیرساخت آماده'}</Tag>
            </Space>
          </Card>
        </Col>)}</Row>
      </Card>
      <Card title="فضای آزمایشی">
        <Space wrap style={{ width: '100%' }}>
          <Select style={{ minWidth: 300 }} placeholder="انتخاب فضای آزمایشی" value={workspace} onChange={setWorkspace} options={(workspaces.data ?? []).map(w => ({ label: `${w.title} (${w.code})`, value: w.id }))} />
          <Input placeholder="کد لاتین یکتا" style={{ width: 170 }} value={code} onChange={e => setCode(e.target.value)} maxLength={80} />
          <Input placeholder="عنوان فضای آزمایشی" style={{ width: 220 }} value={title} onChange={e => setTitle(e.target.value)} maxLength={250} />
          <Button type="primary" loading={create.isPending} disabled={!/^[a-zA-Z0-9_-]{3,80}$/.test(code) || !title.trim()} onClick={() => create.mutate()}>ایجاد فضا</Button>
        </Space>
        {create.isError && <Alert style={{ marginTop: 12 }} type="error" message={safeMessage(create.error)} showIcon />}
      </Card>
      <Card title="کاتالوگ سناریوهای کسب‌وکار">
        <Space wrap style={{ marginBottom: 12 }}>
          <Button onClick={() => setSelectedIds(csv.map(s => s.id))}>انتخاب همه</Button>
          <Button onClick={() => setSelectedIds([])}>حذف انتخاب (اجرای همه)</Button>
          <Button type="primary" disabled={!workspace || !csv.length} loading={preflight.isPending} onClick={() => preflight.mutate()}>ثبت بررسی ایمن {selected.length} سناریو</Button>
        </Space>
        <Table rowKey="id" pagination={{ pageSize: 8 }} dataSource={csv} size="small" rowSelection={{ selectedRowKeys: selectedIds, onChange: keys => setSelectedIds(keys.map(String)) }} columns={[
          { title: 'شناسه', dataIndex: 'id', width: 110 },
          { title: 'فاز', dataIndex: 'phase', width: 85 },
          { title: 'موضوع', dataIndex: 'title' },
          { title: 'نوع اجرا', render: () => <Tag color="orange">مسدود تا تأیید امنیت</Tag> },
        ]} />
        {preflight.isError && <Alert type="error" message={safeMessage(preflight.error)} showIcon />}
      </Card>
      <Card title="نتیجه آخرین بررسی و شواهد">
        {runId && <Space direction="vertical" style={{ width: '100%' }}>
          <Descriptions bordered size="small" column={1} items={[
            {key:'id',label:'شناسه اجرا',children:runId},
            {key:'state',label:'وضعیت',children:<Tag color="orange">{current.data?.state ?? 'در حال بارگذاری'}</Tag>},
            {key:'gate',label:'دلیل عدم اجرا',children:current.data?.gate_reason ?? '—'},
          ]} />
          <Button onClick={() => { const id = runId; window.open(`/api/v1/admin/simulations/runs/${id}/report`, '_blank', 'noopener,noreferrer') }}>نمایش گزارش JSON</Button>
          <Table rowKey="scenario_id" size="small" pagination={{ pageSize: 8 }} dataSource={current.data?.results ?? []} columns={[
            {title:'شناسه',dataIndex:'scenario_id'}, {title:'سناریو',dataIndex:'title'},
            {title:'نتیجه',dataIndex:'state',render:(value:string)=><Tag color="orange">{value}</Tag>},
          ]} />
        </Space>}
        {!runId && <Typography.Text type="secondary">پس از ثبت یک بررسی، نتایج و دلایل مسدودشدن سناریوها در این قسمت دیده می‌شود.</Typography.Text>}
        <Divider />
        <Typography.Text strong>شواهد رکوردهای TEST در PostgreSQL</Typography.Text>
        <Table rowKey="table" pagination={false} size="small" dataSource={Object.entries(evidence.data?.rowsByTable ?? {}).map(([table,count])=>({table,count}))} columns={[
          {title:'جدول',dataIndex:'table'}, {title:'تعداد رکورد TEST',dataIndex:'count'},
        ]} />
      </Card>
      <Card title="گیت‌های امنیت و آمادگی واقعی">
        <Space direction="vertical" style={{ width:'100%' }}>
          {readiness.data && <>
            <Tag color={readiness.data.migration58Installed ? 'blue' : 'red'}>ساختار V58: {readiness.data.migration58Installed ? 'ایجاد شده' : 'نیازمند نصب'}</Tag>
            <Tag color="orange">کنترل RLS و ارتباط‌های متقاطع: نیازمند اجرا و تأیید</Tag>
            <Tag color="orange">محافظ عملیات خارجی: نیازمند اجرا و تأیید</Tag>
            <Tag color="red">اجرای سناریوهای دارای نوشتن: غیرفعال</Tag>
          </>}
          <Typography.Paragraph type="secondary">حالت واقعیِ سایر صفحات همچنان بدون تغییر باقی می‌ماند؛ انتخاب حالت TEST برای کل برنامه صرفاً پس از عبور از گیت جداسازی فعال می‌شود.</Typography.Paragraph>
        </Space>
      </Card>
    </Space>
  </div>
}
