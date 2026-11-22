import { HowToRegRoundedIcon } from '../ui/antdIcons'
import { Alert, Button, Card, Col, Form, Input, Row, Space, Typography } from 'antd'
import { useState } from 'react'
import { Link, Navigate } from 'react-router-dom'
import { api } from '../api/client'
import type { RegistrationResponse } from '../api/types'
import { useAuth } from '../auth/AuthProvider'

type RegisterValues = {
  username: string
  displayName: string
  email?: string
  mobile?: string
  password: string
  confirmPassword: string
}

export function RegisterPage() {
  const { user } = useAuth()
  const [result, setResult] = useState<RegistrationResponse | null>(null)
  const [serverError, setServerError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (user) return <Navigate to="/cases" replace />

  const submit = async (values: RegisterValues) => {
    setServerError(null)
    setSubmitting(true)

    try {
      setResult(
        await api<RegistrationResponse>('/api/v1/auth/register', {
          method: 'POST',
          body: JSON.stringify({
            username: values.username.trim(),
            displayName: values.displayName.trim(),
            email: values.email?.trim() || null,
            mobile: values.mobile?.trim() || null,
            password: values.password,
          }),
        }),
      )
    } catch (error) {
      setServerError(
        error instanceof Error ? error.message : 'ثبت‌نام ناموفق بود.',
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'grid',
        placeItems: 'center',
        padding: 24,
        background: '#F6F8FC',
      }}
    >
      <Card style={{ width: '100%', maxWidth: 560, borderRadius: 16 }}>
        <Space direction="vertical" size={24} style={{ width: '100%' }}>
          <Space align="center" size={12}>
            <div
              style={{
                width: 48,
                height: 48,
                borderRadius: 12,
                display: 'grid',
                placeItems: 'center',
                background: '#1677ff',
                color: '#fff',
              }}
            >
              <HowToRegRoundedIcon />
            </div>
            <div>
              <Typography.Title level={3} style={{ margin: 0 }}>
                ثبت‌نام در ساخت‌یار
              </Typography.Title>
              <Typography.Text type="secondary">
                حساب جدید بعد از تأیید مدیر سیستم فعال می‌شود.
              </Typography.Text>
            </div>
          </Space>

          {serverError ? <Alert type="error" showIcon message={serverError} /> : null}

          {result ? (
            <Space direction="vertical" size={16} style={{ width: '100%' }}>
              <Alert type="success" showIcon message={result.message} />
              <Link to="/login">
                <Button type="primary" block>
                  رفتن به صفحه ورود
                </Button>
              </Link>
            </Space>
          ) : (
            <Form<RegisterValues>
              layout="vertical"
              onFinish={submit}
              requiredMark={false}
              autoComplete="on"
            >
              <Form.Item
                label="نام کاربری"
                name="username"
                extra="بدون فاصله؛ حروف، عدد، نقطه، خط تیره و زیرخط مجاز است."
                rules={[
                  { required: true, message: 'نام کاربری الزامی است' },
                  { min: 3, message: 'حداقل ۳ کاراکتر' },
                  { max: 100, message: 'حداکثر ۱۰۰ کاراکتر' },
                ]}
              >
                <Input size="large" autoComplete="username" />
              </Form.Item>

              <Form.Item
                label="نام نمایشی"
                name="displayName"
                rules={[
                  { required: true, message: 'نام نمایشی الزامی است' },
                  { min: 2, message: 'حداقل ۲ کاراکتر' },
                ]}
              >
                <Input size="large" />
              </Form.Item>

              <Row gutter={16}>
                <Col xs={24} sm={12}>
                  <Form.Item
                    label="ایمیل"
                    name="email"
                    rules={[{ type: 'email', message: 'ایمیل معتبر نیست' }]}
                  >
                    <Input size="large" autoComplete="email" />
                  </Form.Item>
                </Col>

                <Col xs={24} sm={12}>
                  <Form.Item label="موبایل" name="mobile">
                    <Input size="large" autoComplete="tel" />
                  </Form.Item>
                </Col>
              </Row>

              <Form.Item
                label="رمز عبور"
                name="password"
                extra="حداقل ۱۰ کاراکتر و شامل حرف، عدد و نویسه خاص"
                rules={[
                  { required: true, message: 'رمز عبور الزامی است' },
                  { min: 10, message: 'حداقل ۱۰ کاراکتر' },
                ]}
              >
                <Input.Password size="large" autoComplete="new-password" />
              </Form.Item>

              <Form.Item
                label="تکرار رمز عبور"
                name="confirmPassword"
                dependencies={['password']}
                rules={[
                  { required: true, message: 'تکرار رمز عبور الزامی است' },
                  ({ getFieldValue }) => ({
                    validator(_, value) {
                      if (!value || getFieldValue('password') === value) {
                        return Promise.resolve()
                      }
                      return Promise.reject(new Error('تکرار رمز عبور یکسان نیست'))
                    },
                  }),
                ]}
              >
                <Input.Password size="large" autoComplete="new-password" />
              </Form.Item>

              <Form.Item style={{ marginBottom: 12 }}>
                <Button
                  type="primary"
                  htmlType="submit"
                  size="large"
                  block
                  loading={submitting}
                >
                  ثبت درخواست عضویت
                </Button>
              </Form.Item>

              <Link to="/login">
                <Button htmlType="button">قبلاً حساب دارید؟ ورود</Button>
              </Link>
            </Form>
          )}
        </Space>
      </Card>
    </div>
  )
}
