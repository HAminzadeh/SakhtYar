import {
  EditOutlined,
  KeyOutlined,
  PlusOutlined,
  SearchOutlined,
  SafetyCertificateOutlined,
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
    const q = search.trim().toLocaleLowerCase('fa')

    return (users.data ?? []).filter((user) => {
      const matchesSearch =
        !q ||
        [user.displayName, user.username, user.email]
          .filter(Boolean)
          .some((value) =>
            String(value).toLocaleLowerCase('fa').includes(q),
          )

      const matchesRole =
        roleFilter === 'ALL' || user.role === roleFilter

      const matchesStatus =
        statusFilter === 'ALL' || user.status === statusFilter

      return matchesSearch && matchesRole && matchesStatus
    })
  }, [users.data, search, roleFilter, statusFilter])

  const columns = [
    {
      title: 'کاربر',
      key: 'user',
      render: (_: unknown, user: UserAdminItem) => (
        <Space size={12}>
          <Avatar
            size={46}
            className="sakhtyar-user-avatar"
            icon={<UserOutlined />}
          />
          <div>
            <Typography.Text strong>
              {safeDisplayName(
                user.displayName,
                roleLabel(user.role) || user.username,
              )}
            </Typography.Text>
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
          ? new Date(value).toLocaleString('fa-IR')
          : 'ثبت نشده',
    },
    {
      title: 'عملیات',
      key: 'actions',
      render: (_: unknown, user: UserAdminItem) => (
        <Space>
          <Button
            icon={<EditOutlined />}
            onClick={() => setEditing(user)}
          >
            ویرایش
          </Button>
          <Button
            icon={<KeyOutlined />}
            onClick={() => setResetTarget(user)}
          >
            رمز
          </Button>
        </Space>
      ),
    },
  ]

  return (
    <div className="sakhtyar-page-stack">
      <section className="sakhtyar-hero sakhtyar-hero-users">
        <div className="sakhtyar-hero-overlay" />
        <div className="sakhtyar-hero-copy">
          <span className="sakhtyar-hero-icon">
            <UserOutlined />
          </span>
          <div>
            <Typography.Title level={1}>
              کاربران و دسترسی‌ها
            </Typography.Title>
            <Typography.Paragraph>
              مدیریت کاربران، نقش‌ها، وضعیت حساب و بازنشانی رمز عبور
            </Typography.Paragraph>
          </div>
        </div>
      </section>

      <Card className="sakhtyar-filter-card">
        <div className="sakhtyar-filter-row">
          <Input
            size="large"
            allowClear
            prefix={<SearchOutlined />}
            placeholder="جستجو در نام، نام کاربری یا ایمیل..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />

          <Select
            size="large"
            value={roleFilter}
            onChange={setRoleFilter}
            options={[
              { value: 'ALL', label: 'همه نقش‌ها' },
              ...roles,
            ]}
          />

          <Select
            size="large"
            value={statusFilter}
            onChange={setStatusFilter}
            options={[
              { value: 'ALL', label: 'همه وضعیت‌ها' },
              ...statuses,
            ]}
          />

          <Button
            type="primary"
            size="large"
            icon={<PlusOutlined />}
            onClick={() => setCreateOpen(true)}
          >
            ایجاد کاربر
          </Button>
        </div>
      </Card>

      {users.isError ? (
        <Alert
          type="error"
          showIcon
          message="دریافت کاربران ناموفق بود."
        />
      ) : null}

      <Card className="sakhtyar-table-card">
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
              <Input size="large" />
            </Form.Item>

            <Form.Item label="ایمیل" name="email">
              <Input size="large" />
            </Form.Item>

            <Form.Item label="موبایل" name="mobile">
              <Input size="large" />
            </Form.Item>

            <Form.Item label="نقش" name="role">
              <Select size="large" options={roles} />
            </Form.Item>

            <Form.Item label="وضعیت" name="status">
              <Select size="large" options={statuses} />
            </Form.Item>

            <Button
              type="primary"
              htmlType="submit"
              block
              size="large"
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
            <Input size="large" />
          </Form.Item>

          <Form.Item
            label="نام نمایشی"
            name="displayName"
            rules={[{ required: true }]}
          >
            <Input size="large" />
          </Form.Item>

          <Form.Item label="ایمیل" name="email">
            <Input size="large" />
          </Form.Item>

          <Form.Item label="موبایل" name="mobile">
            <Input size="large" />
          </Form.Item>

          <Form.Item
            label="رمز اولیه"
            name="password"
            rules={[{ required: true, min: 10 }]}
          >
            <Input.Password size="large" />
          </Form.Item>

          <Form.Item label="نقش" name="role">
            <Select size="large" options={roles} />
          </Form.Item>

          <Form.Item label="وضعیت" name="status">
            <Select size="large" options={statuses} />
          </Form.Item>

          <Button
            type="primary"
            htmlType="submit"
            block
            size="large"
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
            <Input.Password size="large" />
          </Form.Item>

          <Button
            type="primary"
            htmlType="submit"
            block
            size="large"
            loading={reset.isPending}
          >
            بازنشانی رمز
          </Button>
        </Form>
      </Modal>
    </div>
  )
}
