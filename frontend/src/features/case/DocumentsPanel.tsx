import CloudUploadRoundedIcon from '@mui/icons-material/CloudUploadRounded'
import {
  Alert,
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
import { api } from '../../api/client'
import type { DocumentItem } from '../../api/types'

function readableSize(value: number) {
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / (1024 * 1024)).toFixed(1)} MB`
}

export function DocumentsPanel({ caseId }: { caseId: string }) {
  const queryClient = useQueryClient()

  const documents = useQuery({
    queryKey: ['documents', caseId],
    queryFn: () =>
      api<DocumentItem[]>(`/api/v1/cases/${caseId}/documents`),
  })

  const upload = useMutation({
    mutationFn: async (file: File) => {
      const formData = new FormData()
      formData.append('file', file)

      return api<DocumentItem>(
        `/api/v1/cases/${caseId}/documents`,
        {
          method: 'POST',
          body: formData,
        },
      )
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['documents', caseId] })
    },
  })

  const handleFile = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]

    if (file) {
      upload.mutate(file)
    }

    event.target.value = ''
  }

  return (
    <Card variant="outlined">
      <CardContent>
        <Stack spacing={2}>
          <Stack
            direction={{ xs: 'column', sm: 'row' }}
            justifyContent="space-between"
            alignItems={{ xs: 'stretch', sm: 'center' }}
            gap={1}
          >
            <div>
              <Typography variant="h6" fontWeight={700}>
                مدارک پرونده
              </Typography>
              <Typography color="text.secondary" variant="body2">
                فایل‌ها در MinIO ذخیره و متادیتای آن‌ها در PostgreSQL ثبت می‌شود.
              </Typography>
            </div>

            <Button
              component="label"
              variant="outlined"
              startIcon={<CloudUploadRoundedIcon />}
              disabled={upload.isPending}
            >
              {upload.isPending ? 'در حال بارگذاری...' : 'بارگذاری مدرک'}
              <input hidden type="file" onChange={handleFile} />
            </Button>
          </Stack>

          {upload.isError && (
            <Alert severity="error">
              بارگذاری فایل ناموفق بود.
            </Alert>
          )}

          {documents.isError && (
            <Alert severity="error">
              دریافت فهرست مدارک ناموفق بود.
            </Alert>
          )}

          <Divider />

          <List disablePadding>
            {documents.isLoading && (
              <ListItem>
                <ListItemText primary="در حال دریافت مدارک..." />
              </ListItem>
            )}

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
  )
}
