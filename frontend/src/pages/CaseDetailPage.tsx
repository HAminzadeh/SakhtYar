import ArticleRoundedIcon from '@mui/icons-material/ArticleRounded'
import GroupsRoundedIcon from '@mui/icons-material/GroupsRounded'
import HomeWorkRoundedIcon from '@mui/icons-material/HomeWorkRounded'
import MapRoundedIcon from '@mui/icons-material/MapRounded'
import PsychologyRoundedIcon from '@mui/icons-material/PsychologyRounded'
import SpaceDashboardRoundedIcon from '@mui/icons-material/SpaceDashboardRounded'
import {
  Alert,
  Box,
  Chip,
  Paper,
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
import { useAuth } from '../auth/AuthProvider'
import type { CaseItem } from '../api/types'
import { AssistantPanel } from '../features/case/AssistantPanel'
import { CaseOverview } from '../features/case/CaseOverview'
import { DocumentsPanel } from '../features/case/DocumentsPanel'
import { OwnersPanel } from '../features/case/OwnersPanel'
import { PropertyMap } from '../features/case/PropertyMap'
import { PropertyPanel } from '../features/case/PropertyPanel'

type CaseTab =
  | 'overview'
  | 'property'
  | 'owners'
  | 'map'
  | 'documents'
  | 'assistant'

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
  return <Box sx={{ pt: { xs: 2, md: 3 } }}>{children}</Box>
}

export function CaseDetailPage() {
  const { id = '' } = useParams()
  const { hasPermission } = useAuth()
  const canUseAgent = hasPermission('AGENT_USE')
  const [tab, setTab] = useState<CaseTab>('overview')

  const caseQuery = useQuery({
    queryKey: ['case', id],
    queryFn: () => api<CaseItem>(`/api/v1/cases/${id}`),
    enabled: Boolean(id),
  })

  if (caseQuery.isLoading) {
    return (
      <Stack spacing={2}>
        <Skeleton variant="rounded" height={128} />
        <Skeleton variant="rounded" height={56} />
        <Skeleton variant="rounded" height={320} />
      </Stack>
    )
  }

  if (caseQuery.isError || !caseQuery.data) {
    return <Alert severity="error">پرونده پیدا نشد.</Alert>
  }

  const item = caseQuery.data
  const location =
    [item.city, item.district].filter(Boolean).join('، ') ||
    'موقعیت ثبت نشده'

  return (
    <Stack spacing={{ xs: 2, md: 3 }}>
      <Paper
        sx={{
          p: { xs: 2, sm: 2.5, md: 3 },
          border: '1px solid',
          borderColor: 'divider',
          borderRadius: 3,
          background:
            'linear-gradient(135deg, rgba(239,246,255,.92), rgba(255,255,255,1) 55%)',
        }}
      >
        <Stack
          direction={{ xs: 'column', md: 'row' }}
          justifyContent="space-between"
          alignItems={{ xs: 'stretch', md: 'center' }}
          gap={2}
        >
          <Box>
            <Stack
              direction="row"
              spacing={1}
              alignItems="center"
              flexWrap="wrap"
            >
              <Typography
                variant="h4"
                sx={{ fontSize: { xs: '1.45rem', sm: '1.8rem' } }}
              >
                {item.title}
              </Typography>

              <Chip
                size="small"
                label={statusLabel(item.status)}
                color={item.status === 'ACTIVE' ? 'success' : 'default'}
                variant={item.status === 'ACTIVE' ? 'filled' : 'outlined'}
              />
            </Stack>

            <Typography color="text.secondary" mt={0.75}>
              {location}
            </Typography>
          </Box>

          <Box
            sx={{
              px: 1.5,
              py: 1,
              borderRadius: 2,
              bgcolor: 'rgba(255,255,255,.78)',
              border: '1px solid',
              borderColor: 'divider',
              alignSelf: { xs: 'stretch', md: 'auto' },
            }}
          >
            <Typography variant="caption" color="text.secondary">
              آخرین بروزرسانی
            </Typography>
            <Typography variant="body2" fontWeight={700}>
              {new Date(item.updatedAt).toLocaleString('fa-IR')}
            </Typography>
          </Box>
        </Stack>
      </Paper>

      <Paper
        sx={{
          border: '1px solid',
          borderColor: 'divider',
          borderRadius: 3,
          overflow: 'hidden',
        }}
      >
        <Tabs
          value={tab}
          onChange={(_, value: CaseTab) => setTab(value)}
          variant="scrollable"
          scrollButtons="auto"
          allowScrollButtonsMobile
          sx={{
            px: { xs: 0.5, sm: 1 },
            '& .MuiTab-root': {
              minWidth: { xs: 92, sm: 120 },
              px: { xs: 1.25, sm: 2 },
            },
          }}
        >
          <Tab
            value="overview"
            icon={<SpaceDashboardRoundedIcon />}
            iconPosition="start"
            label="خلاصه"
          />
          <Tab
            value="property"
            icon={<HomeWorkRoundedIcon />}
            iconPosition="start"
            label="مشخصات ملک"
          />
          <Tab
            value="owners"
            icon={<GroupsRoundedIcon />}
            iconPosition="start"
            label="مالکین"
          />
          <Tab
            value="map"
            icon={<MapRoundedIcon />}
            iconPosition="start"
            label="نقشه"
          />
          <Tab
            value="documents"
            icon={<ArticleRoundedIcon />}
            iconPosition="start"
            label="مدارک"
          />
          {canUseAgent && (
            <Tab
              value="assistant"
              icon={<PsychologyRoundedIcon />}
              iconPosition="start"
              label="دستیار هوشمند"
            />
          )}
        </Tabs>
      </Paper>

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
        <PropertyMap caseId={id} />
      </TabPanel>

      <TabPanel value="documents" current={tab}>
        <DocumentsPanel caseId={id} />
      </TabPanel>

      {canUseAgent && (
        <TabPanel value="assistant" current={tab}>
          <AssistantPanel caseId={id} />
        </TabPanel>
      )}
    </Stack>
  )
}
