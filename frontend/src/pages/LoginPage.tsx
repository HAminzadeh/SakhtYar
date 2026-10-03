import { ArrowLeftOutlined, LockOutlined, UserAddOutlined, UserOutlined } from '@ant-design/icons'
import { Alert, Button, Card, Form, Input, Space, Typography } from 'antd'
import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthProvider'
import { useI18n } from '../i18n/LanguageProvider'
import { LanguageSwitcher } from '../ui/LanguageSwitcher'

type LoginValues = { username: string; password: string }

export function LoginPage() {
  const { user, login } = useAuth()
  const { t } = useI18n()
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  if (user) return <Navigate to="/cases" replace />

  const submit = async (values: LoginValues) => {
    setError(null); setSubmitting(true)
    try {
      await login(values.username.trim(), values.password)
      navigate('/cases', { replace: true })
    } catch (err) {
      setError(err instanceof Error ? err.message : t('invalidLogin'))
    } finally { setSubmitting(false) }
  }

  return (
    <div className="sakhtyar-login-v56">
      <section className="sakhtyar-login-v56__hero">
        <div className="sakhtyar-login-v56__shade" />
        <div className="sakhtyar-login-v56__brand">
          <div className="sakhtyar-login-v56__brandline">
            <img src="/assets/sakhtyar/brand/sakhtyar-mark.svg" alt="" className="sakhtyar-login-v56__mark" />
            <div>
              <div className="sakhtyar-login-v56__wordmark">{t('appName')}</div>
              <div className="sakhtyar-login-v56__subbrand">{t('appSubtitle')}</div>
            </div>
          </div>
        </div>
      </section>
      <section className="sakhtyar-login-v56__panel">
        <Card className="sakhtyar-login-v56__card" bordered={false}>
          <Space style={{ width: '100%', justifyContent: 'flex-end', marginBottom: 12 }}>
            <LanguageSwitcher />
          </Space>
          <div className="sakhtyar-login-v56__cardhead">
            <img src="/assets/sakhtyar/brand/sakhtyar-mark.svg" alt="" className="sakhtyar-login-v56__cardmark" />
            <div className="sakhtyar-login-v56__cardword">{t('appName')}</div>
            <Typography.Title level={2}>{t('loginTitle')}</Typography.Title>
            <Typography.Text type="secondary">{t('loginHint')}</Typography.Text>
          </div>
          {error ? <Alert type="error" showIcon message={error} style={{ marginBottom: 14 }} /> : null}
          <Form<LoginValues> layout="vertical" onFinish={submit} requiredMark={false} autoComplete="on" initialValues={{ username: 'admin' }}>
            <Form.Item label={t('username')} name="username" rules={[{ required: true, whitespace: true }]}>
              <Input size="large" prefix={<UserOutlined />} autoComplete="username" disabled={submitting} placeholder={t('username')} />
            </Form.Item>
            <Form.Item label={t('password')} name="password" rules={[{ required: true }]}>
              <Input.Password size="large" prefix={<LockOutlined />} autoComplete="current-password" disabled={submitting} placeholder={t('password')} />
            </Form.Item>
            <Button type="primary" htmlType="submit" size="large" block loading={submitting} icon={<ArrowLeftOutlined />}>
              {t('login')}
            </Button>
            <Link to="/register">
              <Button size="large" block icon={<UserAddOutlined />} style={{ marginTop: 12 }}>{t('register')}</Button>
            </Link>
          </Form>
        </Card>
      </section>
    </div>
  )
}