import DescriptionRoundedIcon from '@mui/icons-material/DescriptionRounded'
import GroupsRoundedIcon from '@mui/icons-material/GroupsRounded'
import HomeWorkRoundedIcon from '@mui/icons-material/HomeWorkRounded'
import PercentRoundedIcon from '@mui/icons-material/PercentRounded'
import {
  Alert,
  Box,
  Card,
  CardContent,
  Grid,
  Stack,
  Typography,
} from '@mui/material'
import { useQuery } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { ApiError, api } from '../../api/client'
import type {
  CaseItem,
  DocumentItem,
  OwnerItem,
  PropertyItem,
} from '../../api/types'

function sharePercent(owners: OwnerItem[]) {
  return owners.reduce((sum, owner) => {
    if (!owner.ownershipDenominator) return sum
    return (
      sum +
      (owner.ownershipNumerator / owner.ownershipDenominator) * 100
    )
  }, 0)
}

function faNumber(value: number, maximumFractionDigits = 2) {
  return new Intl.NumberFormat('fa-IR', {
    maximumFractionDigits,
  }).format(value)
}

function StatCard({
  title,
  value,
  subtitle,
  icon,
}: {
  title: string
  value: string
  subtitle?: string
  icon: ReactNode
}) {
  return (
    <Card variant="outlined" sx={{ height: '100%' }}>
      <CardContent>
        <Stack direction="row" spacing={2} alignItems="center">
          <Box
            sx={{
              width: 48,
              height: 48,
              borderRadius: 2,
              bgcolor: 'primary.50',
              color: 'primary.main',
              display: 'grid',
              placeItems: 'center',
              flexShrink: 0,
            }}
          >
            {icon}
          </Box>

          <div>
            <Typography variant="body2" color="text.secondary">
              {title}
            </Typography>
            <Typography variant="h5" fontWeight={800}>
              {value}
            </Typography>
            {subtitle && (
              <Typography variant="caption" color="text.secondary">
                {subtitle}
              </Typography>
            )}
          </div>
        </Stack>
      </CardContent>
    </Card>
  )
}

export function CaseOverview({
  caseId,
  caseItem,
}: {
  caseId: string
  caseItem: CaseItem
}) {
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

  const owners = useQuery({
    queryKey: ['owners', caseId],
    queryFn: async (): Promise<OwnerItem[]> => {
      try {
        return await api<OwnerItem[]>(
          `/api/v1/cases/${caseId}/owners`,
        )
      } catch (error) {
        if (error instanceof ApiError && error.status === 404) {
          return []
        }
        throw error
      }
    },
    retry: false,
  })

  const documents = useQuery({
    queryKey: ['documents', caseId],
    queryFn: () =>
      api<DocumentItem[]>(`/api/v1/cases/${caseId}/documents`),
  })

  const ownerItems = owners.data ?? []
  const ownership = sharePercent(ownerItems)
  const primaryOwner = ownerItems.find((owner) => owner.primaryContact)
  const area =
    property.data?.landAreaM2 ??
    caseItem.landAreaM2 ??
    null

  const anyError =
    property.isError || owners.isError || documents.isError

  return (
    <Stack spacing={3}>
      {anyError && (
        <Alert severity="warning">
          بخشی از اطلاعات خلاصه پرونده دریافت نشد.
        </Alert>
      )}

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
          <StatCard
            title="مساحت زمین"
            value={
              area == null
                ? 'ثبت نشده'
                : `${faNumber(area)} متر مربع`
            }
            icon={<HomeWorkRoundedIcon />}
          />
        </Grid>

        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
          <StatCard
            title="مالکین"
            value={`${ownerItems.length.toLocaleString('fa-IR')} نفر`}
            subtitle={
              primaryOwner
                ? `رابط: ${primaryOwner.firstName} ${primaryOwner.lastName}`
                : 'رابط اصلی تعیین نشده'
            }
            icon={<GroupsRoundedIcon />}
          />
        </Grid>

        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
          <StatCard
            title="سهم ثبت‌شده"
            value={`${faNumber(ownership)}٪`}
            subtitle={
              ownership < 99.999
                ? `${faNumber(Math.max(0, 100 - ownership))}٪ باقی‌مانده`
                : 'مالکیت کامل ثبت شده'
            }
            icon={<PercentRoundedIcon />}
          />
        </Grid>

        <Grid size={{ xs: 12, sm: 6, lg: 3 }}>
          <StatCard
            title="مدارک"
            value={`${(documents.data?.length ?? 0).toLocaleString('fa-IR')} فایل`}
            icon={<DescriptionRoundedIcon />}
          />
        </Grid>
      </Grid>

      <Card variant="outlined">
        <CardContent>
          <Typography variant="h6" fontWeight={700} gutterBottom>
            وضعیت تکمیل پرونده
          </Typography>

          <Stack spacing={1}>
            <Typography>
              مشخصات ملک:{' '}
              <strong>
                {property.data ? 'ثبت شده' : 'نیازمند تکمیل'}
              </strong>
            </Typography>

            <Typography>
              مالکین:{' '}
              <strong>
                {ownerItems.length > 0
                  ? `${ownerItems.length.toLocaleString('fa-IR')} مالک ثبت شده`
                  : 'هنوز ثبت نشده'}
              </strong>
            </Typography>

            <Typography>
              مدارک:{' '}
              <strong>
                {(documents.data?.length ?? 0) > 0
                  ? `${documents.data?.length.toLocaleString('fa-IR')} فایل`
                  : 'هنوز ثبت نشده'}
              </strong>
            </Typography>
          </Stack>
        </CardContent>
      </Card>
    </Stack>
  )
}
