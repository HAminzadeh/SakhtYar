import CloudUploadRoundedIcon from '@mui/icons-material/CloudUploadRounded'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Divider,
  List,
  ListItem,
  ListItemText,
  Stack,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { type ChangeEvent } from 'react'
import { useParams } from 'react-router-dom'
import { api } from '../api/client'
import type { CaseItem, DocumentItem } from '../api/types'

function readableSize(value: number) {
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / (1024 * 1024)).toFixed(1)} MB`
}

export function CaseDetailPage() {
  const { id = '' } = useParams()
  const queryClient = useQueryClient()

  const caseQuery = useQuery({
    queryKey: ['case', id],
    queryFn: () => api<CaseItem>(`/api/v1/cases/${id}`),
    enabled: Boolean(id),
  })

  const documents = useQuery({
    queryKey: ['documents', id],
    queryFn: () =>
      api<DocumentItem[]>(`/api/v1/cases/${id}/documents`),
    enabled: Boolean(id),
  })

  const upload = useMutation({
    mutationFn: async (file: File) => {
      const formData = new FormData()
      formData.append('file', file)

      return api<DocumentItem>(
        `/api/v1/cases/${id}/documents`,
        {
          method: 'POST',
          body: formData,
        },
      )
    },
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ['documents', id] }),
  })

  const handleFile = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (file) {
      upload.mutate(file)
    }
    event.target.value = ''
  }

  if (caseQuery.isError) {
    return <Alert severity="error">پرونده پیدا نشد.</Alert>
  }

  const item = caseQuery.data

  return (
    <Stack spacing={3}>
      <Box>
        <Typography variant="h4" fontWeight={800}>
          {item?.title ?? 'در حال بارگذاری...'}
        </Typography>
        <Typography color="text.secondary">
          {[item?.city, item?.district].filter(Boolean).join(' - ')}
        </Typography>
      </Box>

      <Card variant="outlined">
        <CardContent>
          <Typography variant="h6" fontWeight={700} gutterBottom>
            مشخصات پایه
          </Typography>
          <Stack spacing={1}>
            <Typography>
              وضعیت: {item?.status ?? '-'}
            </Typography>
            <Typography>
              مساحت: {item?.landAreaM2 ?? '-'} متر مربع
            </Typography>
            <Typography>
              آدرس: {item?.address || '-'}
            </Typography>
          </Stack>
        </CardContent>
      </Card>

      <Card variant="outlined">
        <CardContent>
          <Stack spacing={2}>
            <Stack
              direction={{ xs: 'column', sm: 'row' }}
              justifyContent="space-between"
              gap={1}
            >
              <div>
                <Typography variant="h6" fontWeight={700}>
                  مدارک پرونده
                </Typography>
                <Typography color="text.secondary" variant="body2">
                  فایل‌ها در MinIO ذخیره و متادیتا در PostgreSQL ثبت می‌شود.
                </Typography>
              </div>

              <Button
                component="label"
                variant="outlined"
                startIcon={<CloudUploadRoundedIcon />}
                disabled={upload.isPending}
              >
                بارگذاری مدرک
                <input
                  hidden
                  type="file"
                  onChange={handleFile}
                />
              </Button>
            </Stack>

            {upload.isError && (
              <Alert severity="error">بارگذاری ناموفق بود.</Alert>
            )}

            <Divider />

            <List disablePadding>
              {documents.data?.length === 0 && (
                <ListItem>
                  <ListItemText primary="هنوز مدرکی ثبت نشده است." />
                </ListItem>
              )}

              {documents.data?.map((document) => (
                <ListItem
                  key={document.id}
                  divider
                  secondaryAction={
                    <Button
                      href={`/api/v1/documents/${document.id}/content`}
                      target="_blank"
                    >
                      دریافت
                    </Button>
                  }
                >
                  <ListItemText
                    primary={document.originalFilename}
                    secondary={`${readableSize(document.sizeBytes)} • ${document.uploadedBy}`}
                  />
                </ListItem>
              ))}
            </List>
          </Stack>
        </CardContent>
      </Card>
    </Stack>
  )
}
