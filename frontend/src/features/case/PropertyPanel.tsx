import SaveRoundedIcon from '@mui/icons-material/SaveRounded'
import {
  Alert,
  Button,
  Card,
  CardContent,
  Grid,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { ApiError, api } from '../../api/client'
import type { CaseItem, PropertyItem } from '../../api/types'

type PropertyForm = {
  province: string
  city: string
  district: string
  neighborhood: string
  address: string
  landAreaM2: string
  registryMainNo: string
  registrySubNo: string
  registrySection: string
  postalCode: string
  latitude: string
  longitude: string
}

function fromCase(item: CaseItem): PropertyForm {
  return {
    province: '',
    city: item.city ?? '',
    district: item.district ?? '',
    neighborhood: '',
    address: item.address ?? '',
    landAreaM2:
      item.landAreaM2 == null ? '' : String(item.landAreaM2),
    registryMainNo: '',
    registrySubNo: '',
    registrySection: '',
    postalCode: '',
    latitude: '',
    longitude: '',
  }
}

function fromProperty(item: PropertyItem): PropertyForm {
  return {
    province: item.province ?? '',
    city: item.city ?? '',
    district: item.district ?? '',
    neighborhood: item.neighborhood ?? '',
    address: item.address ?? '',
    landAreaM2:
      item.landAreaM2 == null ? '' : String(item.landAreaM2),
    registryMainNo: item.registryMainNo ?? '',
    registrySubNo: item.registrySubNo ?? '',
    registrySection: item.registrySection ?? '',
    postalCode: item.postalCode ?? '',
    latitude: item.latitude == null ? '' : String(item.latitude),
    longitude: item.longitude == null ? '' : String(item.longitude),
  }
}

function nullableNumber(value: string) {
  const trimmed = value.trim()
  return trimmed === '' ? null : Number(trimmed)
}

export function PropertyPanel({
  caseId,
  caseItem,
}: {
  caseId: string
  caseItem: CaseItem
}) {
  const queryClient = useQueryClient()
  const [form, setForm] = useState<PropertyForm>(() => fromCase(caseItem))
  const [saved, setSaved] = useState(false)

  const property = useQuery({
    queryKey: ['property', caseId],
    queryFn: async (): Promise<PropertyItem | null> => {
      try {
        return await api<PropertyItem>(
          `/api/v1/cases/${caseId}/property`,
        )
      } catch (error) {
        if (error instanceof ApiError && error.status === 404) {
          return null
        }
        throw error
      }
    },
    retry: false,
  })

  useEffect(() => {
    if (property.data) {
      setForm(fromProperty(property.data))
    } else if (property.data === null) {
      setForm(fromCase(caseItem))
    }
  }, [property.data, caseItem])

  const save = useMutation({
    mutationFn: () =>
      api<PropertyItem>(`/api/v1/cases/${caseId}/property`, {
        method: 'PUT',
        body: JSON.stringify({
          province: form.province.trim() || null,
          city: form.city.trim() || null,
          district: form.district.trim() || null,
          neighborhood: form.neighborhood.trim() || null,
          address: form.address.trim() || null,
          landAreaM2: nullableNumber(form.landAreaM2),
          registryMainNo: form.registryMainNo.trim() || null,
          registrySubNo: form.registrySubNo.trim() || null,
          registrySection: form.registrySection.trim() || null,
          postalCode: form.postalCode.trim() || null,
          latitude: nullableNumber(form.latitude),
          longitude: nullableNumber(form.longitude),
        }),
      }),
    onSuccess: (savedProperty) => {
      setSaved(true)
      setForm(fromProperty(savedProperty))
      queryClient.setQueryData(['property', caseId], savedProperty)
      queryClient.invalidateQueries({ queryKey: ['case', caseId] })
      queryClient.invalidateQueries({ queryKey: ['cases'] })
      queryClient.invalidateQueries({ queryKey: ['owners', caseId] })
      window.setTimeout(() => setSaved(false), 2500)
    },
  })

  const setField = (key: keyof PropertyForm, value: string) => {
    setSaved(false)
    setForm((current) => ({ ...current, [key]: value }))
  }

  const landArea = nullableNumber(form.landAreaM2)
  const latitude = nullableNumber(form.latitude)
  const longitude = nullableNumber(form.longitude)

  const invalidNumbers =
    (landArea != null && (!Number.isFinite(landArea) || landArea <= 0)) ||
    (latitude != null &&
      (!Number.isFinite(latitude) || latitude < -90 || latitude > 90)) ||
    (longitude != null &&
      (!Number.isFinite(longitude) || longitude < -180 || longitude > 180))

  return (
    <Card variant="outlined">
      <CardContent>
        <Stack spacing={3}>
          <div>
            <Typography variant="h6" fontWeight={700}>
              مشخصات ملک
            </Typography>
            <Typography color="text.secondary" variant="body2">
              اطلاعات ثبتی، موقعیت و مشخصات پایه ملک این پرونده
            </Typography>
          </div>

          {property.isError && (
            <Alert severity="error">
              دریافت مشخصات ملک ناموفق بود.
            </Alert>
          )}

          {property.data === null && (
            <Alert severity="info">
              برای این پرونده هنوز رکورد ملک ایجاد نشده است. با ذخیره فرم،
              ملک ایجاد می‌شود.
            </Alert>
          )}

          {saved && (
            <Alert severity="success">
              مشخصات ملک با موفقیت ذخیره شد.
            </Alert>
          )}

          {save.isError && (
            <Alert severity="error">
              {save.error instanceof Error
                ? save.error.message
                : 'ذخیره مشخصات ملک ناموفق بود.'}
            </Alert>
          )}

          <Grid container spacing={2}>
            <Grid size={{ xs: 12, sm: 6, md: 4 }}>
              <TextField
                fullWidth
                label="استان"
                value={form.province}
                onChange={(e) => setField('province', e.target.value)}
              />
            </Grid>

            <Grid size={{ xs: 12, sm: 6, md: 4 }}>
              <TextField
                fullWidth
                label="شهر"
                value={form.city}
                onChange={(e) => setField('city', e.target.value)}
              />
            </Grid>

            <Grid size={{ xs: 12, sm: 6, md: 4 }}>
              <TextField
                fullWidth
                label="منطقه"
                value={form.district}
                onChange={(e) => setField('district', e.target.value)}
              />
            </Grid>

            <Grid size={{ xs: 12, sm: 6, md: 4 }}>
              <TextField
                fullWidth
                label="محله"
                value={form.neighborhood}
                onChange={(e) =>
                  setField('neighborhood', e.target.value)
                }
              />
            </Grid>

            <Grid size={{ xs: 12, sm: 6, md: 4 }}>
              <TextField
                fullWidth
                label="مساحت زمین (متر مربع)"
                type="number"
                value={form.landAreaM2}
                onChange={(e) =>
                  setField('landAreaM2', e.target.value)
                }
                slotProps={{
                  htmlInput: { min: 0.01, step: 0.01 },
                }}
              />
            </Grid>

            <Grid size={{ xs: 12, sm: 6, md: 4 }}>
              <TextField
                fullWidth
                label="کد پستی"
                value={form.postalCode}
                onChange={(e) => setField('postalCode', e.target.value)}
              />
            </Grid>

            <Grid size={{ xs: 12, sm: 6, md: 4 }}>
              <TextField
                fullWidth
                label="پلاک اصلی"
                value={form.registryMainNo}
                onChange={(e) =>
                  setField('registryMainNo', e.target.value)
                }
              />
            </Grid>

            <Grid size={{ xs: 12, sm: 6, md: 4 }}>
              <TextField
                fullWidth
                label="پلاک فرعی"
                value={form.registrySubNo}
                onChange={(e) =>
                  setField('registrySubNo', e.target.value)
                }
              />
            </Grid>

            <Grid size={{ xs: 12, sm: 6, md: 4 }}>
              <TextField
                fullWidth
                label="بخش ثبتی"
                value={form.registrySection}
                onChange={(e) =>
                  setField('registrySection', e.target.value)
                }
              />
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                label="عرض جغرافیایی"
                type="number"
                value={form.latitude}
                onChange={(e) => setField('latitude', e.target.value)}
                helperText="فعلاً اختیاری؛ در مرحله نقشه به صورت خودکار پر می‌شود."
                slotProps={{
                  htmlInput: { min: -90, max: 90, step: 0.0000001 },
                }}
              />
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <TextField
                fullWidth
                label="طول جغرافیایی"
                type="number"
                value={form.longitude}
                onChange={(e) => setField('longitude', e.target.value)}
                helperText="فعلاً اختیاری؛ در مرحله نقشه به صورت خودکار پر می‌شود."
                slotProps={{
                  htmlInput: { min: -180, max: 180, step: 0.0000001 },
                }}
              />
            </Grid>

            <Grid size={12}>
              <TextField
                fullWidth
                label="آدرس کامل"
                multiline
                minRows={3}
                value={form.address}
                onChange={(e) => setField('address', e.target.value)}
              />
            </Grid>
          </Grid>

          {invalidNumbers && (
            <Alert severity="warning">
              مقدار مساحت یا مختصات جغرافیایی معتبر نیست.
            </Alert>
          )}

          <Stack direction="row" justifyContent="flex-end">
            <Button
              variant="contained"
              startIcon={<SaveRoundedIcon />}
              onClick={() => save.mutate()}
              disabled={save.isPending || invalidNumbers}
            >
              {save.isPending
                ? 'در حال ذخیره...'
                : 'ذخیره مشخصات ملک'}
            </Button>
          </Stack>
        </Stack>
      </CardContent>
    </Card>
  )
}
