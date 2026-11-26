import {
  ApartmentOutlined,
  AuditOutlined,
  BarChartOutlined,
  BookOutlined,
  DashboardOutlined,
  DownOutlined,
  FolderOpenOutlined,
  ImportOutlined,
  MenuOutlined,
  PoweroffOutlined,
  QuestionCircleOutlined,
  RobotOutlined,
  SafetyCertificateOutlined,
  SettingOutlined,
  TeamOutlined,
  ToolOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { Avatar, Button, Drawer, Dropdown, Menu, Typography } from 'antd'
import type { MenuProps } from 'antd'
import { useState } from 'react'
import type { ReactNode } from 'react'
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom'
import type { Permission } from '../api/types'
import { useAuth } from '../auth/AuthProvider'
import { LanguageSwitcher } from '../ui/LanguageSwitcher'
import { roleLabel, safeDisplayName } from '../ui/presentation'
import '../styles/navigation-workflow.css'

type NavigationLink = {
  label: string
  path: string
  permission?: Permission
  icon?: ReactNode
}
type NavigationGroup = {
  key: string
  label: string
  icon: ReactNode
  links: NavigationLink[]
}

// Navigation is an information architecture, never an authorization boundary.
// App.tsx and backend permissions remain responsible for enforcing access.
const navigationGroups: NavigationGroup[] = [
  {
    key: 'market', label: 'بازار و همکاران', icon: <TeamOutlined />,
    links: [
      { label: 'تحلیل بازار', path: '/market', permission: 'CASE_READ', icon: <BarChartOutlined /> },
      { label: 'مدیریت سازندگان', path: '/admin/builders', permission: 'USER_MANAGE', icon: <ApartmentOutlined /> },
    ],
  },
  {
    key: 'knowledge', label: 'دانش و هوشمندی', icon: <BookOutlined />,
    links: [
      { label: 'مرکز دانش', path: '/admin/knowledge', permission: 'USER_MANAGE', icon: <BookOutlined /> },
      { label: 'ورود و استخراج دانش', path: '/admin/knowledge/import', permission: 'USER_MANAGE', icon: <ImportOutlined /> },
      { label: 'مدیریت هوش مصنوعی', path: '/admin/ai', permission: 'AI_MANAGE', icon: <RobotOutlined /> },
      { label: 'دستیارها و رجیستری هوشمند', path: '/intelligence', permission: 'AGENT_USE', icon: <RobotOutlined /> },
      { label: 'مرکز راهبری هوشمندی', path: '/admin/engineering', permission: 'USER_MANAGE', icon: <ToolOutlined /> },
    ],
  },
  {
    key: 'management', label: 'مدیریت سامانه', icon: <DashboardOutlined />,
    links: [
      { label: 'کاربران و دسترسی‌ها', path: '/admin/users', permission: 'USER_MANAGE', icon: <SafetyCertificateOutlined /> },
      { label: 'عملیات سامانه', path: '/admin/operations', permission: 'OPERATIONS_READ', icon: <DashboardOutlined /> },
      { label: 'رویدادها و اعلان‌ها', path: '/admin/events', permission: 'USER_MANAGE', icon: <AuditOutlined /> },
      { label: 'آزمون و تضمین کیفیت', path: '/admin/quality', permission: 'QUALITY_VIEW', icon: <AuditOutlined /> },
      { label: 'شبیه‌سازی و داده‌های تست', path: '/admin/simulations', permission: 'USER_MANAGE', icon: <ToolOutlined /> },
      { label: 'تنظیمات پلتفرم', path: '/admin/config', permission: 'USER_MANAGE', icon: <SettingOutlined /> },
    ],
  },
]

export function AppShell() {
  const { user, logout, hasPermission } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [mobileOpen, setMobileOpen] = useState(false)

  const pathActive = (path: string) =>
    location.pathname === path || location.pathname.startsWith(path + '/')

  const visibleGroups = navigationGroups
    .map(group => ({
      ...group,
      links: group.links.filter(link => !link.permission || hasPermission(link.permission)),
    }))
    .filter(group => group.links.length > 0)

  const profileName = safeDisplayName(
    user?.displayName,
    roleLabel(user?.role) || user?.username || 'کاربر ساخت‌یار',
  )

  const go = (path: string) => {
    setMobileOpen(false)
    navigate(path)
  }

  const handleLogout = async () => {
    setMobileOpen(false)
    await logout()
    navigate('/login')
  }

  const profileItems: MenuProps['items'] = [
    { key: 'account', label: 'حساب کاربری من', icon: <UserOutlined />, onClick: () => go('/account') },
    { key: 'preferences', label: 'تنظیمات شخصی', icon: <SettingOutlined />, onClick: () => go('/settings') },
    { key: 'help', label: 'راهنما و آموزش', icon: <QuestionCircleOutlined />, onClick: () => go('/help') },
    { type: 'divider' },
    { key: 'logout', label: 'خروج از حساب', icon: <PoweroffOutlined />, danger: true, onClick: () => void handleLogout() },
  ]

  const mobileItems: MenuProps['items'] = [
    ...(hasPermission('CASE_READ')
      ? [{ key: '/cases', label: 'پرونده‌های من', icon: <FolderOpenOutlined /> }]
      : []),
    ...visibleGroups.map(group => ({
      key: group.key,
      label: group.label,
      icon: group.icon,
      children: group.links.map(link => ({ key: link.path, label: link.label, icon: link.icon })),
    })),
    { key: '/help', label: 'راهنما و آموزش', icon: <QuestionCircleOutlined /> },
    { key: '/account', label: 'حساب کاربری', icon: <UserOutlined /> },
    { key: '/settings', label: 'تنظیمات', icon: <SettingOutlined /> },
  ]

  return (
    <div className="sakhtyar-shell">
      <header className="sakhtyar-topbar sakhtyar-topbar-v56 sy-nav-header">
        <Link to="/cases" className="sakhtyar-brand sakhtyar-brand-v56" aria-label="ساخت‌یار، فهرست پرونده‌ها">
          <img src="/assets/sakhtyar/brand/sakhtyar-mark.svg" alt="" className="sakhtyar-brand-mark" />
          <span className="sakhtyar-brand-copy">
            <strong>ساخت‌یار</strong>
            <small>سامانه مدیریت هوشمند مشارکت در ساخت</small>
          </span>
        </Link>

        <nav className="sakhtyar-main-nav sy-nav-desktop" aria-label="منوی اصلی سامانه">
          {hasPermission('CASE_READ') && (
            <Button type={pathActive('/cases') ? 'primary' : 'default'} icon={<FolderOpenOutlined />}
              onClick={() => go('/cases')}>
              پرونده‌ها
            </Button>
          )}
          {visibleGroups.map(group => (
            <Dropdown key={group.key} trigger={['click']} placement="bottomRight"
              menu={{
                selectedKeys: group.links.filter(link => pathActive(link.path)).map(link => link.path),
                items: group.links.map(link => ({
                  key: link.path,
                  label: link.label,
                  icon: link.icon,
                  onClick: () => go(link.path),
                })),
              }}>
              <Button type={group.links.some(link => pathActive(link.path)) ? 'primary' : 'default'}
                icon={group.icon} aria-label={`زیرمنوی ${group.label}`}>
                {group.label} <DownOutlined className="sy-nav-chevron" />
              </Button>
            </Dropdown>
          ))}
          <Button type={pathActive('/help') ? 'primary' : 'default'}
            icon={<QuestionCircleOutlined />} onClick={() => go('/help')}>
            راهنما
          </Button>
        </nav>

        <Button className="sy-nav-mobile-trigger" icon={<MenuOutlined />}
          aria-label="باز کردن منوی سامانه" onClick={() => setMobileOpen(true)}>
          منو
        </Button>

        <div className="sakhtyar-profile-panel sy-nav-profile">
          <LanguageSwitcher />
          <Dropdown trigger={['click']} placement="bottomLeft" menu={{ items: profileItems }}>
            <Button type="text" className="sy-nav-profile-trigger" aria-label="گزینه‌های حساب کاربری">
              <Avatar className="sakhtyar-profile-avatar" icon={<UserOutlined />} />
              <span className="sakhtyar-profile-copy">
                <Typography.Text strong ellipsis>{profileName}</Typography.Text>
                <Typography.Text type="secondary" ellipsis>{roleLabel(user?.role)}</Typography.Text>
              </span>
              <DownOutlined />
            </Button>
          </Dropdown>
        </div>
      </header>

      <Drawer title="منوی ساخت‌یار" placement="right" width={320} open={mobileOpen}
        onClose={() => setMobileOpen(false)} className="sy-nav-drawer">
        <Menu mode="inline" items={mobileItems} selectedKeys={[location.pathname]}
          defaultOpenKeys={visibleGroups.map(group => group.key)}
          onClick={({ key }) => go(key)} />
        <Button danger block icon={<PoweroffOutlined />} onClick={() => void handleLogout()}
          style={{ marginTop: 16 }}>خروج از حساب</Button>
      </Drawer>

      <main className="sakhtyar-page"><Outlet /></main>
    </div>
  )
}
