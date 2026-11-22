import {
  CameraOutlined,
  PictureOutlined,
} from '@ant-design/icons'
import {
  Alert,
  Button,
  Card,
  Empty,
  Image,
  Input,
  Modal,
  Select,
  Space,
  Tag,
  Typography,
  message,
} from 'antd'
import {
  useMemo,
  useRef,
  useState,
} from 'react'
import {
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import { api } from '../../api/client'
import type { DocumentItem } from '../../api/types'

type GalleryStage =
  | 'BEFORE_DEMOLITION'
  | 'DEMOLITION'
  | 'CONSTRUCTION'
  | 'FINISHING'
  | 'DELIVERY'

const stages: Array<{
  value: GalleryStage
  label: string
  color: string
}> = [
  {
    value: 'BEFORE_DEMOLITION',
    label: 'قبل از تخریب',
    color: 'default',
  },
  {
    value: 'DEMOLITION',
    label: 'مرحله تخریب',
    color: 'volcano',
  },
  {
    value: 'CONSTRUCTION',
    label: 'حین ساخت',
    color: 'blue',
  },
  {
    value: 'FINISHING',
    label: 'نازک‌کاری / تکمیل',
    color: 'gold',
  },
  {
    value: 'DELIVERY',
    label: 'تحویل / نتیجه نهایی',
    color: 'green',
  },
]

function stageMeta(stage: GalleryStage) {
  return (
    stages.find((item) => item.value === stage) ??
    stages[0]
  )
}

function encodeName(
  stage: GalleryStage,
  note: string,
  originalName: string,
) {
  const cleanNote = note
    .trim()
    .replace(/[|/\\:*?"<>]/g, ' ')
    .slice(0, 60)

  return `GALLERY__${stage}__${cleanNote || 'تصویر پروژه'}__${originalName}`
}

function parseDocument(document: DocumentItem) {
  const parts =
    document.originalFilename.split('__')

  if (
    parts.length >= 4 &&
    parts[0] === 'GALLERY'
  ) {
    return {
      document,
      stage: parts[1] as GalleryStage,
      note: parts[2],
      sourceName: parts.slice(3).join('__'),
    }
  }

  return null
}

export function ProjectGalleryPanel({
  caseId,
  caseTitle,
}: {
  caseId: string
  caseTitle: string
}) {
  const queryClient = useQueryClient()
  const inputRef =
    useRef<HTMLInputElement | null>(null)
  const [stage, setStage] =
    useState<GalleryStage>('CONSTRUCTION')
  const [note, setNote] = useState('')
  const [preview, setPreview] =
    useState<DocumentItem | null>(null)

  const documents = useQuery({
    queryKey: ['documents', caseId],
    queryFn: () =>
      api<DocumentItem[]>(
        `/api/v1/cases/${caseId}/documents`,
      ),
  })

  const gallery = useMemo(
    () =>
      (documents.data ?? [])
        .map(parseDocument)
        .filter(
          (
            item,
          ): item is NonNullable<
            ReturnType<typeof parseDocument>
          > => Boolean(item),
        )
        .filter((item) =>
          item.document.contentType?.startsWith(
            'image/',
          ),
        )
        .sort((a, b) =>
          a.document.uploadedAt <
          b.document.uploadedAt
            ? 1
            : -1,
        ),
    [documents.data],
  )

  const upload = useMutation({
    mutationFn: async (files: File[]) => {
      const uploaded: DocumentItem[] = []

      for (const file of files) {
        const renamed = new File(
          [file],
          encodeName(stage, note, file.name),
          { type: file.type },
        )
        const formData = new FormData()
        formData.append('file', renamed)

        uploaded.push(
          await api<DocumentItem>(
            `/api/v1/cases/${caseId}/documents`,
            {
              method: 'POST',
              body: formData,
            },
          ),
        )
      }

      return uploaded
    },
    onSuccess: (result) => {
      queryClient.invalidateQueries({
        queryKey: ['documents', caseId],
      })
      message.success(
        `${result.length.toLocaleString(
          'fa-IR',
        )} تصویر به گالری اضافه شد.`,
      )
      setNote('')
      if (inputRef.current) {
        inputRef.current.value = ''
      }
    },
  })

  const handleFiles = (
    files: FileList | null,
  ) => {
    if (!files?.length) return

    const images = Array.from(files).filter(
      (file) =>
        file.type.startsWith('image/'),
    )

    if (!images.length) {
      message.warning(
        'لطفاً فایل تصویری انتخاب کنید.',
      )
      return
    }

    upload.mutate(images)
  }

  return (
    <div className="sakhtyar-gallery-panel">
      <Card className="sakhtyar-gallery-uploader sakhtyar-animated-card">
        <div className="sakhtyar-gallery-uploader__header">
          <div>
            <Typography.Title level={4}>
              گالری تصاویر پروژه
            </Typography.Title>
            <Typography.Text type="secondary">
              مراحل مختلف {caseTitle} را با
              تصویر مستند کنید؛ تصاویر در همان
              زیرساخت مدارک پرونده ذخیره می‌شوند.
            </Typography.Text>
          </div>

          <Tag
            icon={<PictureOutlined />}
            color="blue"
          >
            {gallery.length.toLocaleString(
              'fa-IR',
            )}{' '}
            تصویر
          </Tag>
        </div>

        <div className="sakhtyar-gallery-uploader__controls">
          <Select
            value={stage}
            onChange={(value) =>
              setStage(value)
            }
            options={stages.map((item) => ({
              value: item.value,
              label: item.label,
            }))}
          />

          <Input
            value={note}
            onChange={(event) =>
              setNote(event.target.value)
            }
            placeholder="عنوان یا توضیح کوتاه تصویر..."
          />

          <input
            ref={inputRef}
            type="file"
            accept="image/*"
            multiple
            hidden
            onChange={(event) =>
              handleFiles(event.target.files)
            }
          />

          <Button
            type="primary"
            icon={<CameraOutlined />}
            className="sakhtyar-animated-primary"
            loading={upload.isPending}
            onClick={() =>
              inputRef.current?.click()
            }
          >
            افزودن تصویر
          </Button>
        </div>

        {upload.isError ? (
          <Alert
            type="error"
            showIcon
            message="بارگذاری تصویر ناموفق بود."
            style={{ marginTop: 12 }}
          />
        ) : null}
      </Card>

      {documents.isError ? (
        <Alert
          type="error"
          showIcon
          message="دریافت تصاویر پروژه ناموفق بود."
        />
      ) : null}

      {gallery.length ? (
        <div className="sakhtyar-gallery-grid">
          {gallery.map((item) => {
            const meta = stageMeta(
              item.stage,
            )

            return (
              <Card
                key={item.document.id}
                hoverable
                className="sakhtyar-gallery-card sakhtyar-animated-card"
                cover={
                  <button
                    type="button"
                    className="sakhtyar-gallery-card__cover"
                    onClick={() =>
                      setPreview(
                        item.document,
                      )
                    }
                  >
                    <img
                      src={`/api/v1/documents/${item.document.id}/content`}
                      alt={item.note}
                    />
                  </button>
                }
              >
                <Space
                  direction="vertical"
                  size={7}
                  style={{ width: '100%' }}
                >
                  <div className="sakhtyar-gallery-card__top">
                    <Typography.Text
                      strong
                      ellipsis
                    >
                      {item.note}
                    </Typography.Text>
                    <Tag color={meta.color}>
                      {meta.label}
                    </Tag>
                  </div>

                  <Typography.Text type="secondary">
                    {new Date(
                      item.document.uploadedAt,
                    ).toLocaleString(
                      'fa-IR',
                    )}
                  </Typography.Text>
                </Space>
              </Card>
            )
          })}
        </div>
      ) : (
        <Card className="sakhtyar-gallery-empty sakhtyar-animated-card">
          <Empty description="هنوز تصویری برای این پروژه ثبت نشده است." />
        </Card>
      )}

      <Modal
        open={Boolean(preview)}
        onCancel={() => setPreview(null)}
        footer={null}
        width={980}
        destroyOnHidden
      >
        {preview ? (
          <Image
            src={`/api/v1/documents/${preview.id}/content`}
            alt={preview.originalFilename}
            style={{ width: '100%' }}
          />
        ) : null}
      </Modal>
    </div>
  )
}
