import AddRoundedIcon from '@mui/icons-material/AddRounded'
import LockResetRoundedIcon from '@mui/icons-material/LockResetRounded'
import ManageAccountsRoundedIcon from '@mui/icons-material/ManageAccountsRounded'
import {
  Alert,
  Button,
  Card,
  CardContent,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api } from '../api/client'
import type { UserAdminItem, UserRole, UserStatus } from '../api/types'

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

const roleDescription: Record<UserRole, string> = {
  ADMIN: 'دسترسی کامل، مدیریت کاربران و تمام عملیات سیستم',
  PROJECT_MANAGER: 'مدیریت پرونده، ملک، مالکین، مدارک و Agentها',
  ANALYST: 'مشاهده پرونده، تحلیل Agent و ویرایش اطلاعات ملک',
  LEGAL_EXPERT: 'مشاهده پرونده، مالکین، مدارک و ابزارهای Agent',
  READ_ONLY: 'فقط مشاهده اطلاعات پرونده، ملک، مالکین و مدارک',
}

const rolePermissions: Record<UserRole, string[]> = {
  ADMIN: ['تمام دسترسی‌های سیستم'],
  PROJECT_MANAGER: [
    'مشاهده و ویرایش پرونده',
    'ویرایش ملک و مالکین',
    'مدیریت مدارک',
    'استفاده از Agentها',
    'مدیریت واژه‌نامه',
  ],
  ANALYST: [
    'مشاهده پرونده',
    'مشاهده و ویرایش ملک',
    'مشاهده مالکین و مدارک',
    'استفاده از Agentها',
  ],
  LEGAL_EXPERT: [
    'مشاهده پرونده و ملک',
    'مشاهده مالکین',
    'مشاهده و افزودن مدارک',
    'استفاده از Agentها',
  ],
  READ_ONLY: [
    'مشاهده پرونده',
    'مشاهده ملک',
    'مشاهده مالکین',
    'مشاهده مدارک',
  ],
}

type EditForm = {
  displayName: string
  email: string
  mobile: string
  role: UserRole
  status: UserStatus
}

export function UsersPage() {
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState<UserAdminItem | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [resetTarget, setResetTarget] = useState<UserAdminItem | null>(null)
  const [resetPassword, setResetPassword] = useState('')

  const [editForm, setEditForm] = useState<EditForm>({
    displayName: '',
    email: '',
    mobile: '',
    role: 'READ_ONLY',
    status: 'PENDING',
  })

  const [createForm, setCreateForm] = useState({
    username: '',
    displayName: '',
    email: '',
    mobile: '',
    password: '',
    role: 'READ_ONLY' as UserRole,
    status: 'ACTIVE' as UserStatus,
  })

  const users = useQuery({
    queryKey: ['admin-users'],
    queryFn: () => api<UserAdminItem[]>('/api/v1/admin/users'),
  })

  const updateUser = useMutation({
    mutationFn: () =>
      api<UserAdminItem>(`/api/v1/admin/users/${editing!.id}`, {
        method: 'PUT',
        body: JSON.stringify({
          ...editForm,
          email: editForm.email || null,
          mobile: editForm.mobile || null,
        }),
      }),
    onSuccess: () => {
      setEditing(null)
      queryClient.invalidateQueries({ queryKey: ['admin-users'] })
    },
  })

  const createUser = useMutation({
    mutationFn: () =>
      api<UserAdminItem>('/api/v1/admin/users', {
        method: 'POST',
        body: JSON.stringify({
          ...createForm,
          email: createForm.email || null,
          mobile: createForm.mobile || null,
        }),
      }),
    onSuccess: () => {
      setCreateOpen(false)
      setCreateForm({
        username: '',
        displayName: '',
        email: '',
        mobile: '',
        password: '',
        role: 'READ_ONLY',
        status: 'ACTIVE',
      })
      queryClient.invalidateQueries({ queryKey: ['admin-users'] })
    },
  })

  const unlockUser = useMutation({
    mutationFn: (userId: string) =>
      api<void>(`/api/v1/admin/users/${userId}/unlock`, {
        method: 'POST',
      }),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ['admin-users'] }),
  })

  const reset = useMutation({
    mutationFn: () =>
      api<void>(
        `/api/v1/admin/users/${resetTarget!.id}/reset-password`,
        {
          method: 'POST',
          body: JSON.stringify({ newPassword: resetPassword }),
        },
      ),
    onSuccess: () => {
      setResetTarget(null)
      setResetPassword('')
      queryClient.invalidateQueries({ queryKey: ['admin-users'] })
    },
  })

  const openEdit = (user: UserAdminItem) => {
    setEditing(user)
    setEditForm({
      displayName: user.displayName,
      email: user.email ?? '',
      mobile: user.mobile ?? '',
      role: user.role,
      status: user.status,
    })
  }

  return (
    <Stack spacing={3}>
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        justifyContent="space-between"
        alignItems={{ xs: 'stretch', sm: 'center' }}
        gap={2}
      >
        <div>
          <Typography variant="h4">کاربران و دسترسی‌ها</Typography>
          <Typography color="text.secondary" mt={0.5}>
            تأیید ثبت‌نام، نقش‌ها، وضعیت حساب و بازنشانی رمز عبور
          </Typography>
        </div>

        <Button
          variant="contained"
          startIcon={<AddRoundedIcon />}
          onClick={() => setCreateOpen(true)}
        >
          ایجاد کاربر
        </Button>
      </Stack>

      {users.isError && (
        <Alert severity="error">دریافت کاربران ناموفق بود.</Alert>
      )}

      <Grid container spacing={2}>
        {(users.data ?? []).map((user) => (
          <Grid key={user.id} size={{ xs: 12, md: 6, xl: 4 }}>
            <Card sx={{ height: '100%' }}>
              <CardContent>
                <Stack spacing={2}>
                  <Stack
                    direction="row"
                    justifyContent="space-between"
                    gap={1}
                  >
                    <Stack spacing={0.25}>
                      <Typography variant="h6">{user.displayName}</Typography>
                      <Typography variant="body2" color="text.secondary" dir="ltr">
                        @{user.username}
                      </Typography>
                    </Stack>

                    <Chip
                      size="small"
                      color={
                        user.status === 'ACTIVE'
                          ? 'success'
                          : user.status === 'PENDING'
                            ? 'warning'
                            : 'default'
                      }
                      label={
                        user.status === 'ACTIVE'
                          ? 'فعال'
                          : user.status === 'PENDING'
                            ? 'در انتظار'
                            : 'تعلیق'
                      }
                    />
                  </Stack>

                  <Stack direction="row" gap={1} flexWrap="wrap">
                    <Chip
                      size="small"
                      variant="outlined"
                      label={
                        roles.find((item) => item.value === user.role)?.label ??
                        user.role
                      }
                    />
                    {user.lockedUntil &&
                      new Date(user.lockedUntil) > new Date() && (
                        <Chip size="small" color="error" label="قفل موقت" />
                      )}
                  </Stack>

                  <Typography variant="body2" color="text.secondary">
                    {user.email || 'ایمیل ثبت نشده'}
                    {user.mobile ? ` · ${user.mobile}` : ''}
                  </Typography>

                  <Typography variant="caption" color="text.secondary">
                    آخرین ورود:{' '}
                    {user.lastLoginAt
                      ? new Date(user.lastLoginAt).toLocaleString('fa-IR')
                      : 'تا کنون وارد نشده'}
                  </Typography>

                  <Stack direction="row" gap={1} flexWrap="wrap">
                    <Button
                      fullWidth
                      variant="outlined"
                      startIcon={<ManageAccountsRoundedIcon />}
                      onClick={() => openEdit(user)}
                    >
                      نقش و وضعیت
                    </Button>
                    <Button
                      variant="text"
                      startIcon={<LockResetRoundedIcon />}
                      onClick={() => setResetTarget(user)}
                    >
                      رمز
                    </Button>
                    {user.lockedUntil &&
                      new Date(user.lockedUntil) > new Date() && (
                        <Button
                          color="warning"
                          variant="text"
                          disabled={unlockUser.isPending}
                          onClick={() => unlockUser.mutate(user.id)}
                        >
                          باز کردن قفل
                        </Button>
                      )}
                  </Stack>
                </Stack>
              </CardContent>
            </Card>
          </Grid>
        ))}
      </Grid>

      <Dialog
        open={Boolean(editing)}
        onClose={() => setEditing(null)}
        fullWidth
        maxWidth="sm"
      >
        <DialogTitle>ویرایش کاربر</DialogTitle>
        <DialogContent>
          <Stack spacing={2} pt={1}>
            {updateUser.isError && (
              <Alert severity="error">
                {updateUser.error instanceof Error
                  ? updateUser.error.message
                  : 'ویرایش ناموفق بود.'}
              </Alert>
            )}
            <TextField
              label="نام نمایشی"
              value={editForm.displayName}
              onChange={(e) =>
                setEditForm({ ...editForm, displayName: e.target.value })
              }
            />
            <TextField
              label="ایمیل"
              value={editForm.email}
              onChange={(e) => setEditForm({ ...editForm, email: e.target.value })}
            />
            <TextField
              label="موبایل"
              value={editForm.mobile}
              onChange={(e) =>
                setEditForm({ ...editForm, mobile: e.target.value })
              }
            />
            <TextField
              select
              label="نقش"
              value={editForm.role}
              onChange={(e) =>
                setEditForm({
                  ...editForm,
                  role: e.target.value as UserRole,
                })
              }
            >
              {roles.map((role) => (
                <MenuItem key={role.value} value={role.value}>
                  {role.label}
                </MenuItem>
              ))}
            </TextField>
            <Alert severity="info">
              <Stack spacing={0.75}>
                <span>{roleDescription[editForm.role]}</span>
                <Stack direction="row" gap={0.75} flexWrap="wrap">
                  {rolePermissions[editForm.role].map((item) => (
                    <Chip key={item} size="small" label={item} />
                  ))}
                </Stack>
              </Stack>
            </Alert>
            <TextField
              select
              label="وضعیت"
              value={editForm.status}
              onChange={(e) =>
                setEditForm({
                  ...editForm,
                  status: e.target.value as UserStatus,
                })
              }
            >
              {statuses.map((status) => (
                <MenuItem key={status.value} value={status.value}>
                  {status.label}
                </MenuItem>
              ))}
            </TextField>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditing(null)}>انصراف</Button>
          <Button
            variant="contained"
            disabled={!editForm.displayName.trim() || updateUser.isPending}
            onClick={() => updateUser.mutate()}
          >
            ذخیره
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        fullWidth
        maxWidth="sm"
      >
        <DialogTitle>ایجاد کاربر</DialogTitle>
        <DialogContent>
          <Stack spacing={2} pt={1}>
            {createUser.isError && (
              <Alert severity="error">
                {createUser.error instanceof Error
                  ? createUser.error.message
                  : 'ایجاد کاربر ناموفق بود.'}
              </Alert>
            )}
            <TextField
              label="نام کاربری"
              value={createForm.username}
              onChange={(e) =>
                setCreateForm({ ...createForm, username: e.target.value })
              }
            />
            <TextField
              label="نام نمایشی"
              value={createForm.displayName}
              onChange={(e) =>
                setCreateForm({ ...createForm, displayName: e.target.value })
              }
            />
            <TextField
              label="ایمیل"
              value={createForm.email}
              onChange={(e) =>
                setCreateForm({ ...createForm, email: e.target.value })
              }
            />
            <TextField
              label="موبایل"
              value={createForm.mobile}
              onChange={(e) =>
                setCreateForm({ ...createForm, mobile: e.target.value })
              }
            />
            <TextField
              label="رمز اولیه"
              type="password"
              value={createForm.password}
              onChange={(e) =>
                setCreateForm({ ...createForm, password: e.target.value })
              }
              helperText="حداقل ۱۰ کاراکتر و شامل حرف، عدد و نویسه خاص"
            />
            <TextField
              select
              label="نقش"
              value={createForm.role}
              onChange={(e) =>
                setCreateForm({
                  ...createForm,
                  role: e.target.value as UserRole,
                })
              }
            >
              {roles.map((role) => (
                <MenuItem key={role.value} value={role.value}>
                  {role.label}
                </MenuItem>
              ))}
            </TextField>
            <Alert severity="info">
              <Stack spacing={0.75}>
                <span>{roleDescription[createForm.role]}</span>
                <Stack direction="row" gap={0.75} flexWrap="wrap">
                  {rolePermissions[createForm.role].map((item) => (
                    <Chip key={item} size="small" label={item} />
                  ))}
                </Stack>
              </Stack>
            </Alert>
            <TextField
              select
              label="وضعیت اولیه"
              value={createForm.status}
              onChange={(e) =>
                setCreateForm({
                  ...createForm,
                  status: e.target.value as UserStatus,
                })
              }
            >
              {statuses.map((status) => (
                <MenuItem key={status.value} value={status.value}>
                  {status.label}
                </MenuItem>
              ))}
            </TextField>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCreateOpen(false)}>انصراف</Button>
          <Button
            variant="contained"
            disabled={
              !createForm.username.trim() ||
              !createForm.displayName.trim() ||
              createForm.password.length < 10 ||
              createUser.isPending
            }
            onClick={() => createUser.mutate()}
          >
            ایجاد کاربر
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog
        open={Boolean(resetTarget)}
        onClose={() => setResetTarget(null)}
        fullWidth
        maxWidth="xs"
      >
        <DialogTitle>بازنشانی رمز {resetTarget?.displayName}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} pt={1}>
            {reset.isError && (
              <Alert severity="error">
                {reset.error instanceof Error
                  ? reset.error.message
                  : 'بازنشانی رمز ناموفق بود.'}
              </Alert>
            )}
            <TextField
              label="رمز جدید"
              type="password"
              value={resetPassword}
              onChange={(e) => setResetPassword(e.target.value)}
              helperText="نشست‌های فعلی کاربر پس از تغییر رمز لغو می‌شوند."
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setResetTarget(null)}>انصراف</Button>
          <Button
            variant="contained"
            disabled={resetPassword.length < 10 || reset.isPending}
            onClick={() => reset.mutate()}
          >
            بازنشانی
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  )
}
