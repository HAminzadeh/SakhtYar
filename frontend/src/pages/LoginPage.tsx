import {
  ArrowLeftOutlined,
  LockOutlined,
  UserAddOutlined,
  UserOutlined,
} from '@ant-design/icons'
import {
  Alert,
  Button,
  Card,
  Form,
  Input,
  Typography,
} from 'antd'
import { useState } from 'react'
import {
  Link,
  Navigate,
  useNavigate,
} from 'react-router-dom'
import { useAuth } from '../auth/AuthProvider'

type LoginValues = {
  username: string
  password: string
}

export function LoginPage() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const [error, setError] =
    useState<string | null>(null)
  const [submitting, setSubmitting] =
    useState(false)

  if (user) return <Navigate to="/cases" replace />

  const submit = async (
    values: LoginValues,
  ) => {
    setError(null)
    setSubmitting(true)

    try {
      await login(
        values.username.trim(),
        values.password,
      )
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
    <div className="sakhtyar-login-v56">
      <section className="sakhtyar-login-v56__hero">
        <div className="sakhtyar-login-v56__shade" />

        <div className="sakhtyar-login-v56__brand">
          <div className="sakhtyar-login-v56__brandline">
            <img
              src="/assets/sakhtyar/brand/sakhtyar-mark.svg"
              alt=""
              className="sakhtyar-login-v56__mark"
            />
            <div>
              <div className="sakhtyar-login-v56__wordmark">
                ساخت‌یار
              </div>
              <div className="sakhtyar-login-v56__subbrand">
                سامانه هوشمند مشارکت در ساخت
              </div>
            </div>
          </div>

          <Typography.Title>
            از ارزیابی ملک تا ساخت،
            <br />
            همه‌چیز در یک مسیر روشن
          </Typography.Title>

          <Typography.Paragraph>
            پرونده، مالکین، مدارک، نقشه، تصاویر
            پروژه و دستیار هوشمند را در یک محیط
            یکپارچه و حرفه‌ای مدیریت کنید.
          </Typography.Paragraph>

          <div className="sakhtyar-login-v56__features">
            <span>پرونده‌های مشارکت</span>
            <span>گالری مراحل پروژه</span>
            <span>نقشه و اطلاعات ملک</span>
            <span>کنترل دسترسی‌ها</span>
          </div>
        </div>
      </section>

      <section className="sakhtyar-login-v56__panel">
        <Card
          className="sakhtyar-login-v56__card"
          bordered={false}
        >
          <div className="sakhtyar-login-v56__cardhead">
            <img
              src="/assets/sakhtyar/brand/sakhtyar-mark.svg"
              alt=""
              className="sakhtyar-login-v56__cardmark"
            />
            <div className="sakhtyar-login-v56__cardword">
              ساخت‌یار
            </div>
            <Typography.Title level={2}>
              خوش آمدید
            </Typography.Title>
            <Typography.Text type="secondary">
              برای ورود، اطلاعات حساب خود را وارد
              کنید.
            </Typography.Text>
          </div>

          {error ? (
            <Alert
              type="error"
              showIcon
              message={error}
              style={{ marginBottom: 14 }}
            />
          ) : null}

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
                placeholder="نام کاربری"
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
                placeholder="رمز عبور"
              />
            </Form.Item>

            <Button
              type="primary"
              htmlType="submit"
              size="large"
              block
              loading={submitting}
              icon={<ArrowLeftOutlined />}
              className="sakhtyar-login-v56__submit sakhtyar-animated-primary"
            >
              ورود به ساخت‌یار
            </Button>

            <div className="sakhtyar-login-v56__divider">
              <span>یا</span>
            </div>

            <Link to="/register">
              <Button
                size="large"
                block
                icon={<UserAddOutlined />}
                className="sakhtyar-login-v56__register"
              >
                ایجاد حساب جدید
              </Button>
            </Link>
          </Form>
        </Card>
      </section>
    </div>
  )
}
