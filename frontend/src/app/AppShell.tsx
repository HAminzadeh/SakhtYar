import {
  BellOutlined,
  FolderOpenOutlined,
  PoweroffOutlined,
  SafetyCertificateOutlined,
  RobotOutlined,
  ToolOutlined,
  DashboardOutlined,
  AuditOutlined,
  UserOutlined,
  BookOutlined,
  ImportOutlined,
  QuestionCircleOutlined,
} from '@ant-design/icons'
import {
  Dropdown,
  Avatar,
  Badge,
  Button,
  Tooltip,
  Typography,
} from 'antd'
import {
  Link,
  Outlet,
  useLocation,
  useNavigate,
} from 'react-router-dom'
import { useAuth } from '../auth/AuthProvider'
import {
  roleLabel,
  safeDisplayName,
} from '../ui/presentation'
import { LanguageSwitcher } from '../ui/LanguageSwitcher'
import { useI18n } from '../i18n/LanguageProvider'

export function AppShell() {
  const { user, logout, hasPermission } = useAuth()
  const { t } = useI18n()
  const navigate = useNavigate()
  const location = useLocation()

  const active = (prefix: string) =>
    location.pathname.startsWith(prefix)

  const handleLogout = async () => {
    await logout()
    navigate('/login')
  }

  const profileName = safeDisplayName(
    user?.displayName,
    roleLabel(user?.role) ||
      user?.username ||
      'کاربر ساخت‌یار',
  )

  return (
    <div className="sakhtyar-shell">
      <header className="sakhtyar-topbar sakhtyar-topbar-v56">
        <Link
          to="/cases"
          className="sakhtyar-brand sakhtyar-brand-v56"
        >
          <img
            src="/assets/sakhtyar/brand/sakhtyar-mark.svg"
            alt=""
            className="sakhtyar-brand-mark"
          />
          <span className="sakhtyar-brand-copy">
            <strong>ساخت‌یار</strong>
            <small>
              سامانه مدیریت هوشمند مشارکت در ساخت
            </small>
          </span>
        </Link>

        <nav className="sakhtyar-main-nav">
          {hasPermission('CASE_READ') && (
            <Button
              type={
                active('/cases') ? 'primary' : 'default'
              }
              icon={<FolderOpenOutlined />}
              onClick={() => navigate('/cases')}
            >
              پرونده‌ها
            </Button>
          )}

          {hasPermission('USER_MANAGE') && (
            <Button
              type={
                active('/admin/users')
                  ? 'primary'
                  : 'default'
              }
              icon={<SafetyCertificateOutlined />}
              onClick={() =>
                navigate('/admin/users')
              }
            >
              کاربران
            </Button>
          )}

          {hasPermission('AI_MANAGE') && (
            <Button type={active('/admin/ai') ? 'primary' : 'default'} icon={<RobotOutlined />} onClick={() => navigate('/admin/ai')}>
              مدیریت هوش مصنوعی
            </Button>
          )}


          {hasPermission('OPERATIONS_READ') && (
            <Button type={active('/admin/operations') ? 'primary' : 'default'} icon={<DashboardOutlined />} onClick={() => navigate('/admin/operations')}>
              عملیات
            </Button>
          )}

          {hasPermission('USER_MANAGE') && (
            <Button
              type={location.pathname === '/admin/knowledge' ? 'primary' : 'default'}
              icon={<BookOutlined />}
              onClick={() => navigate('/admin/knowledge')}
            >
              مرکز دانش
            </Button>
          )}
          {hasPermission('USER_MANAGE') && (
            <Button
              type={active('/admin/knowledge/import') ? 'primary' : 'default'}
              icon={<ImportOutlined />}
              onClick={() => navigate('/admin/knowledge/import')}
            >
              ورود دانش
            </Button>
          )}
          {(hasPermission('USER_MANAGE') || hasPermission('QUALITY_VIEW')) && (
            <Dropdown trigger={['click']} placement="bottomRight" menu={{items: [
              ...(hasPermission('QUALITY_VIEW') ? [{ key:'quality',label:'مرکز آزمون و تضمین کیفیت',icon:<AuditOutlined />,onClick:()=>navigate('/admin/quality') }] : []),
              ...(hasPermission('USER_MANAGE') ? [{ key:'simulations',label:'شبیه‌سازی و داده‌های آزمایشی',icon:<AuditOutlined />,onClick:()=>navigate('/admin/simulations') }] : []),
              ...(hasPermission('USER_MANAGE') ? [{ key:'engineering',label:'مرکز راهبری هوش مصنوعی',icon:<ToolOutlined />,onClick:()=>navigate('/admin/engineering') }] : []),
            ]}}>
              <Button type={active('/admin/quality') || active('/admin/engineering') || active('/admin/simulations') ? 'primary' : 'default'} icon={<ToolOutlined />}>مراکز تخصصی</Button>
            </Dropdown>
          )}
          <Button type={active('/help') ? 'primary' : 'default'} icon={<QuestionCircleOutlined />} onClick={() => navigate('/help')}>
            آموزش
          </Button>

          <Button
            type={
              active('/account') ? 'primary' : 'default'
            }
            icon={<UserOutlined />}
            onClick={() => navigate('/account')}
          >
            حساب من
          </Button>
          <Dropdown menu={{items: [
            ...(hasPermission('CASE_READ') ? [{key:'market',label:'تحلیل بازار',onClick:()=>navigate('/market')}] : []),
            ...(hasPermission('USER_MANAGE') ? [
              {key:'builders',label:'مدیریت سازندگان',onClick:()=>navigate('/admin/builders')},
              {key:'events',label:'مانیتورینگ و اعلان‌ها',onClick:()=>navigate('/admin/events')},
            ] : []),
            ...(hasPermission('AGENT_USE') ? [{key:'intelligence',label:'رجیستری هوشمندی',onClick:()=>navigate('/intelligence')}] : []),
          ]}} trigger={['click']}>
            <Button>خدمات هوشمند</Button>
          </Dropdown>
        </nav>

        <div className="sakhtyar-profile-panel">
          <LanguageSwitcher />
          <Button type="text" onClick={() => navigate('/settings')}>
            {t('settings')}
          </Button>
          <Badge dot offset={[-2, 4]}>
            <Button
              className="sakhtyar-notification"
              type="text"
              icon={<BellOutlined />}
              aria-label="اعلان‌ها"
            />
          </Badge>

          <Avatar
            className="sakhtyar-profile-avatar"
            icon={<UserOutlined />}
          />

          <div className="sakhtyar-profile-copy">
            <Typography.Text strong>
              {profileName}
            </Typography.Text>
            <Typography.Text type="secondary">
              {roleLabel(user?.role)}
            </Typography.Text>
          </div>

          <Tooltip title="خروج از حساب">
            <Button
              danger
              shape="circle"
              className="sakhtyar-logout-button"
              icon={<PoweroffOutlined />}
              aria-label="خروج"
              onClick={handleLogout}
            />
          </Tooltip>
        </div>
      </header>

      <main className="sakhtyar-page">
        <Outlet />
      </main>
    </div>
  )
}
