import {
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons'
import {
  Alert,
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
            ownersQuery.data === null
          }
        >
          افزودن مالک
        </Button>
      </div>

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
        <Form<OwnerForm>
          form={form}
          layout="vertical"
          onFinish={(values) =>
            saveOwner.mutate(
              values,
            )
          }
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
      </Modal>
    </div>
  )
}
