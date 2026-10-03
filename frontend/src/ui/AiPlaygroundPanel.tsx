import {
  Alert,
  Button,
  Card,
  Col,
  Descriptions,
  Form,
  Input,
  Row,
  Select,
  Space,
  Statistic,
  Tag,
  Typography,
} from 'antd'
import {
  ExperimentOutlined,
  PlayCircleOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../api/client'

type Agent = {
  type: string
  name: string
  provider?: string
  capabilities?: string[]
}

type PlaygroundResult = {
  agentCode: string
  provider: string
  model: string
  fallbackUsed: boolean
  latencyMs: number
  inputTokens: number
  outputTokens: number
  rawText?: string | null
  structuredData?: Record<string, unknown> | null
}

export function AiPlaygroundPanel() {
  const [result, setResult] = useState<PlaygroundResult | null>(null)

  const agents = useQuery({
    queryKey: ['ai-playground-agents'],
    queryFn: () => api<Agent[]>('/api/v1/admin/ai/agents'),
  })

  const run = useMutation({
    mutationFn: (values: {
      agentCode: string
      systemPrompt?: string
      input: string
    }) =>
      api<PlaygroundResult>('/api/v1/admin/ai/playground/run', {
        method: 'POST',
        body: JSON.stringify(values),
      }),
    onSuccess: (data) => setResult(data),
  })

  return (
    <div className="sakhtyar-dashboard-grid">
      <Alert
        showIcon
        type="info"
        message="آزمایشگاه از همان AI Gateway واقعی استفاده می‌کند"
        description="اجرای تست در ai_usage_event ثبت می‌شود؛ بنابراین نمودارهای مصرف، Latency و Fallback با داده واقعی به‌روزرسانی خواهند شد. از وارد کردن اطلاعات محرمانه واقعی در محیط تست خودداری کنید."
      />

      <Row gutter={[14, 14]}>
        <Col xs={24} xl={10}>
          <Card
            title={
              <Space>
                <ExperimentOutlined />
                <span>درخواست آزمایشی</span>
              </Space>
            }
            className="sakhtyar-playground-card"
          >
            <Form
              layout="vertical"
              initialValues={{ agentCode: 'PERSIAN' }}
              onFinish={(values) => run.mutate(values)}
            >
              <Form.Item
                name="agentCode"
                label="عامل"
                rules={[{ required: true }]}
              >
                <Select
                  showSearch
                  optionFilterProp="label"
                  options={(agents.data ?? []).map((agent) => ({
                    value: agent.type,
                    label: `${agent.name} (${agent.type})`,
                  }))}
                />
              </Form.Item>

              <Form.Item
                name="systemPrompt"
                label="System Prompt اختیاری"
                extra="اگر خالی باشد Prompt امن پیش‌فرض آزمایشگاه استفاده می‌شود."
              >
                <Input.TextArea rows={4} />
              </Form.Item>

              <Form.Item
                name="input"
                label="ورودی"
                rules={[
                  {
                    required: true,
                    message: 'یک ورودی آزمایشی وارد کنید.',
                  },
                ]}
              >
                <Input.TextArea
                  rows={8}
                  placeholder="مثال: از این متن نوع ملک و مساحت را به JSON استخراج کن..."
                />
              </Form.Item>

              <Button
                type="primary"
                htmlType="submit"
                icon={<PlayCircleOutlined />}
                loading={run.isPending}
                block
              >
                اجرای تست واقعی
              </Button>
            </Form>
          </Card>
        </Col>

        <Col xs={24} xl={14}>
          <Card
            title="نتیجه اجرا"
            className="sakhtyar-playground-card"
          >
            {run.isError ? (
              <Alert
                type="error"
                showIcon
                message="اجرای AI ناموفق بود"
                description={
                  run.error instanceof Error
                    ? run.error.message
                    : 'خطای ناشناخته'
                }
              />
            ) : null}

            {result ? (
              <div className="sakhtyar-dashboard-grid">
                <Row gutter={[10, 10]}>
                  <Col xs={12} md={6}>
                    <Card size="small">
                      <Statistic
                        title="Latency"
                        value={result.latencyMs}
                        suffix="ms"
                      />
                    </Card>
                  </Col>
                  <Col xs={12} md={6}>
                    <Card size="small">
                      <Statistic
                        title="Input Token"
                        value={result.inputTokens}
                      />
                    </Card>
                  </Col>
                  <Col xs={12} md={6}>
                    <Card size="small">
                      <Statistic
                        title="Output Token"
                        value={result.outputTokens}
                      />
                    </Card>
                  </Col>
                  <Col xs={12} md={6}>
                    <Card size="small">
                      <Statistic
                        title="Fallback"
                        value={result.fallbackUsed ? 'بله' : 'خیر'}
                      />
                    </Card>
                  </Col>
                </Row>

                <Descriptions column={{ xs: 1, md: 2 }} bordered size="small">
                  <Descriptions.Item label="عامل">
                    {result.agentCode}
                  </Descriptions.Item>
                  <Descriptions.Item label="ارائه‌دهنده">
                    {result.provider}
                  </Descriptions.Item>
                  <Descriptions.Item label="مدل">
                    {result.model}
                  </Descriptions.Item>
                  <Descriptions.Item label="مسیر">
                    <Tag color={result.fallbackUsed ? 'gold' : 'green'}>
                      {result.fallbackUsed ? 'Fallback' : 'Primary'}
                    </Tag>
                  </Descriptions.Item>
                </Descriptions>

                <div>
                  <Typography.Title level={5}>پاسخ متنی</Typography.Title>
                  <pre className="sakhtyar-playground-output">
                    {result.rawText || '—'}
                  </pre>
                </div>

                <div>
                  <Typography.Title level={5}>Structured Data</Typography.Title>
                  <pre className="sakhtyar-playground-output">
                    {JSON.stringify(result.structuredData ?? {}, null, 2)}
                  </pre>
                </div>
              </div>
            ) : (
              <div className="sakhtyar-playground-empty">
                <ExperimentOutlined />
                <strong>هنوز تستی اجرا نشده است</strong>
                <span>
                  یک عامل و ورودی انتخاب کنید تا Provider، Model، Latency،
                  Token و پاسخ واقعی نمایش داده شود.
                </span>
              </div>
            )}
          </Card>
        </Col>
      </Row>
    </div>
  )
}