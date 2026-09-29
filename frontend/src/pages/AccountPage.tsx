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
  Typography,
} from 'antd'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { api } from '../api/client'
import type { AuthSessionItem, Me } from '../api/types'
import { useAuth } from '../auth/AuthProvider'
import { PageHero } from '../ui/PageHero'
import {
  roleLabel,
  safeDisplayName,
  userStatusLabel,
} from '../ui/presentation'

export function AccountPage() {
  const { user, refresh } = useAuth()
  const queryClient = useQueryClient()
  const [displayName, setDisplayName] = useState(
    safeDisplayName(
      user?.displayName,
      roleLabel(user?.role) || user?.username || 'کاربر ساخت‌یار',
    ),
  )
  const [email, setEmail] = useState(user?.email ?? '')
  const [mobile, setMobile] = useState(user?.mobile ?? '')
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')

  useEffect(() => {
    setDisplayName(
      safeDisplayName(
        user?.displayName,
        roleLabel(user?.role) || user?.username || 'کاربر ساخت‌یار',
      ),
    )
    setEmail(user?.email ?? '')
    setMobile(user?.mobile ?? '')
  }, [user])

  const sessions = useQuery({
    queryKey: ['auth-sessions'],
    queryFn: () => api<AuthSessionItem[]>('/api/v1/auth/sessions'),
  })

  const updateProfile = useMutation({
    mutationFn: () =>
      api<Me>('/api/v1/auth/profile', {
        method: 'PUT',
        body: JSON.stringify({
          displayName,
          email: email.trim() || null,
          mobile: mobile.trim() || null,
        }),
      }),
    onSuccess: () => refresh(),
  })

  const changePassword = useMutation({
    mutationFn: () =>
      api<void>('/api/v1/auth/change-password', {
        method: 'POST',
        body: JSON.stringify({
          currentPassword,
          newPassword,
        }),
      }),
    onSuccess: () => window.location.assign('/login'),
  })

  const revokeSession = useMutation({
    mutationFn: (id: string) =>
      api<void>(`/api/v1/auth/sessions/${id}`, {
        method: 'DELETE',
      }),
    onSuccess: () =>
      queryClient.invalidateQueries({
        queryKey: ['auth-sessions'],
      }),
  })

  const revokeAll = useMutation({
    mutationFn: () =>
      api<void>('/api/v1/auth/sessions', {
        method: 'DELETE',
      }),
    onSuccess: () => window.location.assign('/login'),
  })

  const shownName = safeDisplayName(
    user?.displayName,
    roleLabel(user?.role) || user?.username || 'کاربر ساخت‌یار',
  )

  return (
    <div className="sakhtyar-page-stack sakhtyar-account-page-v54">
      <PageHero
        image="/assets/sakhtyar/heroes/account-hero.jpg"
        title="حساب کاربری و امنیت"
        subtitle="پروفایل، رمز عبور و نشست‌های فعال حساب شما"
        icon={<SafetyCertificateOutlined />}
      />

      <div className="sakhtyar-account-identity-strip">
        <Space size={12}>
          <Avatar
            size={44}
            className="sakhtyar-account-avatar"
            icon={<UserOutlined />}
          />

          <div>
            <strong>{shownName}</strong>
            <div>@{user?.username}</div>
          </div>
        </Space>

        <Space wrap>
          <Tag
            color="blue"
            icon={<SafetyCertificateOutlined />}
          >
            {roleLabel(user?.role)}
          </Tag>
          <Tag color="green">
            {userStatusLabel(user?.status)}
          </Tag>
        </Space>
      </div>

      <Row gutter={[14, 14]}>
        <Col xs={24} xl={14}>
          <Card
            title={
              <Space>
                <UserOutlined />
                پروفایل
              </Space>
            }
            className="sakhtyar-account-card sakhtyar-animated-card"
          >
            {updateProfile.isSuccess ? (
              <Alert
                type="success"
                showIcon
                message="پروفایل ذخیره شد."
                style={{ marginBottom: 12 }}
              />
            ) : null}

            {updateProfile.isError ? (
              <Alert
                type="error"
                showIcon
                message={
                  updateProfile.error instanceof Error
                    ? updateProfile.error.message
                    : 'ذخیره پروفایل ناموفق بود.'
                }
                style={{ marginBottom: 12 }}
              />
            ) : null}

            <Row gutter={[12, 10]}>
              <Col xs={24} md={12}>
                <label className="sakhtyar-field-caption">
                  نام کاربری
                </label>
                <Input
                  prefix={<UserOutlined />}
                  value={user?.username ?? ''}
                  disabled
                />
              </Col>

              <Col xs={24} md={12}>
                <label className="sakhtyar-field-caption">
                  نام نمایشی
                </label>
                <Input
                  prefix={<UserOutlined />}
                  value={displayName}
                  onChange={(e) =>
                    setDisplayName(e.target.value)
                  }
                />
              </Col>

              <Col xs={24} md={12}>
                <label className="sakhtyar-field-caption">
                  ایمیل
                </label>
                <Input
                  prefix={<MailOutlined />}
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                />
              </Col>

              <Col xs={24} md={12}>
                <label className="sakhtyar-field-caption">
                  موبایل
                </label>
                <Input
                  prefix={<MobileOutlined />}
                  value={mobile}
                  onChange={(e) => setMobile(e.target.value)}
                />
              </Col>
            </Row>

            <div className="sakhtyar-card-actions">
              <Button
                type="primary"
                icon={<SaveOutlined />}
                className="sakhtyar-animated-primary"
                disabled={
                  !displayName.trim() ||
                  updateProfile.isPending
                }
                loading={updateProfile.isPending}
                onClick={() => updateProfile.mutate()}
              >
                ذخیره پروفایل
              </Button>
            </div>
          </Card>
        </Col>

        <Col xs={24} xl={10}>
          <Card
            title={
              <Space>
                <KeyOutlined />
                تغییر رمز عبور
              </Space>
            }
            className="sakhtyar-account-card sakhtyar-animated-card"
          >
            {changePassword.isError ? (
              <Alert
                type="error"
                showIcon
                message={
                  changePassword.error instanceof Error
                    ? changePassword.error.message
                    : 'تغییر رمز عبور ناموفق بود.'
                }
                style={{ marginBottom: 12 }}
              />
            ) : null}

            <Space
              direction="vertical"
              size={10}
              style={{ width: '100%' }}
            >
              <div>
                <label className="sakhtyar-field-caption">
                  رمز فعلی
                </label>
                <Input.Password
                  prefix={<LockOutlined />}
                  value={currentPassword}
                  onChange={(e) =>
                    setCurrentPassword(e.target.value)
                  }
                />
              </div>

              <div>
                <label className="sakhtyar-field-caption">
                  رمز جدید
                </label>
                <Input.Password
                  prefix={<LockOutlined />}
                  value={newPassword}
                  onChange={(e) =>
                    setNewPassword(e.target.value)
                  }
                />
              </div>

              <div>
                <label className="sakhtyar-field-caption">
                  تکرار رمز جدید
                </label>
                <Input.Password
                  prefix={<LockOutlined />}
                  status={
                    confirmPassword &&
                    confirmPassword !== newPassword
                      ? 'error'
                      : undefined
                  }
                  value={confirmPassword}
                  onChange={(e) =>
                    setConfirmPassword(e.target.value)
                  }
                />
              </div>

              <Button
                type="primary"
                block
                icon={<KeyOutlined />}
                className="sakhtyar-animated-primary"
                disabled={
                  !currentPassword ||
                  newPassword.length < 10 ||
                  newPassword !== confirmPassword ||
                  changePassword.isPending
                }
                loading={changePassword.isPending}
                onClick={() => changePassword.mutate()}
              >
                تغییر رمز عبور
              </Button>
            </Space>
          </Card>
        </Col>
      </Row>

      <Card
        title={
          <Space>
            <DesktopOutlined />
            نشست‌ها و دستگاه‌ها
          </Space>
        }
        extra={
          <Button
            danger
            size="small"
            icon={<LogoutOutlined />}
            disabled={revokeAll.isPending}
            onClick={() => revokeAll.mutate()}
          >
            خروج از همه دستگاه‌ها
          </Button>
        }
        className="sakhtyar-account-card sakhtyar-animated-card"
      >
        <List
          loading={sessions.isLoading}
          dataSource={sessions.data ?? []}
          locale={{ emptyText: 'نشستی ثبت نشده است.' }}
          renderItem={(session) => (
            <List.Item
              actions={
                session.active
                  ? [
                      <Button
                        key="revoke"
                        danger
                        size="small"
                        onClick={() =>
                          revokeSession.mutate(session.id)
                        }
                      >
                        پایان نشست
                      </Button>,
                    ]
                  : undefined
              }
            >
              <List.Item.Meta
                avatar={
                  <Avatar
                    size={36}
                    icon={<DesktopOutlined />}
                    className="sakhtyar-session-avatar"
                  />
                }
                title={
                  <Space wrap>
                    <strong>
                      {session.deviceName ||
                        (session.clientType === 'MOBILE'
                          ? 'موبایل'
                          : 'مرورگر وب')}
                    </strong>
                    <Tag
                      color={
                        session.active
                          ? 'green'
                          : 'default'
                      }
                    >
                      {session.active
                        ? 'فعال'
                        : 'پایان‌یافته'}
                    </Tag>
                  </Space>
                }
                description={`${session.ipAddress || 'IP نامشخص'} · آخرین استفاده: ${new Date(
                  session.lastUsedAt,
                ).toLocaleString('fa-IR')}`}
              />
            </List.Item>
          )}
        />
      </Card>
    </div>
  )
}
