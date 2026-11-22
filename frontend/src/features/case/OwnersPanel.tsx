import {
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons'
import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Checkbox,
  Col,
  Form,
  Input,
  InputNumber,
  Modal,
  Progress,
  Row,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd'
import {
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import {
  useMemo,
  useState,
} from 'react'
import {
  ApiError,
  api,
} from '../../api/client'
import type { OwnerItem } from '../../api/types'
import { useI18n } from '../../i18n/LanguageProvider'

type OwnerForm = {
  firstName: string
  lastName: string
  nationalId?: string
  mobile?: string
  ownershipNumerator: number
  ownershipDenominator: number
  primaryContact: boolean
}

function percent(
  numerator: number,
  denominator: number,
) {
  return denominator
    ? (numerator / denominator) * 100
    : 0
}

function faPercent(value: number) {
  return new Intl.NumberFormat(
    'fa-IR',
    { maximumFractionDigits: 2 },
  ).format(value)
}

export function OwnersPanel({
  caseId,
}: {
  caseId: string
}) {
  const queryClient =
    useQueryClient()
  const { message } = AntdApp.useApp()
  const { language } = useI18n()
  const [ownerError, setOwnerError] =
    useState<string | null>(null)
  const [open, setOpen] =
    useState(false)
  const [editing, setEditing] =
    useState<OwnerItem | null>(null)
  const [form] =
    Form.useForm<OwnerForm>()

  const ownersQuery = useQuery({
    queryKey: ['owners', caseId],
    queryFn: async (): Promise<
      OwnerItem[] | null
    > => {
      try {
        return await api<
          OwnerItem[]
        >(
          `/api/v1/cases/${caseId}/owners`,
        )
      } catch (error) {
        if (
          error instanceof ApiError &&
          error.status === 404
        ) {
          return null
        }
        throw error
      }
    },
    retry: false,
  })

  const owners =
    ownersQuery.data ?? []

  const totalPercent = useMemo(
    () =>
      owners.reduce(
        (sum, owner) =>
          sum +
          percent(
            owner.ownershipNumerator,
            owner.ownershipDenominator,
          ),
        0,
      ),
    [owners],
  )

  const editingPercent = editing
    ? percent(
        editing.ownershipNumerator,
        editing.ownershipDenominator,
      )
    : 0

  const basePercentForEdit =
    Math.max(0, totalPercent - editingPercent)

  const shareError = (
    numerator?: number,
    denominator?: number,
  ) => {
    if (!numerator || !denominator) return null

    if (numerator < 1 || denominator < 1) {
      return language === 'fa'
        ? 'صورت و مخرج سهم باید حداقل ۱ باشند.'
        : 'Share numerator and denominator must be at least 1.'
    }

    if (numerator > denominator) {
      return language === 'fa'
        ? 'صورت سهم نمی‌تواند از مخرج سهم بیشتر باشد.'
        : 'Share numerator cannot be greater than denominator.'
    }

    const proposed = percent(numerator, denominator)
    const projected = basePercentForEdit + proposed

    if (projected > 100.000001) {
      const remaining = Math.max(0, 100 - basePercentForEdit)

      return language === 'fa'
        ? `با این سهم، مجموع مالکیت ${faPercent(projected)}٪ می‌شود و از ۱۰۰٪ بیشتر است. حداکثر سهم قابل ثبت ${faPercent(remaining)}٪ است.`
        : `This share makes total ownership ${projected.toFixed(2)}%, which exceeds 100%. The maximum available share is ${remaining.toFixed(2)}%.`
    }

    return null
  }

  const handleOwnerSubmit = (values: OwnerForm) => {
    const error = shareError(
      values.ownershipNumerator,
      values.ownershipDenominator,
    )

    if (error) {
      setOwnerError(error)
      form.setFields([
        { name: 'ownershipNumerator', errors: [error] },
        { name: 'ownershipDenominator', errors: [error] },
      ])
      message.error(error)
      return
    }

    setOwnerError(null)
    saveOwner.mutate(values)
  }
  const primaryContact =
    owners.find(
      (owner) =>
        owner.primaryContact,
    )

  const saveOwner = useMutation({
    mutationFn: async (
      values: OwnerForm,
    ) => {
      const payload = {
        ...values,
        nationalId:
          values.nationalId?.trim() ||
          null,
        mobile:
          values.mobile?.trim() ||
          null,
      }

      if (editing) {
        return api<OwnerItem>(
          `/api/v1/cases/${caseId}/owners/${editing.id}`,
          {
            method: 'PUT',
            body: JSON.stringify(
              payload,
            ),
          },
        )
      }

      return api<OwnerItem>(
        `/api/v1/cases/${caseId}/owners`,
        {
          method: 'POST',
          body: JSON.stringify(payload),
        },
      )
    },
    onSuccess: () => {
      setOwnerError(null)
      message.success(language === 'fa' ? (editing ? 'اطلاعات مالک با موفقیت ویرایش شد.' : 'مالک با موفقیت ثبت شد.') : (editing ? 'Owner updated successfully.' : 'Owner added successfully.'))
      setOpen(false)
      setEditing(null)
      form.resetFields()
      queryClient.invalidateQueries({
        queryKey: [
          'owners',
          caseId,
        ],
      })
    },
    onError: (error) => {
      const text =
        error instanceof ApiError
          ? error.message
          : language === 'fa'
            ? 'ذخیره مالک ناموفق بود.'
            : 'Failed to save owner.'

      const localized =
        language === 'fa' &&
        text === 'Total ownership shares cannot exceed 100 percent.'
          ? 'جمع سهم مالکین نمی‌تواند بیشتر از ۱۰۰٪ باشد.'
          : language === 'fa' &&
              text === 'Ownership numerator cannot be greater than denominator.'
            ? 'صورت سهم نمی‌تواند از مخرج سهم بیشتر باشد.'
            : text

      setOwnerError(localized)
    },
  })

  const deleteOwner =
    useMutation({
      mutationFn: (
        ownerId: string,
      ) =>
        api<void>(
          `/api/v1/cases/${caseId}/owners/${ownerId}`,
          { method: 'DELETE' },
        ),
      onSuccess: () =>
        queryClient.invalidateQueries({
          queryKey: [
            'owners',
            caseId,
          ],
        }),
    })

  const openCreate = () => {
    if (totalPercent >= 99.999999) {
      message.warning(language === 'fa' ? 'مجموع سهم مالکین ۱۰۰٪ است و سهم خالی برای مالک جدید وجود ندارد.' : 'Ownership already totals 100%; there is no remaining share for a new owner.')
      return
    }
    setOwnerError(null)
    setEditing(null)
    form.setFieldsValue({
      firstName: '',
      lastName: '',
      nationalId: '',
      mobile: '',
      ownershipNumerator: 1,
      ownershipDenominator: 1,
      primaryContact: false,
    })
    setOpen(true)
  }

  const openEdit = (
    owner: OwnerItem,
  ) => {
    setOwnerError(null)
    setEditing(owner)
    form.setFieldsValue({
      firstName: owner.firstName,
      lastName: owner.lastName,
      nationalId:
        owner.nationalId ?? '',
      mobile: owner.mobile ?? '',
      ownershipNumerator:
        owner.ownershipNumerator,
      ownershipDenominator:
        owner.ownershipDenominator,
      primaryContact:
        owner.primaryContact,
    })
    setOpen(true)
  }

  const columns = [
    {
      title: 'مالک',
      key: 'owner',
      render: (
        _: unknown,
        owner: OwnerItem,
      ) => (
        <Space>
          <div className="sakhtyar-owner-avatar">
            <UserOutlined />
          </div>
          <strong>
            {owner.firstName}{' '}
            {owner.lastName}
          </strong>
        </Space>
      ),
    },
    {
      title: 'کد ملی',
      dataIndex: 'nationalId',
      render: (
        value?: string | null,
      ) => value || '-',
    },
    {
      title: 'موبایل',
      dataIndex: 'mobile',
      render: (
        value?: string | null,
      ) => value || '-',
    },
    {
      title: 'سهم',
      key: 'share',
      render: (
        _: unknown,
        owner: OwnerItem,
      ) => (
        <strong>
          {faPercent(
            percent(
              owner.ownershipNumerator,
              owner.ownershipDenominator,
            ),
          )}
          ٪
        </strong>
      ),
    },
    {
      title: 'وضعیت',
      key: 'status',
      render: (
        _: unknown,
        owner: OwnerItem,
      ) =>
        owner.primaryContact ? (
          <Tag color="blue">
            رابط اصلی
          </Tag>
        ) : (
          '-'
        ),
    },
    {
      title: 'عملیات',
      key: 'actions',
      render: (
        _: unknown,
        owner: OwnerItem,
      ) => (
        <Space>
          <Button
            size="small"
            icon={<EditOutlined />}
            onClick={() =>
              openEdit(owner)
            }
          />
          <Button
            danger
            size="small"
            icon={<DeleteOutlined />}
            onClick={() => {
              if (
                window.confirm(
                  `مالک «${owner.firstName} ${owner.lastName}» حذف شود؟`,
                )
              ) {
                deleteOwner.mutate(
                  owner.id,
                )
              }
            }}
          />
        </Space>
      ),
    },
  ]

  return (
    <div className="sakhtyar-owners-v56">
      <div className="sakhtyar-owners-v56__head">
        <div>
          <Typography.Title level={4}>
            مالکین ملک
          </Typography.Title>
          <Typography.Text type="secondary">
            مالکین، سهم مالکیت و
            رابط اصلی پرونده
          </Typography.Text>
        </div>

        <Button
          type="primary"
          icon={<PlusOutlined />}
          className="sakhtyar-animated-primary"
          onClick={openCreate}
          disabled={
            ownersQuery.data === null ||
            totalPercent >= 99.999999
          }
        >
          افزودن مالک
        </Button>
      </div>

      {ownersQuery.isError ? (
        <Alert
          type="error"
          showIcon
          message={language === 'fa' ? 'دریافت فهرست مالکین ناموفق بود.' : 'Failed to load owners.'}
          style={{ marginBottom: 12 }}
        />
      ) : null}

      {ownersQuery.data === null ? (
        <Alert
          type="warning"
          showIcon
          message="ابتدا در تب مشخصات ملک، اطلاعات ملک را ذخیره کنید."
        />
      ) : null}

      <Row
        gutter={[12, 12]}
        className="sakhtyar-owner-summary-row"
      >
        <Col xs={24} md={8}>
          <Card className="sakhtyar-owner-summary-card sakhtyar-animated-card">
            <Statistic
              title="تعداد مالک"
              value={owners.length}
              suffix="نفر"
              prefix={<TeamOutlined />}
            />
          </Card>
        </Col>

        <Col xs={24} md={8}>
          <Card className="sakhtyar-owner-summary-card sakhtyar-animated-card">
            <Statistic
              title="مجموع سهم"
              value={Number(
                totalPercent.toFixed(2),
              )}
              suffix="٪"
            />
          </Card>
        </Col>

        <Col xs={24} md={8}>
          <Card className="sakhtyar-owner-summary-card sakhtyar-animated-card">
            <div className="sakhtyar-owner-primary">
              <small>رابط اصلی</small>
              <strong>
                {primaryContact
                  ? `${primaryContact.firstName} ${primaryContact.lastName}`
                  : 'تعیین نشده'}
              </strong>
            </div>
          </Card>
        </Col>
      </Row>

      <Card className="sakhtyar-owner-progress-card">
        <Space
          direction="vertical"
          size={8}
          style={{ width: '100%' }}
        >
          <div className="sakhtyar-owner-progress-title">
            <span>
              پوشش سهم مالکیت
            </span>
            <strong>
              {faPercent(
                totalPercent,
              )}
              ٪
            </strong>
          </div>
          <Progress
            percent={Math.min(
              100,
              Number(
                totalPercent.toFixed(
                  2,
                ),
              ),
            )}
            showInfo={false}
            strokeColor="#22c55e"
          />
        </Space>
      </Card>

      <Card className="sakhtyar-owner-table-card">
        <Table
          rowKey="id"
          dataSource={owners}
          columns={columns}
          pagination={false}
          scroll={{ x: 820 }}
          locale={{
            emptyText:
              'هنوز مالکی ثبت نشده است.',
          }}
        />
      </Card>

      <Modal
        open={open}
        title={
          editing
            ? 'ویرایش مالک'
            : 'افزودن مالک'
        }
        footer={null}
        onCancel={() =>
          setOpen(false)
        }
        destroyOnHidden
      >
        <>
          {ownerError ? (
            <Alert
              type="error"
              showIcon
              closable
              message={ownerError}
              style={{ marginBottom: 12 }}
              onClose={() => setOwnerError(null)}
            />
          ) : null}

          <Form<OwnerForm>
            form={form}
            layout="vertical"
            onFinish={handleOwnerSubmit}
            onValuesChange={() => {
              setOwnerError(null)
              form.setFields([
                { name: 'ownershipNumerator', errors: [] },
                { name: 'ownershipDenominator', errors: [] },
              ])
            }}
          >
          <Row gutter={12}>
            <Col span={12}>
              <Form.Item
                label="نام"
                name="firstName"
                rules={[
                  {
                    required: true,
                  },
                ]}
              >
                <Input />
              </Form.Item>
            </Col>

            <Col span={12}>
              <Form.Item
                label="نام خانوادگی"
                name="lastName"
                rules={[
                  {
                    required: true,
                  },
                ]}
              >
                <Input />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={12}>
            <Col span={12}>
              <Form.Item
                label="کد ملی"
                name="nationalId"
              >
                <Input />
              </Form.Item>
            </Col>

            <Col span={12}>
              <Form.Item
                label="موبایل"
                name="mobile"
              >
                <Input />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={12}>
            <Col span={12}>
              <Form.Item
                label="صورت سهم"
                name="ownershipNumerator"
                rules={[
                  {
                    required: true,
                  },
                ]}
              >
                <InputNumber
                  min={1}
                  style={{
                    width: '100%',
                  }}
                />
              </Form.Item>
            </Col>

            <Col span={12}>
              <Form.Item
                label="مخرج سهم"
                name="ownershipDenominator"
                rules={[
                  {
                    required: true,
                  },
                ]}
              >
                <InputNumber
                  min={1}
                  style={{
                    width: '100%',
                  }}
                />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item
            name="primaryContact"
            valuePropName="checked"
          >
            <Checkbox>
              این مالک رابط اصلی
              پرونده است
            </Checkbox>
          </Form.Item>

          <Button
            type="primary"
            htmlType="submit"
            block
            loading={
              saveOwner.isPending
            }
          >
            ذخیره مالک
          </Button>
          </Form>
        </>
      </Modal>
    </div>
  )
}
