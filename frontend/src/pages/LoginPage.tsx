import {
  ApartmentOutlined,
  ArrowLeftOutlined,
  LockOutlined,
  UserAddOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { Alert, Button, Card, Form, Input, Space, Typography } from 'antd'
import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthProvider'

type LoginValues = {
  username: string
  password: string
}

export function LoginPage() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (user) return <Navigate to="/cases" replace />

  const submit = async (values: LoginValues) => {
    setError(null)
    setSubmitting(true)

    try {
      await login(values.username.trim(), values.password)
      navigate('/cases', { replace: true })
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : 'نام کاربری یا رمز عبور صحیح نیست.',
      )
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="sakhtyar-login">
      <section className="sakhtyar-login-scene">
        <div className="sakhtyar-login-scene-shade" />
        <div className="sakhtyar-login-message">
          <div className="sakhtyar-login-wordmark">ساخت‌یار</div>
          <div className="sakhtyar-login-tagline">
            سامانه هوشمند مشارکت در ساخت
          </div>
          <div className="sakhtyar-login-accent" />
          <p>
            مدیریت حرفه‌ای پروژه‌های ساختمانی، از ارزیابی ملک و توافق با مالکین
            تا قرارداد و اجرای پروژه، در یک پلتفرم یکپارچه.
          </p>
        </div>
      </section>

      <section className="sakhtyar-login-form-side">
        <Card className="sakhtyar-login-card" bordered={false}>
          <Space direction="vertical" size={26} style={{ width: '100%' }}>
            <div className="sakhtyar-login-card-brand">
              <span className="sakhtyar-login-logo">
                <ApartmentOutlined />
              </span>
              <Typography.Title level={2}>ساخت‌یار</Typography.Title>
              <Typography.Text type="secondary">
                سامانه هوشمند مشارکت در ساخت
              </Typography.Text>
            </div>

            {error ? <Alert type="error" showIcon message={error} /> : null}

            <Form<LoginValues>
              layout="vertical"
              onFinish={submit}
              requiredMark={false}
              autoComplete="on"
              initialValues={{ username: 'admin' }}
            >
              <Form.Item
                label="نام کاربری"
                name="username"
                rules={[
                  {
                    required: true,
                    whitespace: true,
                    message: 'نام کاربری الزامی است',
                  },
                ]}
              >
                <Input
                  size="large"
                  prefix={<UserOutlined />}
                  autoComplete="username"
                  disabled={submitting}
                />
              </Form.Item>

              <Form.Item
                label="رمز عبور"
                name="password"
                rules={[
                  {
                    required: true,
                    message: 'رمز عبور الزامی است',
                  },
                ]}
              >
                <Input.Password
                  size="large"
                  prefix={<LockOutlined />}
                  autoComplete="current-password"
                  disabled={submitting}
                />
              </Form.Item>

              <Form.Item style={{ marginBottom: 12 }}>
                <Button
                  type="primary"
                  htmlType="submit"
                  size="large"
                  block
                  loading={submitting}
                  icon={<ArrowLeftOutlined />}
                >
                  ورود به ساخت‌یار
                </Button>
              </Form.Item>

              <Link to="/register">
                <Button
                  size="large"
                  block
                  icon={<UserAddOutlined />}
                >
                  ایجاد حساب جدید
                </Button>
              </Link>
            </Form>
          </Space>
        </Card>
      </section>
    </div>
  )
}
