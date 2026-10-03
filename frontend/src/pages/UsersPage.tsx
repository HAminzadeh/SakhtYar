import {
  EditOutlined,
  KeyOutlined,
  PlusOutlined,
  SearchOutlined,
  SafetyCertificateOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons'
import {
  Alert,
  Avatar,
  Button,
  Card,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd'
import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client'
import type {
  UserAdminItem,
  UserRole,
  UserStatus,
} from '../api/types'
import { PageHero } from '../ui/PageHero'
import {
  roleLabel,
  safeDisplayName,
  userStatusLabel,
} from '../ui/presentation'

const roles: Array<{ value: UserRole; label: string }> = [
  { value: 'ADMIN', label: 'مدیر سیستم' },
  { value: 'PROJECT_MANAGER', label: 'مدیر پروژه' },
  { value: 'ANALYST', label: 'تحلیل‌گر' },
  { value: 'LEGAL_EXPERT', label: 'کارشناس حقوقی' },
  { value: 'READ_ONLY', label: 'فقط مشاهده' },
]

const statuses: Array<{ value: UserStatus; label: string }> = [
  { value: 'PENDING', label: 'در انتظار تأیید' },
  { value: 'ACTIVE', label: 'فعال' },
  { value: 'SUSPENDED', label: 'تعلیق‌شده' },
]

const nowIso = () => new Date().toISOString()
const hoursAgo = (hours: number) =>
  new Date(Date.now() - hours * 60 * 60 * 1000).toISOString()

const demoUsers: UserAdminItem[] = [
  {
    id: 'demo-project-manager',
    username: 'project.manager',
    displayName: 'مهدی رضوانی',
    email: 'project.manager@demo.local',
    mobile: '09120000001',
    role: 'PROJECT_MANAGER',
    status: 'ACTIVE',
    permissions: ['CASE_READ', 'CASE_WRITE', 'PROPERTY_READ', 'PROPERTY_WRITE', 'OWNER_READ', 'OWNER_WRITE', 'DOCUMENT_READ', 'DOCUMENT_WRITE'],
    failedLoginAttempts: 0,
    lockedUntil: null,
    lastLoginAt: hoursAgo(2),
    passwordChangedAt: hoursAgo(240),
    createdAt: hoursAgo(1500),
    updatedAt: nowIso(),
  },
  {
    id: 'demo-analyst',
    username: 'analyst.demo',
    displayName: 'سارا محمدی',
    email: 'analyst@demo.local',
    mobile: '09120000002',
    role: 'ANALYST',
    status: 'ACTIVE',
    permissions: ['CASE_READ', 'PROPERTY_READ', 'OWNER_READ', 'DOCUMENT_READ', 'AGENT_USE'],
    failedLoginAttempts: 0,
    lockedUntil: null,
    lastLoginAt: hoursAgo(5),
    passwordChangedAt: hoursAgo(400),
    createdAt: hoursAgo(1200),
    updatedAt: nowIso(),
  },
  {
    id: 'demo-legal',
    username: 'legal.demo',
    displayName: 'آرمان سلیمی',
    email: 'legal@demo.local',
    mobile: '09120000003',
    role: 'LEGAL_EXPERT',
    status: 'ACTIVE',
    permissions: ['CASE_READ', 'PROPERTY_READ', 'OWNER_READ', 'DOCUMENT_READ', 'DOCUMENT_WRITE'],
    failedLoginAttempts: 0,
    lockedUntil: null,
    lastLoginAt: hoursAgo(24),
    passwordChangedAt: hoursAgo(600),
    createdAt: hoursAgo(1600),
    updatedAt: nowIso(),
  },
  {
    id: 'demo-readonly',
    username: 'viewer.demo',
    displayName: 'نگار کریمی',
    email: 'viewer@demo.local',
    mobile: '09120000004',
    role: 'READ_ONLY',
    status: 'ACTIVE',
    permissions: ['CASE_READ', 'PROPERTY_READ', 'OWNER_READ', 'DOCUMENT_READ'],
    failedLoginAttempts: 0,
    lockedUntil: null,
    lastLoginAt: hoursAgo(52),
    passwordChangedAt: hoursAgo(720),
    createdAt: hoursAgo(1900),
    updatedAt: nowIso(),
  },
  {
    id: 'demo-pending',
    username: 'pending.demo',
    displayName: 'رضا اکبری',
    email: 'pending@demo.local',
    mobile: '09120000005',
    role: 'PROJECT_MANAGER',
    status: 'PENDING',
    permissions: ['CASE_READ'],
    failedLoginAttempts: 0,
    lockedUntil: null,
    lastLoginAt: null,
    passwordChangedAt: hoursAgo(24),
    createdAt: hoursAgo(48),
    updatedAt: nowIso(),
  },
]

type EditForm = {
  displayName: string
  email?: string
  mobile?: string
  role: UserRole
  status: UserStatus
}

type CreateForm = EditForm & {
  username: string
  password: string
}

function isDemoUser(user: UserAdminItem) {
  return user.id.startsWith('demo-')
}

export function UsersPage() {
  const queryClient = useQueryClient()
  const [search, setSearch] = useState('')
  const [roleFilter, setRoleFilter] = useState<string>('ALL')
  const [statusFilter, setStatusFilter] = useState<string>('ALL')
  const [editing, setEditing] = useState<UserAdminItem | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [resetTarget, setResetTarget] = useState<UserAdminItem | null>(null)

  const users = useQuery({
    queryKey: ['admin-users'],
    queryFn: () => api<UserAdminItem[]>('/api/v1/admin/users'),
  })

  const combinedUsers = useMemo(() => {
    const real = users.data ?? []
    const realUsernames = new Set(real.map((user) => user.username))

    return [
      ...real,
      ...demoUsers.filter(
        (demo) => !realUsernames.has(demo.username),
      ),
    ]
  }, [users.data])

  const updateUser = useMutation({
    mutationFn: (values: EditForm) =>
      api<UserAdminItem>(`/api/v1/admin/users/${editing!.id}`, {
        method: 'PUT',
        body: JSON.stringify({
          ...values,
          email: values.email || null,
          mobile: values.mobile || null,
        }),
      }),
    onSuccess: () => {
      setEditing(null)
      queryClient.invalidateQueries({ queryKey: ['admin-users'] })
    },
  })

  const createUser = useMutation({
    mutationFn: (values: CreateForm) =>
      api<UserAdminItem>('/api/v1/admin/users', {
        method: 'POST',
        body: JSON.stringify({
          ...values,
          email: values.email || null,
          mobile: values.mobile || null,
        }),
      }),
    onSuccess: () => {
      setCreateOpen(false)
      queryClient.invalidateQueries({ queryKey: ['admin-users'] })
    },
  })

  const reset = useMutation({
    mutationFn: (values: { newPassword: string }) =>
      api<void>(
        `/api/v1/admin/users/${resetTarget!.id}/reset-password`,
        {
          method: 'POST',
          body: JSON.stringify(values),
        },
      ),
    onSuccess: () => {
      setResetTarget(null)
      queryClient.invalidateQueries({ queryKey: ['admin-users'] })
    },
  })

  const filtered = useMemo(() => {
    const q = search.trim().toLocaleLowerCase(document.documentElement.lang === 'fa' ? 'fa-IR' : 'en-US')

    return combinedUsers.filter((user) => {
      const matchesSearch =
        !q ||
        [user.displayName, user.username, user.email]
          .filter(Boolean)
          .some((value) =>
            String(value).toLocaleLowerCase(document.documentElement.lang === 'fa' ? 'fa-IR' : 'en-US').includes(q),
          )

      const matchesRole =
        roleFilter === 'ALL' || user.role === roleFilter

      const matchesStatus =
        statusFilter === 'ALL' || user.status === statusFilter

      return matchesSearch && matchesRole && matchesStatus
    })
  }, [combinedUsers, search, roleFilter, statusFilter])

  const columns = [
    {
      title: 'کاربر',
      key: 'user',
      render: (_: unknown, user: UserAdminItem) => (
        <Space size={10}>
          <Avatar
            size={40}
            className="sakhtyar-user-avatar"
            icon={<UserOutlined />}
          />
          <div>
            <Space size={6}>
              <Typography.Text strong>
                {safeDisplayName(
                  user.displayName,
                  roleLabel(user.role) || user.username,
                )}
              </Typography.Text>
              {isDemoUser(user) ? (
                <Tag color="purple">نمونه</Tag>
              ) : null}
            </Space>
            <div className="sakhtyar-user-secondary">
              @{user.username}
            </div>
            {user.email ? (
              <div className="sakhtyar-user-secondary">
                {user.email}
              </div>
            ) : null}
          </div>
        </Space>
      ),
    },
    {
      title: 'نقش',
      dataIndex: 'role',
      render: (role: UserRole) => (
        <Tag
          color="blue"
          icon={<SafetyCertificateOutlined />}
        >
          {roleLabel(role)}
        </Tag>
      ),
    },
    {
      title: 'وضعیت',
      dataIndex: 'status',
      render: (status: UserStatus) => (
        <Tag
          color={
            status === 'ACTIVE'
              ? 'green'
              : status === 'PENDING'
                ? 'gold'
                : 'default'
          }
          className={
            status === 'ACTIVE'
              ? 'sakhtyar-status-active'
              : status === 'PENDING'
                ? 'sakhtyar-status-pending'
                : ''
          }
        >
          {userStatusLabel(status)}
        </Tag>
      ),
    },
    {
      title: 'آخرین ورود',
      dataIndex: 'lastLoginAt',
      render: (value?: string | null) =>
        value
          ? new Date(value).toLocaleString(document.documentElement.lang === 'fa' ? 'fa-IR' : 'en-US')
          : 'ثبت نشده',
    },
    {
      title: 'عملیات',
      key: 'actions',
      render: (_: unknown, user: UserAdminItem) => (
        isDemoUser(user) ? (
          <Tag>نمونه نمایشی</Tag>
        ) : (
          <Space>
            <Button
              size="small"
              icon={<EditOutlined />}
              onClick={() => setEditing(user)}
            >
              ویرایش
            </Button>
            <Button
              size="small"
              icon={<KeyOutlined />}
              onClick={() => setResetTarget(user)}
            >
              رمز
            </Button>
          </Space>
        )
      ),
    },
  ]

  return (
    <div className="sakhtyar-page-stack sakhtyar-users-page-v54">
      <PageHero
        image="/assets/sakhtyar/heroes/users-hero.jpg"
        title="کاربران و دسترسی‌ها"
        subtitle="مدیریت کاربران، نقش‌ها، وضعیت حساب و بازنشانی رمز عبور"
        icon={<TeamOutlined />}
      />

      <Card className="sakhtyar-filter-card">
        <div className="sakhtyar-filter-row">
          <Input
            allowClear
            prefix={<SearchOutlined />}
            placeholder="جستجو در نام، نام کاربری یا ایمیل..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />

          <Select
            value={roleFilter}
            onChange={setRoleFilter}
            options={[
              { value: 'ALL', label: 'همه نقش‌ها' },
              ...roles,
            ]}
          />

          <Select
            value={statusFilter}
            onChange={setStatusFilter}
            options={[
              { value: 'ALL', label: 'همه وضعیت‌ها' },
              ...statuses,
            ]}
          />

          <Button
            type="primary"
            icon={<PlusOutlined />}
            className="sakhtyar-animated-primary"
            onClick={() => setCreateOpen(true)}
          >
            ایجاد کاربر
          </Button>
        </div>
      </Card>

      {users.isError ? (
        <Alert
          type="warning"
          showIcon
          message="دریافت کاربران واقعی ناموفق بود؛ کاربران نمونه همچنان نمایش داده می‌شوند."
        />
      ) : null}

      <Card className="sakhtyar-table-card sakhtyar-animated-card">
        <Table
          rowKey="id"
          loading={users.isLoading}
          dataSource={filtered}
          columns={columns}
          pagination={{
            pageSize: 10,
            showSizeChanger: false,
          }}
          scroll={{ x: 920 }}
        />
      </Card>

      <Modal
        open={Boolean(editing)}
        title="ویرایش کاربر"
        footer={null}
        onCancel={() => setEditing(null)}
        destroyOnHidden
      >
        {editing ? (
          <Form<EditForm>
            layout="vertical"
            initialValues={{
              displayName: safeDisplayName(
                editing.displayName,
                roleLabel(editing.role) || editing.username,
              ),
              email: editing.email ?? '',
              mobile: editing.mobile ?? '',
              role: editing.role,
              status: editing.status,
            }}
            onFinish={(values) => updateUser.mutate(values)}
          >
            <Form.Item
              label="نام نمایشی"
              name="displayName"
              rules={[{ required: true }]}
            >
              <Input />
            </Form.Item>

            <Form.Item label="ایمیل" name="email">
              <Input />
            </Form.Item>

            <Form.Item label="موبایل" name="mobile">
              <Input />
            </Form.Item>

            <Form.Item label="نقش" name="role">
              <Select options={roles} />
            </Form.Item>

            <Form.Item label="وضعیت" name="status">
              <Select options={statuses} />
            </Form.Item>

            <Button
              type="primary"
              htmlType="submit"
              block
              loading={updateUser.isPending}
            >
              ذخیره تغییرات
            </Button>
          </Form>
        ) : null}
      </Modal>

      <Modal
        open={createOpen}
        title="ایجاد کاربر"
        footer={null}
        onCancel={() => setCreateOpen(false)}
        destroyOnHidden
      >
        <Form<CreateForm>
          layout="vertical"
          initialValues={{
            role: 'READ_ONLY',
            status: 'ACTIVE',
          }}
          onFinish={(values) => createUser.mutate(values)}
        >
          <Form.Item
            label="نام کاربری"
            name="username"
            rules={[{ required: true }]}
          >
            <Input />
          </Form.Item>

          <Form.Item
            label="نام نمایشی"
            name="displayName"
            rules={[{ required: true }]}
          >
            <Input />
          </Form.Item>

          <Form.Item label="ایمیل" name="email">
            <Input />
          </Form.Item>

          <Form.Item label="موبایل" name="mobile">
            <Input />
          </Form.Item>

          <Form.Item
            label="رمز اولیه"
            name="password"
            rules={[{ required: true, min: 10 }]}
          >
            <Input.Password />
          </Form.Item>

          <Form.Item label="نقش" name="role">
            <Select options={roles} />
          </Form.Item>

          <Form.Item label="وضعیت" name="status">
            <Select options={statuses} />
          </Form.Item>

          <Button
            type="primary"
            htmlType="submit"
            block
            loading={createUser.isPending}
          >
            ایجاد کاربر
          </Button>
        </Form>
      </Modal>

      <Modal
        open={Boolean(resetTarget)}
        title={`بازنشانی رمز ${safeDisplayName(
          resetTarget?.displayName,
          resetTarget?.username || '',
        )}`}
        footer={null}
        onCancel={() => setResetTarget(null)}
        destroyOnHidden
      >
        <Form<{ newPassword: string }>
          layout="vertical"
          onFinish={(values) => reset.mutate(values)}
        >
          <Form.Item
            label="رمز جدید"
            name="newPassword"
            rules={[
              {
                required: true,
                min: 10,
                message: 'حداقل ۱۰ کاراکتر',
              },
            ]}
          >
            <Input.Password />
          </Form.Item>

          <Button
            type="primary"
            htmlType="submit"
            block
            loading={reset.isPending}
          >
            بازنشانی رمز
          </Button>
        </Form>
      </Modal>
    </div>
  )
}
