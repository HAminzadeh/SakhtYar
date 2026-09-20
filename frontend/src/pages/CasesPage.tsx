import AddRoundedIcon from '@mui/icons-material/AddRounded'
import ArrowBackRoundedIcon from '@mui/icons-material/ArrowBackRounded'
import LocationOnRoundedIcon from '@mui/icons-material/LocationOnRounded'
import SquareFootRoundedIcon from '@mui/icons-material/SquareFootRounded'
import {
  Alert,
  Box,
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
import { useAuth } from '../auth/AuthProvider'

type CreateCase = {
  title: string
  city: string
  district: string
  address: string
  landAreaM2: number | null
}

function statusLabel(status: CaseItem['status']) {
  if (status === 'ACTIVE') return 'فعال'
  if (status === 'ARCHIVED') return 'بایگانی'
  return 'پیش‌نویس'
}

export function CasesPage() {
  const navigate = useNavigate()
  const { hasPermission } = useAuth()
  const canWrite = hasPermission('CASE_WRITE')
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
    <Stack spacing={{ xs: 2.5, md: 3.5 }}>
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        justifyContent="space-between"
        alignItems={{ xs: 'stretch', sm: 'center' }}
        gap={2}
      >
        <Box>
          <Typography
            variant="h4"
            sx={{ fontSize: { xs: '1.55rem', sm: '2rem' } }}
          >
            پرونده‌های مشارکت
          </Typography>
          <Typography color="text.secondary" mt={0.5}>
            مدیریت پرونده‌ها، ملک‌ها و فرآیندهای تحلیل
          </Typography>
        </Box>

        {canWrite && (
          <Button
            variant="contained"
            size="large"
            startIcon={<AddRoundedIcon />}
            onClick={() => setOpen(true)}
            sx={{ alignSelf: { xs: 'stretch', sm: 'auto' } }}
          >
            پرونده جدید
          </Button>
        )}
      </Stack>

      {cases.isError && (
        <Alert severity="error">دریافت پرونده‌ها ناموفق بود.</Alert>
      )}

      <Grid container spacing={{ xs: 1.5, sm: 2 }}>
        {cases.data?.map((item) => (
          <Grid key={item.id} size={{ xs: 12, md: 6, xl: 4 }}>
            <Card
              sx={{
                height: '100%',
                transition: 'transform .18s ease, box-shadow .18s ease',
                '&:hover': {
                  transform: { md: 'translateY(-2px)' },
                  boxShadow: { md: '0 10px 28px rgba(16,24,40,.08)' },
                },
              }}
            >
              <CardActionArea
                onClick={() => navigate(`/cases/${item.id}`)}
                sx={{ height: '100%' }}
              >
                <CardContent sx={{ p: { xs: 2, sm: 2.5 } }}>
                  <Stack spacing={2}>
                    <Stack
                      direction="row"
                      justifyContent="space-between"
                      gap={1}
                    >
                      <Box>
                        <Typography variant="h6">{item.title}</Typography>
                        <Stack
                          direction="row"
                          spacing={0.5}
                          alignItems="center"
                          mt={0.75}
                        >
                          <LocationOnRoundedIcon
                            fontSize="small"
                            color="action"
                          />
                          <Typography
                            variant="body2"
                            color="text.secondary"
                          >
                            {[item.city, item.district]
                              .filter(Boolean)
                              .join('، ') || 'موقعیت ثبت نشده'}
                          </Typography>
                        </Stack>
                      </Box>

                      <Chip
                        size="small"
                        label={statusLabel(item.status)}
                        color={item.status === 'ACTIVE' ? 'success' : 'default'}
                        variant={item.status === 'ACTIVE' ? 'filled' : 'outlined'}
                      />
                    </Stack>

                    <Stack
                      direction="row"
                      justifyContent="space-between"
                      alignItems="center"
                      pt={1.5}
                      borderTop="1px solid"
                      borderColor="divider"
                    >
                      <Stack direction="row" spacing={0.75} alignItems="center">
                        <SquareFootRoundedIcon
                          fontSize="small"
                          color="action"
                        />
                        <Typography variant="body2" fontWeight={700}>
                          {item.landAreaM2
                            ? `${item.landAreaM2.toLocaleString('fa-IR')} متر مربع`
                            : 'مساحت ثبت نشده'}
                        </Typography>
                      </Stack>

                      <ArrowBackRoundedIcon
                        fontSize="small"
                        color="primary"
                      />
                    </Stack>
                  </Stack>
                </CardContent>
              </CardActionArea>
            </Card>
          </Grid>
        ))}
      </Grid>

      <Dialog
        open={open && canWrite}
        onClose={() => setOpen(false)}
        fullWidth
        maxWidth="sm"
        fullScreen={false}
      >
        <DialogTitle>ایجاد پرونده جدید</DialogTitle>

        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            <TextField
              fullWidth
              label="عنوان پرونده"
              value={form.title}
              onChange={(e) => setForm({ ...form, title: e.target.value })}
            />
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <TextField
                fullWidth
                label="شهر"
                value={form.city}
                onChange={(e) => setForm({ ...form, city: e.target.value })}
              />
              <TextField
                fullWidth
                label="منطقه"
                value={form.district}
                onChange={(e) =>
                  setForm({ ...form, district: e.target.value })
                }
              />
            </Stack>
            <TextField
              fullWidth
              label="آدرس"
              multiline
              minRows={2}
              value={form.address}
              onChange={(e) => setForm({ ...form, address: e.target.value })}
            />
            <TextField
              fullWidth
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

        <DialogActions sx={{ px: 3, pb: 2.5 }}>
          <Button onClick={() => setOpen(false)}>انصراف</Button>
          <Button
            variant="contained"
            disabled={!form.title.trim() || createCase.isPending}
            onClick={() => createCase.mutate(form)}
          >
            ایجاد پرونده
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  )
}
