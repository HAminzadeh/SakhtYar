import { zodResolver } from '@hookform/resolvers/zod'
import HowToRegRoundedIcon from '@mui/icons-material/HowToRegRounded'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, Navigate } from 'react-router-dom'
import { z } from 'zod'
import { api } from '../api/client'
import type { RegistrationResponse } from '../api/types'
import { useAuth } from '../auth/AuthProvider'

const schema = z
  .object({
    username: z.string().min(3, 'حداقل ۳ کاراکتر').max(100),
    displayName: z.string().min(2, 'نام نمایشی الزامی است'),
    email: z.string().email('ایمیل معتبر نیست').or(z.literal('')),
    mobile: z.string().max(30),
    password: z.string().min(10, 'حداقل ۱۰ کاراکتر'),
    confirmPassword: z.string(),
  })
  .refine((value) => value.password === value.confirmPassword, {
    path: ['confirmPassword'],
    message: 'تکرار رمز عبور یکسان نیست',
  })

type FormValues = z.infer<typeof schema>

export function RegisterPage() {
  const { user } = useAuth()
  const [result, setResult] = useState<RegistrationResponse | null>(null)
  const [serverError, setServerError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      username: '',
      displayName: '',
      email: '',
      mobile: '',
      password: '',
      confirmPassword: '',
    },
  })

  if (user) return <Navigate to="/cases" replace />

  const submit = async (values: FormValues) => {
    setServerError(null)
    try {
      setResult(
        await api<RegistrationResponse>('/api/v1/auth/register', {
          method: 'POST',
          body: JSON.stringify({
            username: values.username,
            displayName: values.displayName,
            email: values.email || null,
            mobile: values.mobile || null,
            password: values.password,
          }),
        }),
      )
    } catch (error) {
      setServerError(
        error instanceof Error ? error.message : 'ثبت‌نام ناموفق بود.',
      )
    }
  }

  return (
    <Box
      minHeight="100vh"
      display="grid"
      sx={{ placeItems: 'center', p: { xs: 1.5, sm: 3 }, bgcolor: 'background.default' }}
    >
      <Card sx={{ width: '100%', maxWidth: 560, borderRadius: 4 }}>
        <CardContent sx={{ p: { xs: 2.5, sm: 4 } }}>
          <Stack spacing={3}>
            <Stack direction="row" spacing={1.5} alignItems="center">
              <Box
                sx={{
                  width: 48,
                  height: 48,
                  borderRadius: 3,
                  bgcolor: 'primary.main',
                  color: 'primary.contrastText',
                  display: 'grid',
                  placeItems: 'center',
                }}
              >
                <HowToRegRoundedIcon />
              </Box>
              <Box>
                <Typography variant="h5">ثبت‌نام در ساخت‌یار</Typography>
                <Typography variant="body2" color="text.secondary">
                  حساب جدید بعد از تأیید مدیر سیستم فعال می‌شود.
                </Typography>
              </Box>
            </Stack>

            {serverError && <Alert severity="error">{serverError}</Alert>}

            {result ? (
              <Stack spacing={2}>
                <Alert severity="success">{result.message}</Alert>
                <Button component={Link} to="/login" variant="contained">
                  رفتن به صفحه ورود
                </Button>
              </Stack>
            ) : (
              <Stack component="form" spacing={2} onSubmit={handleSubmit(submit)}>
                <TextField
                  label="نام کاربری"
                  error={Boolean(errors.username)}
                  helperText={
                    errors.username?.message ??
                    'بدون فاصله؛ حروف، عدد، نقطه، خط تیره و زیرخط مجاز است.'
                  }
                  {...register('username')}
                />
                <TextField
                  label="نام نمایشی"
                  error={Boolean(errors.displayName)}
                  helperText={errors.displayName?.message}
                  {...register('displayName')}
                />
                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
                  <TextField
                    fullWidth
                    label="ایمیل"
                    error={Boolean(errors.email)}
                    helperText={errors.email?.message}
                    {...register('email')}
                  />
                  <TextField fullWidth label="موبایل" {...register('mobile')} />
                </Stack>
                <TextField
                  label="رمز عبور"
                  type="password"
                  error={Boolean(errors.password)}
                  helperText={
                    errors.password?.message ??
                    'حداقل ۱۰ کاراکتر و شامل حرف، عدد و نویسه خاص'
                  }
                  {...register('password')}
                />
                <TextField
                  label="تکرار رمز عبور"
                  type="password"
                  error={Boolean(errors.confirmPassword)}
                  helperText={errors.confirmPassword?.message}
                  {...register('confirmPassword')}
                />
                <Button type="submit" variant="contained" size="large" disabled={isSubmitting}>
                  ثبت درخواست عضویت
                </Button>
                <Button component={Link} to="/login">
                  قبلاً حساب دارید؟ ورود
                </Button>
              </Stack>
            )}
          </Stack>
        </CardContent>
      </Card>
    </Box>
  )
}
