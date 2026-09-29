import {
  AppstoreOutlined,
  EnvironmentOutlined,
  FileTextOutlined,
  HomeOutlined,
  SaveOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons'
import {
  Alert,
  Button,
  Card,
  Col,
  Input,
  Row,
  Select,
  Space,
  Tabs,
  Typography,
} from 'antd'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { ApiError, api } from '../../api/client'
import type { CaseItem, PropertyItem } from '../../api/types'
import { useAuth } from '../../auth/AuthProvider'
import {
  buildingConditionOptions,
  cornerPositionOptions,
  deedTypeOptions,
  landUseOptions,
  orientationOptions,
  ownershipStatusOptions,
  propertyTypeOptions,
  type OptionItem,
} from '../../domain/propertyOptions'
import { PropertyMap } from './PropertyMap'

type PropertyForm = {
  province: string
  city: string
  district: string
  neighborhood: string
  address: string
  landAreaM2: string
  frontageM: string
  passageWidthM: string
  buildingAreaM2: string
  constructionYear: string
  existingFloors: string
  existingUnits: string
  orientation: string
  propertyType: string
  buildingCondition: string
  deedType: string
  ownershipStatus: string
  cornerPosition: string
  landUse: string
  registryMainNo: string
  registrySubNo: string
  registrySection: string
  postalCode: string
  latitude: string
  longitude: string
}

function attr(item: PropertyItem | null | undefined, key: string) {
  const value = item?.attributes?.[key]
  return typeof value === 'string' ? value : ''
}

function fromCase(item: CaseItem): PropertyForm {
  return {
    province: '',
    city: item.city ?? '',
    district: item.district ?? '',
    neighborhood: '',
    address: item.address ?? '',
    landAreaM2: item.landAreaM2 == null ? '' : String(item.landAreaM2),
    frontageM: '',
    passageWidthM: '',
    buildingAreaM2: '',
    constructionYear: '',
    existingFloors: '',
    existingUnits: '',
    orientation: '',
    propertyType: '',
    buildingCondition: '',
    deedType: '',
    ownershipStatus: '',
    cornerPosition: '',
    landUse: '',
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
    landAreaM2: item.landAreaM2 == null ? '' : String(item.landAreaM2),
    frontageM: item.frontageM == null ? '' : String(item.frontageM),
    passageWidthM:
      item.passageWidthM == null ? '' : String(item.passageWidthM),
    buildingAreaM2:
      item.buildingAreaM2 == null ? '' : String(item.buildingAreaM2),
    constructionYear:
      item.constructionYear == null ? '' : String(item.constructionYear),
    existingFloors:
      item.existingFloors == null ? '' : String(item.existingFloors),
    existingUnits:
      item.existingUnits == null ? '' : String(item.existingUnits),
    orientation: item.orientation ?? '',
    propertyType: item.propertyType ?? '',
    buildingCondition: item.buildingCondition ?? '',
    deedType: attr(item, 'deedType'),
    ownershipStatus: attr(item, 'ownershipStatus'),
    cornerPosition: attr(item, 'cornerPosition'),
    landUse: attr(item, 'landUse'),
    registryMainNo: item.registryMainNo ?? '',
    registrySubNo: item.registrySubNo ?? '',
    registrySection: item.registrySection ?? '',
    postalCode: item.postalCode ?? '',
    latitude: item.latitude == null ? '' : String(item.latitude),
    longitude: item.longitude == null ? '' : String(item.longitude),
  }
}

function n(value: string) {
  const x = value.trim()
  return x === '' ? null : Number(x)
}

function i(value: string) {
  const x = n(value)
  return x == null ? null : Math.trunc(x)
}

function opts(items: OptionItem[]) {
  return items
    .filter((x) => x.value !== '')
    .map((x) => ({ value: x.value, label: x.label }))
}

function Field({
  label,
  children,
}: {
  label: string
  children: ReactNode
}) {
  return (
    <div className="sakhtyar-property-field">
      <label>{label}</label>
      {children}
    </div>
  )
}

export function PropertyPanel({
  caseId,
  caseItem,
}: {
  caseId: string
  caseItem: CaseItem
}) {
  const queryClient = useQueryClient()
  const { hasPermission } = useAuth()
  const canWrite = hasPermission('PROPERTY_WRITE')
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
        if (error instanceof ApiError && error.status === 404) return null
        throw error
      }
    },
    retry: false,
  })

  useEffect(() => {
    if (property.data) setForm(fromProperty(property.data))
    else if (property.data === null) setForm(fromCase(caseItem))
  }, [property.data, caseItem])

  const setField = (key: keyof PropertyForm, value: string) => {
    setSaved(false)
    setForm((current) => ({ ...current, [key]: value }))
  }

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
          landAreaM2: n(form.landAreaM2),
          frontageM: n(form.frontageM),
          passageWidthM: n(form.passageWidthM),
          buildingAreaM2: n(form.buildingAreaM2),
          constructionYear: i(form.constructionYear),
          existingFloors: i(form.existingFloors),
          existingUnits: i(form.existingUnits),
          orientation: form.orientation || null,
          propertyType: form.propertyType || null,
          buildingCondition: form.buildingCondition || null,
          registryMainNo: form.registryMainNo.trim() || null,
          registrySubNo: form.registrySubNo.trim() || null,
          registrySection: form.registrySection.trim() || null,
          postalCode: form.postalCode.trim() || null,
          latitude: n(form.latitude),
          longitude: n(form.longitude),
          attributes: {
            ...(property.data?.attributes ?? {}),
            deedType: form.deedType || null,
            ownershipStatus: form.ownershipStatus || null,
            cornerPosition: form.cornerPosition || null,
            landUse: form.landUse || null,
          },
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

  const invalidNumbers = useMemo(() => {
    const checks = [
      n(form.landAreaM2),
      n(form.frontageM),
      n(form.passageWidthM),
      n(form.buildingAreaM2),
    ]
    return checks.some((value) => value != null && (!Number.isFinite(value) || value <= 0))
  }, [form])

  const input = (
    key: keyof PropertyForm,
    label: string,
    rows?: number,
  ) => (
    <Field label={label}>
      {rows ? (
        <Input.TextArea
          rows={rows}
          value={form[key]}
          onChange={(e) => setField(key, e.target.value)}
        />
      ) : (
        <Input
          size="large"
          value={form[key]}
          onChange={(e) => setField(key, e.target.value)}
        />
      )}
    </Field>
  )

  const select = (
    key: keyof PropertyForm,
    label: string,
    values: OptionItem[],
  ) => (
    <Field label={label}>
      <Select
        size="large"
        allowClear
        style={{ width: '100%' }}
        value={form[key] || undefined}
        onChange={(value) => setField(key, value ?? '')}
        options={opts(values)}
      />
    </Field>
  )

  const location = (
    <div className="sakhtyar-property-location-grid">
      <Card
        className="sakhtyar-property-subcard sakhtyar-property-location-info"
        title={
          <Space>
            <EnvironmentOutlined />
            موقعیت و آدرس ملک
          </Space>
        }
      >
        <Row gutter={[12, 12]}>
          <Col xs={24} sm={12}>{input('province', 'استان')}</Col>
          <Col xs={24} sm={12}>{input('city', 'شهر')}</Col>
          <Col xs={24} sm={12}>{input('district', 'منطقه')}</Col>
          <Col xs={24} sm={12}>{input('neighborhood', 'محله')}</Col>
          <Col xs={24} sm={12}>{input('latitude', 'عرض جغرافیایی')}</Col>
          <Col xs={24} sm={12}>{input('longitude', 'طول جغرافیایی')}</Col>
          <Col span={24}>{input('address', 'آدرس کامل', 3)}</Col>
        </Row>
      </Card>

      <div className="sakhtyar-property-location-map">
        <PropertyMap caseId={caseId} compact />
      </div>
    </div>
  )

  const dimensions = (
    <Card className="sakhtyar-property-subcard">
      <Row gutter={[14, 14]}>
        <Col xs={24} sm={12} lg={8}>{input('landAreaM2', 'مساحت زمین (متر مربع)')}</Col>
        <Col xs={24} sm={12} lg={8}>{input('frontageM', 'بر ملک (متر)')}</Col>
        <Col xs={24} sm={12} lg={8}>{input('passageWidthM', 'عرض گذر (متر)')}</Col>
        <Col xs={24} sm={12} lg={8}>{input('buildingAreaM2', 'زیربنای موجود (متر مربع)')}</Col>
        <Col xs={24} sm={12} lg={8}>{input('constructionYear', 'سال ساخت')}</Col>
        <Col xs={24} sm={12} lg={8}>{input('existingFloors', 'تعداد طبقات موجود')}</Col>
        <Col xs={24} sm={12} lg={8}>{input('existingUnits', 'تعداد واحدهای موجود')}</Col>
        <Col xs={24} sm={12} lg={8}>{select('orientation', 'جهت ملک', orientationOptions)}</Col>
        <Col xs={24} sm={12} lg={8}>{select('cornerPosition', 'موقعیت ملک', cornerPositionOptions)}</Col>
      </Row>
    </Card>
  )

  const typeAndLegal = (
    <Card className="sakhtyar-property-subcard">
      <Row gutter={[14, 14]}>
        <Col xs={24} sm={12} lg={8}>{select('propertyType', 'نوع ملک', propertyTypeOptions)}</Col>
        <Col xs={24} sm={12} lg={8}>{select('buildingCondition', 'وضعیت بنا', buildingConditionOptions)}</Col>
        <Col xs={24} sm={12} lg={8}>{select('landUse', 'کاربری', landUseOptions)}</Col>
        <Col xs={24} sm={12} lg={8}>{select('deedType', 'نوع سند', deedTypeOptions)}</Col>
        <Col xs={24} sm={12} lg={8}>{select('ownershipStatus', 'وضعیت مالکیت', ownershipStatusOptions)}</Col>
      </Row>
    </Card>
  )

  const registry = (
    <Card className="sakhtyar-property-subcard">
      <Row gutter={[14, 14]}>
        <Col xs={24} sm={12}>{input('registryMainNo', 'پلاک اصلی')}</Col>
        <Col xs={24} sm={12}>{input('registrySubNo', 'پلاک فرعی')}</Col>
        <Col xs={24} sm={12}>{input('registrySection', 'بخش ثبتی')}</Col>
        <Col xs={24} sm={12}>{input('postalCode', 'کد پستی')}</Col>
      </Row>
    </Card>
  )

  const extra = (
    <Card className="sakhtyar-property-subcard">
      <Typography.Title level={4}>اطلاعات تکمیلی</Typography.Title>
      <Typography.Text type="secondary">
        فیلدهای انعطاف‌پذیر ذخیره‌شده در JSONB
        {property.data
          ? ` — نسخه ${property.data.attributesSchemaVersion}`
          : ''}
      </Typography.Text>
      <pre className="sakhtyar-json-preview">
        {JSON.stringify(property.data?.attributes ?? {}, null, 2)}
      </pre>
    </Card>
  )

  return (
    <Space direction="vertical" size={14} style={{ width: '100%' }}>
      {!canWrite ? (
        <Alert type="info" showIcon message="دسترسی شما فقط برای مشاهده مشخصات ملک است." />
      ) : null}

      {property.isError ? (
        <Alert type="error" showIcon message="دریافت مشخصات ملک ناموفق بود." />
      ) : null}

      {saved ? (
        <Alert type="success" showIcon message="مشخصات ملک با موفقیت ذخیره شد." />
      ) : null}

      {save.isError ? (
        <Alert
          type="error"
          showIcon
          message={
            save.error instanceof Error
              ? save.error.message
              : 'ذخیره مشخصات ملک ناموفق بود.'
          }
        />
      ) : null}

      {invalidNumbers ? (
        <Alert type="warning" showIcon message="یکی از مقادیر عددی واردشده معتبر نیست." />
      ) : null}

      <Card className="sakhtyar-property-tabs-card">
        <Tabs
          defaultActiveKey="location"
          size="large"
          tabBarExtraContent={
            <Button
              type="primary"
              icon={<SaveOutlined />}
              className="sakhtyar-primary-action"
              disabled={!canWrite || save.isPending || invalidNumbers}
              loading={save.isPending}
              onClick={() => save.mutate()}
            >
              ذخیره مشخصات
            </Button>
          }
          items={[
            {
              key: 'location',
              label: 'موقعیت و آدرس',
              icon: <EnvironmentOutlined />,
              children: location,
            },
            {
              key: 'dimensions',
              label: 'ابعاد و ویژگی‌ها',
              icon: <AppstoreOutlined />,
              children: dimensions,
            },
            {
              key: 'type',
              label: 'نوع ملک',
              icon: <HomeOutlined />,
              children: typeAndLegal,
            },
            {
              key: 'registry',
              label: 'اطلاعات ثبتی',
              icon: <FileTextOutlined />,
              children: registry,
            },
            {
              key: 'extra',
              label: 'اطلاعات تکمیلی',
              icon: <UnorderedListOutlined />,
              children: extra,
            },
          ]}
        />
      </Card>
    </Space>
  )
}
