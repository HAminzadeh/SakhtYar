import { zodResolver } from '@hookform/resolvers/zod'
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
      sx={{ placeItems: 'center', bgcolor: 'grey.100', p: 2 }}
    >
      <Card sx={{ width: '100%', maxWidth: 440 }}>
        <CardContent sx={{ p: 4 }}>
          <Stack spacing={3}>
            <Box>
              <Typography variant="h4" fontWeight={800}>
                ساخت‌یار
              </Typography>
              <Typography color="text.secondary">
                سامانه هوشمند مشارکت در ساخت
              </Typography>
            </Box>

            {error && <Alert severity="error">{error}</Alert>}

            <Stack
              component="form"
              spacing={2}
              onSubmit={handleSubmit(submit)}
            >
              <TextField
                label="نام کاربری"
                error={Boolean(errors.username)}
                helperText={errors.username?.message}
                {...register('username')}
              />
              <TextField
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
              >
                ورود
              </Button>
            </Stack>
          </Stack>
        </CardContent>
      </Card>
    </Box>
  )
}
