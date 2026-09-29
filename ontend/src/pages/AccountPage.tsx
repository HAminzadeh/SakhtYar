import {
  DesktopOutlined,
  KeyOutlined,
  LockOutlined,
  LogoutOutlined,
  MailOutlined,
  MobileOutlined,
  SafetyCertificateOutlined,
  SaveOutlined,
  UserOutlined,
} from '@ant-design/icons'
import {
  Alert,
  Avatar,
  Button,
  Card,
  Col,
  Input,
  List,
  Row,
  Space,
  Tag,
} from 'antd'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { api } from '../api/client'
import type { AuthSessionItem, Me } from '../api/types'
import { useAuth } from '../auth/AuthProvider'
import { roleLabel, safeDisplayName, userStatusLabel } from '../ui/presentation'
import { PageHero } from '../ui/PageHero'

export function AccountPage() {
  const { user, refresh } = useAuth()
  const queryClient = useQueryClient()
  const fallbackName = roleLabel(user?.role) || user?.username || 'کاربر ساخت‌یار'
  const [displayName, setDisplayName] = useState(safeDisplayName(user?.displayName, fallbackName))
  const [email, setEmail] = useState(user?.email ?? '')
  const [mobile, setMobile] = useState(user?.mobile ?? '')
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')

  useEffect(() => {
    setDisplayName(safeDisplayName(user?.displayName, fallbackName))
    setEmail(user?.email ?? '')
    setMobile(user?.mobile ?? '')
  }, [user, fallbackName])

  const sessions = useQuery({ queryKey: ['auth-sessions'], queryFn: () => api<AuthSessionItem[]>('/api/v1/auth/sessions') })
  const updateProfile = useMutation({
    mutationFn: () => api<Me>('/api/v1/auth/profile', { method: 'PUT', body: JSON.stringify({ displayName, email: email.trim() || null, mobile: mobile.trim() || null }) }),
    onSuccess: () => refresh(),
  })
  const changePassword = useMutation({
    mutationFn: () => api<void>('/api/v1/auth/change-password', { method: 'POST', body: JSON.stringify({ currentPassword, newPassword }) }),
    onSuccess: () => window.location.assign('/login'),
  })
  const revokeSession = useMutation({
    mutationFn: (id: string) => api<void>(`/api/v1/auth/sessions/${id}`, { method: 'DELETE' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['auth-sessions'] }),
  })
  const revokeAll = useMutation({
    mutationFn: () => api<void>('/api/v1/auth/sessions', { method: 'DELETE' }),
    onSuccess: () => window.location.assign('/login'),
  })

  return (
    <div className="sakhtyar-page-stack">
      <PageHero
        image="/assets/sakhtyar/heroes/account-hero.jpg"
        title="حساب کاربری و امنیت"
        subtitle="پروفایل، رمز عبور و دستگاه‌های متصل"
        icon={<SafetyCertificateOutlined />}
      />

      <Row gutter={[20, 20]}>
        <Col xs={24} xl={12}>
          <Card className="sakhtyar-account-card" title={<Space><UserOutlined />پروفایل</Space>}>
            <div className="sakhtyar-account-card-grid">
              <div className="sakhtyar-account-avatar-column">
                <Avatar size={104} className="sakhtyar-account-avatar" icon={<UserOutlined />} />
                <Tag color="gold">{roleLabel(user?.role)}</Tag>
                <Tag color="green">{userStatusLabel(user?.status)}</Tag>
              </div>
              <div className="sakhtyar-account-fields">
                {updateProfile.isSuccess ? <Alert type="success" showIcon message="پروفایل ذخیره شد." /> : null}
                {updateProfile.isError ? <Alert type="error" showIcon message={updateProfile.error instanceof Error ? updateProfile.error.message : 'ذخیره پروفایل ناموفق بود.'} /> : null}
                <label className="sakhtyar-field-caption">نام نمایشی</label>
                <Input size="large" prefix={<UserOutlined />} value={displayName} onChange={(e) => setDisplayName(e.target.value)} />
                <label className="sakhtyar-field-caption">نام کاربری</label>
                <Input size="large" prefix={<UserOutlined />} value={user?.username ?? ''} disabled />
                <label className="sakhtyar-field-caption">موبایل</label>
                <Input size="large" prefix={<MobileOutlined />} value={mobile} onChange={(e) => setMobile(e.target.value)} />
                <label className="sakhtyar-field-caption">ایمیل</label>
                <Input size="large" prefix={<MailOutlined />} value={email} onChange={(e) => setEmail(e.target.value)} />
              </div>
            </div>
            <Button type="primary" size="large" block className="sakhtyar-primary-action" icon={<SaveOutlined />} disabled={!displayName.trim() || updateProfile.isPending} loading={updateProfile.isPending} onClick={() => updateProfile.mutate()}>
              ذخیره پروفایل
            </Button>
          </Card>
        </Col>

        <Col xs={24} xl={12}>
          <Card className="sakhtyar-account-card" title={<Space><KeyOutlined />تغییر رمز عبور</Space>}>
            {changePassword.isError ? <Alert type="error" showIcon message={changePassword.error instanceof Error ? changePassword.error.message : 'تغییر رمز عبور ناموفق بود.'} style={{ marginBottom: 16 }} /> : null}
            <Space direction="vertical" size={14} style={{ width: '100%' }}>
              <div><label className="sakhtyar-field-caption">رمز فعلی</label><Input.Password size="large" prefix={<LockOutlined />} placeholder="رمز فعلی خود را وارد کنید" value={currentPassword} onChange={(e) => setCurrentPassword(e.target.value)} /></div>
              <div><label className="sakhtyar-field-caption">رمز جدید</label><Input.Password size="large" prefix={<LockOutlined />} placeholder="رمز جدید را وارد کنید" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} /></div>
              <div><label className="sakhtyar-field-caption">تکرار رمز جدید</label><Input.Password size="large" prefix={<LockOutlined />} placeholder="رمز جدید را مجدداً وارد کنید" status={confirmPassword && confirmPassword !== newPassword ? 'error' : undefined} value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} /></div>
              <Button size="large" block icon={<KeyOutlined />} disabled={!currentPassword || newPassword.length < 10 || newPassword !== confirmPassword || changePassword.isPending} loading={changePassword.isPending} onClick={() => changePassword.mutate()}>
                تغییر رمز عبور
              </Button>
            </Space>
          </Card>
        </Col>
      </Row>

      <Card
        title={<Space><DesktopOutlined />نشست‌ها و دستگاه‌ها</Space>}
        extra={<Button danger icon={<LogoutOutlined />} disabled={revokeAll.isPending} onClick={() => revokeAll.mutate()}>خروج از همه دستگاه‌ها</Button>}
        className="sakhtyar-account-card"
      >
        <List
          loading={sessions.isLoading}
          dataSource={sessions.data ?? []}
          locale={{ emptyText: 'نشستی ثبت نشده است.' }}
          renderItem={(session) => (
            <List.Item actions={session.active ? [<Button key="revoke" danger onClick={() => revokeSession.mutate(session.id)}>پایان نشست</Button>] : undefined}>
              <List.Item.Meta
                avatar={<Avatar icon={<DesktopOutlined />} className="sakhtyar-session-avatar" />}
                title={<Space wrap><strong>{session.deviceName || (session.clientType === 'MOBILE' ? 'موبایل' : 'مرورگر وب')}</strong><Tag color={session.active ? 'green' : 'default'}>{session.active ? 'فعال' : 'پایان‌یافته'}</Tag></Space>}
                description={`${session.ipAddress || 'IP نامشخص'} · آخرین استفاده: ${new Date(session.lastUsedAt).toLocaleString('fa-IR')}`}
              />
            </List.Item>
          )}
        />
      </Card>
    </div>
  )
}
