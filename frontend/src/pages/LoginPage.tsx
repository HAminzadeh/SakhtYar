import { zodResolver } from '@hookform/resolvers/zod'
import ConstructionRoundedIcon from '@mui/icons-material/ConstructionRounded'
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
import { Navigate, useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { useAuth } from '../auth/AuthProvider'

const schema = z.object({
  username: z.string().min(1, 'نام کاربری الزامی است'),
  password: z.string().min(1, 'رمز عبور الزامی است'),
})

type FormValues = z.infer<typeof schema>

export function LoginPage() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
  })

  if (user) {
    return <Navigate to="/cases" replace />
  }

  const submit = async (values: FormValues) => {
    setError(null)
    try {
      await login(values.username, values.password)
      navigate('/cases')
    } catch {
      setError('نام کاربری یا رمز عبور صحیح نیست.')
    }
  }

  return (
    <Box
      minHeight="100vh"
      display="grid"
      sx={{
        placeItems: 'center',
        p: { xs: 1.5, sm: 3 },
        background:
          'radial-gradient(circle at 80% 0%, #DBEAFE 0%, transparent 34%), #F6F8FC',
      }}
    >
      <Card
        sx={{
          width: '100%',
          maxWidth: 440,
          borderRadius: 4,
          boxShadow: '0 24px 60px rgba(16,24,40,.10)',
        }}
      >
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
                <ConstructionRoundedIcon />
              </Box>
              <Box>
                <Typography variant="h5">ساخت‌یار</Typography>
                <Typography variant="body2" color="text.secondary">
                  سامانه هوشمند مشارکت در ساخت
                </Typography>
              </Box>
            </Stack>

            {error && <Alert severity="error">{error}</Alert>}

            <Stack
              component="form"
              spacing={2}
              onSubmit={handleSubmit(submit)}
            >
              <TextField
                fullWidth
                label="نام کاربری"
                error={Boolean(errors.username)}
                helperText={errors.username?.message}
                {...register('username')}
              />
              <TextField
                fullWidth
                label="رمز عبور"
                type="password"
                error={Boolean(errors.password)}
                helperText={errors.password?.message}
                {...register('password')}
              />

              <Button
                variant="contained"
                size="large"
                type="submit"
                disabled={isSubmitting}
                sx={{ minHeight: 48 }}
              >
                ورود به ساخت‌یار
              </Button>
            </Stack>
          </Stack>
        </CardContent>
      </Card>
    </Box>
  )
}
