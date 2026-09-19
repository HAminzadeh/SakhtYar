import {
  Alert,
  Box,
  Card,
  Chip,
  Skeleton,
  Stack,
  Tab,
  Tabs,
  Typography,
} from '@mui/material'
import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import type { ReactNode } from 'react'
import { useParams } from 'react-router-dom'
import { api } from '../api/client'
import type { CaseItem } from '../api/types'
import { CaseOverview } from '../features/case/CaseOverview'
import { DocumentsPanel } from '../features/case/DocumentsPanel'
import { MapPlaceholder } from '../features/case/MapPlaceholder'
import { OwnersPanel } from '../features/case/OwnersPanel'
import { PropertyPanel } from '../features/case/PropertyPanel'

type CaseTab = 'overview' | 'property' | 'owners' | 'map' | 'documents'

function statusLabel(status: CaseItem['status']) {
  switch (status) {
    case 'ACTIVE':
      return 'فعال'
    case 'ARCHIVED':
      return 'بایگانی'
    default:
      return 'پیش‌نویس'
  }
}

function TabPanel({
  value,
  current,
  children,
}: {
  value: CaseTab
  current: CaseTab
  children: ReactNode
}) {
  if (value !== current) return null

  return <Box sx={{ pt: 3 }}>{children}</Box>
}

export function CaseDetailPage() {
  const { id = '' } = useParams()
  const [tab, setTab] = useState<CaseTab>('overview')

  const caseQuery = useQuery({
    queryKey: ['case', id],
    queryFn: () => api<CaseItem>(`/api/v1/cases/${id}`),
    enabled: Boolean(id),
  })

  if (caseQuery.isLoading) {
    return (
      <Stack spacing={2}>
        <Skeleton variant="text" width="35%" height={52} />
        <Skeleton variant="rounded" height={64} />
        <Skeleton variant="rounded" height={280} />
      </Stack>
    )
  }

  if (caseQuery.isError || !caseQuery.data) {
    return <Alert severity="error">پرونده پیدا نشد.</Alert>
  }

  const item = caseQuery.data
  const location =
    [item.city, item.district].filter(Boolean).join(' - ') ||
    'موقعیت ثبت نشده'

  return (
    <Stack spacing={3}>
      <Stack
        direction={{ xs: 'column', md: 'row' }}
        justifyContent="space-between"
        alignItems={{ xs: 'flex-start', md: 'center' }}
        gap={2}
      >
        <Box>
          <Stack
            direction="row"
            spacing={1.5}
            alignItems="center"
            flexWrap="wrap"
          >
            <Typography variant="h4" fontWeight={800}>
              {item.title}
            </Typography>

            <Chip
              size="small"
              label={statusLabel(item.status)}
              color={item.status === 'ACTIVE' ? 'success' : 'default'}
            />
          </Stack>

          <Typography color="text.secondary" mt={0.5}>
            {location}
          </Typography>
        </Box>

        <Typography variant="body2" color="text.secondary">
          آخرین بروزرسانی:{' '}
          {new Date(item.updatedAt).toLocaleString('fa-IR')}
        </Typography>
      </Stack>

      <Card variant="outlined">
        <Tabs
          value={tab}
          onChange={(_, value: CaseTab) => setTab(value)}
          variant="scrollable"
          scrollButtons="auto"
          allowScrollButtonsMobile
        >
          <Tab value="overview" label="خلاصه" />
          <Tab value="property" label="مشخصات ملک" />
          <Tab value="owners" label="مالکین" />
          <Tab value="map" label="نقشه" />
          <Tab value="documents" label="مدارک" />
        </Tabs>
      </Card>

      <TabPanel value="overview" current={tab}>
        <CaseOverview caseId={id} caseItem={item} />
      </TabPanel>

      <TabPanel value="property" current={tab}>
        <PropertyPanel caseId={id} caseItem={item} />
      </TabPanel>

      <TabPanel value="owners" current={tab}>
        <OwnersPanel caseId={id} />
      </TabPanel>

      <TabPanel value="map" current={tab}>
        <MapPlaceholder />
      </TabPanel>

      <TabPanel value="documents" current={tab}>
        <DocumentsPanel caseId={id} />
      </TabPanel>
    </Stack>
  )
}
