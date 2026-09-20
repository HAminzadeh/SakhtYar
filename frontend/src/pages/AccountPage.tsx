import DevicesRoundedIcon from '@mui/icons-material/DevicesRounded'
import LockResetRoundedIcon from '@mui/icons-material/LockResetRounded'
import PersonRoundedIcon from '@mui/icons-material/PersonRounded'
import {
  Alert,
  Button,
  Card,
  CardContent,
  Chip,
  Grid,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { api } from '../api/client'
import type { AuthSessionItem, Me } from '../api/types'
import { useAuth } from '../auth/AuthProvider'

export function AccountPage() {
  const { user, refresh } = useAuth()
  const queryClient = useQueryClient()
  const [displayName, setDisplayName] = useState(user?.displayName ?? '')
  const [email, setEmail] = useState(user?.email ?? '')
  const [mobile, setMobile] = useState(user?.mobile ?? '')
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')

  useEffect(() => {
    setDisplayName(user?.displayName ?? '')
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
        body: JSON.stringify({ currentPassword, newPassword }),
      }),
    onSuccess: () => window.location.assign('/login'),
  })

  const revokeSession = useMutation({
    mutationFn: (id: string) =>
      api<void>(`/api/v1/auth/sessions/${id}`, { method: 'DELETE' }),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ['auth-sessions'] }),
  })

  const revokeAll = useMutation({
    mutationFn: () => api<void>('/api/v1/auth/sessions', { method: 'DELETE' }),
    onSuccess: () => window.location.assign('/login'),
  })

  return (
    <Stack spacing={2.5}>
      <div>
        <Typography variant="h4">حساب کاربری و امنیت</Typography>
        <Typography color="text.secondary" mt={0.5}>
          پروفایل، رمز عبور و دستگاه‌های متصل
        </Typography>
      </div>

      <Card>
        <CardContent sx={{ p: { xs: 2, md: 3 } }}>
          <Stack spacing={2.5}>
            <Stack direction="row" spacing={1} alignItems="center">
              <PersonRoundedIcon color="primary" />
              <Typography variant="h6">پروفایل</Typography>
            </Stack>

            {updateProfile.isSuccess && (
              <Alert severity="success">پروفایل ذخیره شد.</Alert>
            )}
            {updateProfile.isError && (
              <Alert severity="error">
                {updateProfile.error instanceof Error
                  ? updateProfile.error.message
                  : 'ذخیره پروفایل ناموفق بود.'}
              </Alert>
            )}

            <Grid container spacing={2}>
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField fullWidth label="نام کاربری" value={user?.username ?? ''} disabled />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField fullWidth label="نام نمایشی" value={displayName} onChange={(e) => setDisplayName(e.target.value)} />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField fullWidth label="ایمیل" value={email} onChange={(e) => setEmail(e.target.value)} />
              </Grid>
              <Grid size={{ xs: 12, md: 6 }}>
                <TextField fullWidth label="موبایل" value={mobile} onChange={(e) => setMobile(e.target.value)} />
              </Grid>
            </Grid>

            <Stack direction="row" gap={1} flexWrap="wrap">
              <Chip label={`نقش: ${user?.role ?? '-'}`} />
              <Chip color="success" variant="outlined" label={`وضعیت: ${user?.status ?? '-'}`} />
            </Stack>

            <Stack direction="row" justifyContent="flex-end">
              <Button
                variant="contained"
                disabled={!displayName.trim() || updateProfile.isPending}
                onClick={() => updateProfile.mutate()}
              >
                ذخیره پروفایل
              </Button>
            </Stack>
          </Stack>
        </CardContent>
      </Card>

      <Card>
        <CardContent sx={{ p: { xs: 2, md: 3 } }}>
          <Stack spacing={2.5}>
            <Stack direction="row" spacing={1} alignItems="center">
              <LockResetRoundedIcon color="primary" />
              <Typography variant="h6">تغییر رمز عبور</Typography>
            </Stack>

            {changePassword.isError && (
              <Alert severity="error">
                {changePassword.error instanceof Error
                  ? changePassword.error.message
                  : 'تغییر رمز عبور ناموفق بود.'}
              </Alert>
            )}

            <Grid container spacing={2}>
              <Grid size={{ xs: 12, md: 4 }}>
                <TextField fullWidth type="password" label="رمز فعلی" value={currentPassword} onChange={(e) => setCurrentPassword(e.target.value)} />
              </Grid>
              <Grid size={{ xs: 12, md: 4 }}>
                <TextField fullWidth type="password" label="رمز جدید" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} helperText="حداقل ۱۰ کاراکتر، شامل حرف، عدد و نویسه خاص" />
              </Grid>
              <Grid size={{ xs: 12, md: 4 }}>
                <TextField
                  fullWidth
                  type="password"
                  label="تکرار رمز جدید"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  error={Boolean(confirmPassword) && newPassword !== confirmPassword}
                />
              </Grid>
            </Grid>

            <Stack direction="row" justifyContent="flex-end">
              <Button
                variant="contained"
                disabled={
                  !currentPassword ||
                  newPassword.length < 10 ||
                  newPassword !== confirmPassword ||
                  changePassword.isPending
                }
                onClick={() => changePassword.mutate()}
              >
                تغییر رمز و خروج از همه دستگاه‌ها
              </Button>
            </Stack>
          </Stack>
        </CardContent>
      </Card>

      <Card>
        <CardContent sx={{ p: { xs: 2, md: 3 } }}>
          <Stack spacing={2}>
            <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" gap={1}>
              <Stack direction="row" spacing={1} alignItems="center">
                <DevicesRoundedIcon color="primary" />
                <div>
                  <Typography variant="h6">نشست‌ها و دستگاه‌ها</Typography>
                  <Typography variant="body2" color="text.secondary">
                    نشست‌های Web و Mobile متصل به حساب
                  </Typography>
                </div>
              </Stack>

              <Button
                color="error"
                variant="outlined"
                disabled={revokeAll.isPending}
                onClick={() => revokeAll.mutate()}
              >
                خروج از همه دستگاه‌ها
              </Button>
            </Stack>

            {(sessions.data ?? []).map((session) => (
              <Stack
                key={session.id}
                direction={{ xs: 'column', md: 'row' }}
                justifyContent="space-between"
                alignItems={{ xs: 'stretch', md: 'center' }}
                gap={1.5}
                py={1}
                borderTop="1px solid"
                borderColor="divider"
              >
                <Stack spacing={0.25}>
                  <Stack direction="row" gap={1} alignItems="center">
                    <Typography fontWeight={700}>
                      {session.deviceName ||
                        (session.clientType === 'MOBILE' ? 'موبایل' : 'مرورگر وب')}
                    </Typography>
                    <Chip
                      size="small"
                      color={session.active ? 'success' : 'default'}
                      label={session.active ? 'فعال' : 'پایان‌یافته'}
                    />
                  </Stack>
                  <Typography variant="body2" color="text.secondary">
                    {session.ipAddress || 'IP نامشخص'} · آخرین استفاده:{' '}
                    {new Date(session.lastUsedAt).toLocaleString('fa-IR')}
                  </Typography>
                </Stack>

                {session.active && (
                  <Button
                    size="small"
                    color="error"
                    onClick={() => revokeSession.mutate(session.id)}
                  >
                    پایان دادن به نشست
                  </Button>
                )}
              </Stack>
            ))}
          </Stack>
        </CardContent>
      </Card>
    </Stack>
  )
}
