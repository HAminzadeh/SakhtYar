import {
  ApartmentOutlined,
  BellOutlined,
  FolderOpenOutlined,
  LogoutOutlined,
  SafetyCertificateOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { Avatar, Badge, Button, Typography } from 'antd'
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthProvider'
import { roleLabel, safeDisplayName } from '../ui/presentation'

export function AppShell() {
  const { user, logout, hasPermission } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()

  const active = (prefix: string) => location.pathname.startsWith(prefix)

  const handleLogout = async () => {
    await logout()
    navigate('/login')
  }

  const profileName = safeDisplayName(
    user?.displayName,
    roleLabel(user?.role) || user?.username || 'کاربر ساخت‌یار',
  )

  return (
    <div className="sakhtyar-shell">
      <header className="sakhtyar-topbar">
        <Link to="/cases" className="sakhtyar-brand">
          <span className="sakhtyar-brand-logo">
            <ApartmentOutlined />
            <i />
          </span>
          <span className="sakhtyar-brand-copy">
            <strong>ساخت‌یار</strong>
            <small>سامانه مدیریت هوشمند مشارکت در ساخت</small>
          </span>
        </Link>

        <nav className="sakhtyar-main-nav">
          {hasPermission('CASE_READ') && (
            <Button
              type={active('/cases') ? 'primary' : 'default'}
              icon={<FolderOpenOutlined />}
              onClick={() => navigate('/cases')}
            >
              پرونده‌ها
            </Button>
          )}

          {hasPermission('USER_MANAGE') && (
            <Button
              type={active('/admin/users') ? 'primary' : 'default'}
              icon={<SafetyCertificateOutlined />}
              onClick={() => navigate('/admin/users')}
            >
              کاربران
            </Button>
          )}

          <Button
            type={active('/account') ? 'primary' : 'default'}
            icon={<UserOutlined />}
            onClick={() => navigate('/account')}
          >
            حساب من
          </Button>
        </nav>

        <div className="sakhtyar-profile-panel">
          <Badge dot offset={[-2, 4]}>
            <Button
              className="sakhtyar-notification"
              type="text"
              icon={<BellOutlined />}
              aria-label="اعلان‌ها"
            />
          </Badge>

          <Avatar className="sakhtyar-profile-avatar" icon={<UserOutlined />} />

          <div className="sakhtyar-profile-copy">
            <Typography.Text strong>{profileName}</Typography.Text>
            <Typography.Text type="secondary">
              {roleLabel(user?.role)}
            </Typography.Text>
          </div>

          <Button icon={<LogoutOutlined />} onClick={handleLogout}>
            خروج
          </Button>
        </div>
      </header>

      <main className="sakhtyar-page">
        <Outlet />
      </main>
    </div>
  )
}
