import { CloudUploadRoundedIcon } from '../../ui/antdIcons'
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
} from '../../ui/antdCompat'
import { App as AntdApp } from 'antd'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { type ChangeEvent, useRef, useState } from 'react'
import { ApiError, api } from '../../api/client'
import type { DocumentItem } from '../../api/types'
import { useI18n } from '../../i18n/LanguageProvider'

const MAX_DOCUMENT_BYTES = 25 * 1024 * 1024
const SUPPORTED_EXTENSIONS = new Set([
  'pdf', 'jpg', 'jpeg', 'png', 'webp', 'docx', 'xlsx', 'txt',
])

function readableSize(value: number) {
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / (1024 * 1024)).toFixed(1)} MB`
}

function invalidDocumentReason(file: File, persian: boolean): string | null {
  if (file.size <= 0 || file.size > MAX_DOCUMENT_BYTES) {
    return persian
      ? 'حجم فایل باید بیشتر از صفر و حداکثر ۲۵ مگابایت باشد.'
      : 'Document size must be greater than zero and at most 25 MiB.'
  }
  const extension = file.name.split('.').pop()?.toLowerCase() ?? ''
  if (!SUPPORTED_EXTENSIONS.has(extension)) {
    return persian
      ? 'فرمت فایل مجاز نیست. PDF، تصویر، DOCX، XLSX و TXT پذیرفته می‌شوند.'
      : 'Unsupported format. Use PDF, an image, DOCX, XLSX or TXT.'
  }
  return null
}

export function DocumentsPanel({ caseId }: { caseId: string }) {
  const queryClient = useQueryClient()
  const inputRef = useRef<HTMLInputElement | null>(null)
  const [uploadError, setUploadError] = useState<string | null>(null)
  const { message } = AntdApp.useApp()
  const { language } = useI18n()

  const documents = useQuery({
    queryKey: ['documents', caseId],
    queryFn: () => api<DocumentItem[]>(`/api/v1/cases/${caseId}/documents`),
  })

  const upload = useMutation({
    mutationFn: async (file: File) => {
      const formData = new FormData()
      formData.append('file', file)
      return api<DocumentItem>(`/api/v1/cases/${caseId}/documents`, {
        method: 'POST',
        body: formData,
      })
    },
    onSuccess: (document) => {
      setUploadError(null)
      message.success(
        language === 'fa'
          ? `مدرک «${document.originalFilename}» با موفقیت بارگذاری شد.`
          : `Document "${document.originalFilename}" uploaded successfully.`,
      )
      queryClient.invalidateQueries({ queryKey: ['documents', caseId] })
    },
    onError: (error) => {
      const text = error instanceof ApiError
        ? error.message
        : language === 'fa' ? 'بارگذاری مدرک ناموفق بود.' : 'Document upload failed.'
      setUploadError(text)
      message.error(text)
    },
  })

  const handleFile = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return
    const error = invalidDocumentReason(file, language === 'fa')
    if (error) {
      setUploadError(error)
      message.error(error)
      return
    }
    setUploadError(null)
    upload.mutate(file)
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
              <Typography color="text.secondary" variant="caption">
                {language === 'fa'
                  ? 'فرمت‌های مجاز: PDF، تصویر، DOCX، XLSX، TXT — حداکثر ۲۵ مگابایت'
                  : 'PDF, image, DOCX, XLSX, TXT — up to 25 MiB'}
              </Typography>
            </div>
            <div>
              <input
                ref={inputRef}
                type="file"
                hidden
                onChange={handleFile}
                accept=".pdf,.jpg,.jpeg,.png,.webp,.docx,.xlsx,.txt"
              />
              <Button
                variant="outlined"
                startIcon={<CloudUploadRoundedIcon />}
                disabled={upload.isPending}
                onClick={() => inputRef.current?.click()}
              >
                {upload.isPending ? 'در حال بارگذاری...' : 'بارگذاری مدرک'}
              </Button>
            </div>
          </Stack>

          {uploadError ? <Alert severity="error">{uploadError}</Alert> : null}
          {documents.isError ? (
            <Alert severity="error">
              {language === 'fa' ? 'دریافت فهرست مدارک ناموفق بود.' : 'Failed to load documents.'}
            </Alert>
          ) : null}
          <Divider />
          <List disablePadding>
            {documents.isLoading ? (
              <ListItem>
                <ListItemText primary={language === 'fa'
                  ? 'در حال دریافت مدارک...' : 'Loading documents...'} />
              </ListItem>
            ) : null}
            {documents.data?.length === 0 ? (
              <ListItem>
                <ListItemText primary={language === 'fa'
                  ? 'هنوز مدرکی ثبت نشده است.' : 'No documents have been uploaded yet.'} />
              </ListItem>
            ) : null}
            {documents.data?.map((document) => (
              <ListItem
                key={document.id}
                divider
                secondaryAction={
                  <Button href={`/api/v1/documents/${document.id}/content`} target="_blank">
                    {language === 'fa' ? 'دریافت' : 'Download'}
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
