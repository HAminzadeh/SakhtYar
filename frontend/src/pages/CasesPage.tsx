import AddRoundedIcon from '@mui/icons-material/AddRounded'
import {
  Alert,
  Button,
  Card,
  CardActionArea,
  CardContent,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api/client'
import type { CaseItem } from '../api/types'

type CreateCase = {
  title: string
  city: string
  district: string
  address: string
  landAreaM2: number | null
}

export function CasesPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState<CreateCase>({
    title: '',
    city: '',
    district: '',
    address: '',
    landAreaM2: null,
  })

  const cases = useQuery({
    queryKey: ['cases'],
    queryFn: () => api<CaseItem[]>('/api/v1/cases'),
  })

  const createCase = useMutation({
    mutationFn: (data: CreateCase) =>
      api<CaseItem>('/api/v1/cases', {
        method: 'POST',
        body: JSON.stringify({
          ...data,
          status: 'DRAFT',
          description: '',
        }),
      }),
    onSuccess: (created) => {
      queryClient.invalidateQueries({ queryKey: ['cases'] })
      setOpen(false)
      navigate(`/cases/${created.id}`)
    },
  })

  return (
    <Stack spacing={3}>
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        justifyContent="space-between"
        alignItems={{ xs: 'stretch', sm: 'center' }}
        gap={2}
      >
        <div>
          <Typography variant="h4" fontWeight={800}>
            پرونده‌های مشارکت
          </Typography>
          <Typography color="text.secondary">
            ورودی اصلی چرخه مشارکت در ساخت
          </Typography>
        </div>

        <Button
          variant="contained"
          startIcon={<AddRoundedIcon />}
          onClick={() => setOpen(true)}
        >
          پرونده جدید
        </Button>
      </Stack>

      {cases.isError && (
        <Alert severity="error">دریافت پرونده‌ها ناموفق بود.</Alert>
      )}

      <Grid container spacing={2}>
        {cases.data?.map((item) => (
          <Grid key={item.id} size={{ xs: 12, md: 6, lg: 4 }}>
            <Card variant="outlined">
              <CardActionArea onClick={() => navigate(`/cases/${item.id}`)}>
                <CardContent>
                  <Stack spacing={1.5}>
                    <Stack direction="row" justifyContent="space-between">
                      <Typography variant="h6" fontWeight={700}>
                        {item.title}
                      </Typography>
                      <Chip
                        size="small"
                        label={
                          item.status === 'DRAFT'
                            ? 'پیش‌نویس'
                            : item.status === 'ACTIVE'
                              ? 'فعال'
                              : 'بایگانی'
                        }
                      />
                    </Stack>

                    <Typography color="text.secondary">
                      {[item.city, item.district]
                        .filter(Boolean)
                        .join(' - ') || 'موقعیت ثبت نشده'}
                    </Typography>

                    <Typography>
                      {item.landAreaM2
                        ? `${item.landAreaM2.toLocaleString('fa-IR')} متر مربع`
                        : 'مساحت ثبت نشده'}
                    </Typography>
                  </Stack>
                </CardContent>
              </CardActionArea>
            </Card>
          </Grid>
        ))}
      </Grid>

      <Dialog
        open={open}
        onClose={() => setOpen(false)}
        fullWidth
        maxWidth="sm"
      >
        <DialogTitle>ایجاد پرونده جدید</DialogTitle>

        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <TextField
              label="عنوان پرونده"
              value={form.title}
              onChange={(e) => setForm({ ...form, title: e.target.value })}
            />
            <TextField
              label="شهر"
              value={form.city}
              onChange={(e) => setForm({ ...form, city: e.target.value })}
            />
            <TextField
              label="منطقه"
              value={form.district}
              onChange={(e) =>
                setForm({ ...form, district: e.target.value })
              }
            />
            <TextField
              label="آدرس"
              multiline
              minRows={2}
              value={form.address}
              onChange={(e) => setForm({ ...form, address: e.target.value })}
            />
            <TextField
              label="مساحت زمین (متر مربع)"
              type="number"
              value={form.landAreaM2 ?? ''}
              onChange={(e) =>
                setForm({
                  ...form,
                  landAreaM2:
                    e.target.value === '' ? null : Number(e.target.value),
                })
              }
            />
          </Stack>
        </DialogContent>

        <DialogActions>
          <Button onClick={() => setOpen(false)}>انصراف</Button>
          <Button
            variant="contained"
            disabled={!form.title.trim() || createCase.isPending}
            onClick={() => createCase.mutate(form)}
          >
            ایجاد
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  )
}
