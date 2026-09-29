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

function attributeString(item: PropertyItem | null | undefined, key: string) {
  const value = item?.attributes?.[key]
  return typeof value === 'string' ? value : ''
}

function fromCase(item: CaseItem): PropertyForm {
  return {
    province: '', city: item.city ?? '', district: item.district ?? '', neighborhood: '', address: item.address ?? '',
    landAreaM2: item.landAreaM2 == null ? '' : String(item.landAreaM2), frontageM: '', passageWidthM: '', buildingAreaM2: '',
    constructionYear: '', existingFloors: '', existingUnits: '', orientation: '', propertyType: '', buildingCondition: '',
    deedType: '', ownershipStatus: '', cornerPosition: '', landUse: '', registryMainNo: '', registrySubNo: '', registrySection: '', postalCode: '', latitude: '', longitude: '',
  }
}

function fromProperty(item: PropertyItem): PropertyForm {
  return {
    province: item.province ?? '', city: item.city ?? '', district: item.district ?? '', neighborhood: item.neighborhood ?? '', address: item.address ?? '',
    landAreaM2: item.landAreaM2 == null ? '' : String(item.landAreaM2), frontageM: item.frontageM == null ? '' : String(item.frontageM),
    passageWidthM: item.passageWidthM == null ? '' : String(item.passageWidthM), buildingAreaM2: item.buildingAreaM2 == null ? '' : String(item.buildingAreaM2),
    constructionYear: item.constructionYear == null ? '' : String(item.constructionYear), existingFloors: item.existingFloors == null ? '' : String(item.existingFloors),
    existingUnits: item.existingUnits == null ? '' : String(item.existingUnits), orientation: item.orientation ?? '', propertyType: item.propertyType ?? '',
    buildingCondition: item.buildingCondition ?? '', deedType: attributeString(item, 'deedType'), ownershipStatus: attributeString(item, 'ownershipStatus'),
    cornerPosition: attributeString(item, 'cornerPosition'), landUse: attributeString(item, 'landUse'), registryMainNo: item.registryMainNo ?? '',
    registrySubNo: item.registrySubNo ?? '', registrySection: item.registrySection ?? '', postalCode: item.postalCode ?? '',
    latitude: item.latitude == null ? '' : String(item.latitude), longitude: item.longitude == null ? '' : String(item.longitude),
  }
}

function nullableNumber(value: string) { const trimmed = value.trim(); return trimmed === '' ? null : Number(trimmed) }
function nullableInteger(value: string) { const number = nullableNumber(value); return number == null ? null : Math.trunc(number) }
function selectOptions(items: OptionItem[]) { return items.map((item) => ({ value: item.value, label: item.label })) }
function Field({ label, children }: { label: string; children: ReactNode }) { return <div className="sakhtyar-property-field"><label>{label}</label>{children}</div> }

export function PropertyPanel({ caseId, caseItem }: { caseId: string; caseItem: CaseItem }) {
  const queryClient = useQueryClient()
  const { hasPermission } = useAuth()
  const canWrite = hasPermission('PROPERTY_WRITE')
  const [form, setForm] = useState<PropertyForm>(() => fromCase(caseItem))
  const [saved, setSaved] = useState(false)

  const property = useQuery({
    queryKey: ['property', caseId],
    queryFn: async (): Promise<PropertyItem | null> => {
      try { return await api<PropertyItem>(`/api/v1/cases/${caseId}/property`) }
      catch (error) { if (error instanceof ApiError && error.status === 404) return null; throw error }
    }, retry: false,
  })

  useEffect(() => {
    if (property.data) setForm(fromProperty(property.data))
    else if (property.data === null) setForm(fromCase(caseItem))
  }, [property.data, caseItem])

  const save = useMutation({
    mutationFn: () => api<PropertyItem>(`/api/v1/cases/${caseId}/property`, {
      method: 'PUT',
      body: JSON.stringify({
        province: form.province.trim() || null, city: form.city.trim() || null, district: form.district.trim() || null,
        neighborhood: form.neighborhood.trim() || null, address: form.address.trim() || null,
        landAreaM2: nullableNumber(form.landAreaM2), frontageM: nullableNumber(form.frontageM), passageWidthM: nullableNumber(form.passageWidthM),
        buildingAreaM2: nullableNumber(form.buildingAreaM2), constructionYear: nullableInteger(form.constructionYear), existingFloors: nullableInteger(form.existingFloors),
        existingUnits: nullableInteger(form.existingUnits), orientation: form.orientation || null, propertyType: form.propertyType || null,
        buildingCondition: form.buildingCondition || null, registryMainNo: form.registryMainNo.trim() || null, registrySubNo: form.registrySubNo.trim() || null,
        registrySection: form.registrySection.trim() || null, postalCode: form.postalCode.trim() || null, latitude: nullableNumber(form.latitude), longitude: nullableNumber(form.longitude),
        attributes: { ...(property.data?.attributes ?? {}), deedType: form.deedType || null, ownershipStatus: form.ownershipStatus || null, cornerPosition: form.cornerPosition || null, landUse: form.landUse || null },
      }),
    }),
    onSuccess: (savedProperty) => {
      setSaved(true); setForm(fromProperty(savedProperty)); queryClient.setQueryData(['property', caseId], savedProperty)
      queryClient.invalidateQueries({ queryKey: ['case', caseId] }); queryClient.invalidateQueries({ queryKey: ['cases'] }); queryClient.invalidateQueries({ queryKey: ['owners', caseId] })
      window.setTimeout(() => setSaved(false), 2500)
    },
  })

  const setField = (key: keyof PropertyForm, value: string) => { setSaved(false); setForm((current) => ({ ...current, [key]: value })) }

  const invalidNumbers = useMemo(() => {
    const landArea = nullableNumber(form.landAreaM2), frontage = nullableNumber(form.frontageM), passageWidth = nullableNumber(form.passageWidthM), buildingArea = nullableNumber(form.buildingAreaM2)
    const year = nullableInteger(form.constructionYear), floors = nullableInteger(form.existingFloors), units = nullableInteger(form.existingUnits)
    const latitude = nullableNumber(form.latitude), longitude = nullableNumber(form.longitude)
    return (landArea != null && (!Number.isFinite(landArea) || landArea <= 0)) || (frontage != null && (!Number.isFinite(frontage) || frontage <= 0)) ||
      (passageWidth != null && (!Number.isFinite(passageWidth) || passageWidth <= 0)) || (buildingArea != null && (!Number.isFinite(buildingArea) || buildingArea <= 0)) ||
      (year != null && (year < 1000 || year > 2500)) || (floors != null && (floors < 0 || floors > 200)) || (units != null && (units < 0 || units > 10000)) ||
      (latitude != null && (!Number.isFinite(latitude) || latitude < -90 || latitude > 90)) || (longitude != null && (!Number.isFinite(longitude) || longitude < -180 || longitude > 180))
  }, [form])

  const saveButton = <Button type="primary" icon={<SaveOutlined />} className="sakhtyar-primary-action" disabled={!canWrite || save.isPending || invalidNumbers} loading={save.isPending} onClick={() => save.mutate()}>ذخیره مشخصات</Button>

  const locationTab = (
    <Row gutter={[18, 18]} align="stretch">
      <Col xs={24} xl={10}>
        <Card className="sakhtyar-property-subcard" title={<Space><EnvironmentOutlined />موقعیت و آدرس ملک</Space>}>
          <Row gutter={[12, 12]}>
            <Col xs={24} sm={12}><Field label="استان"><Input size="large" value={form.province} onChange={(e) => setField('province', e.target.value)} /></Field></Col>
            <Col xs={24} sm={12}><Field label="شهر"><Input size="large" value={form.city} onChange={(e) => setField('city', e.target.value)} /></Field></Col>
            <Col xs={24} sm={12}><Field label="منطقه"><Input size="large" value={form.district} onChange={(e) => setField('district', e.target.value)} /></Field></Col>
            <Col xs={24} sm={12}><Field label="محله"><Input size="large" value={form.neighborhood} onChange={(e) => setField('neighborhood', e.target.value)} /></Field></Col>
            <Col xs={24} sm={12}><Field label="عرض جغرافیایی"><Input size="large" type="number" value={form.latitude} onChange={(e) => setField('latitude', e.target.value)} /></Field></Col>
            <Col xs={24} sm={12}><Field label="طول جغرافیایی"><Input size="large" type="number" value={form.longitude} onChange={(e) => setField('longitude', e.target.value)} /></Field></Col>
            <Col span={24}><Field label="آدرس کامل"><Input.TextArea rows={3} value={form.address} onChange={(e) => setField('address', e.target.value)} /></Field></Col>
          </Row>
        </Card>
      </Col>
      <Col xs={24} xl={14}><PropertyMap caseId={caseId} compact /></Col>
    </Row>
  )

  const dimensionsTab = (
    <Card className="sakhtyar-property-subcard"><Row gutter={[14, 14]}>
      {[
        ['مساحت زمین (متر مربع)', 'landAreaM2'], ['بر ملک (متر)', 'frontageM'], ['عرض گذر (متر)', 'passageWidthM'], ['زیربنای موجود (متر مربع)', 'buildingAreaM2'],
        ['سال ساخت', 'constructionYear'], ['تعداد طبقات موجود', 'existingFloors'], ['تعداد واحدهای موجود', 'existingUnits'],
      ].map(([label, key]) => <Col xs={24} sm={12} lg={8} key={key}><Field label={label}><Input size="large" type="number" value={form[key as keyof PropertyForm]} onChange={(e) => setField(key as keyof PropertyForm, e.target.value)} /></Field></Col>)}
      <Col xs={24} sm={12} lg={8}><Field label="جهت ملک"><Select size="large" style={{ width: '100%' }} value={form.orientation || undefined} onChange={(value) => setField('orientation', value ?? '')} options={selectOptions(orientationOptions)} /></Field></Col>
      <Col xs={24} sm={12} lg={8}><Field label="موقعیت ملک"><Select size="large" style={{ width: '100%' }} value={form.cornerPosition || undefined} onChange={(value) => setField('cornerPosition', value ?? '')} options={selectOptions(cornerPositionOptions)} /></Field></Col>
    </Row></Card>
  )

  const typeTab = (
    <Card className="sakhtyar-property-subcard"><Row gutter={[14, 14]}>
      <Col xs={24} sm={12} lg={8}><Field label="نوع ملک"><Select size="large" style={{ width: '100%' }} value={form.propertyType || undefined} onChange={(value) => setField('propertyType', value ?? '')} options={selectOptions(propertyTypeOptions)} /></Field></Col>
      <Col xs={24} sm={12} lg={8}><Field label="وضعیت بنا"><Select size="large" style={{ width: '100%' }} value={form.buildingCondition || undefined} onChange={(value) => setField('buildingCondition', value ?? '')} options={selectOptions(buildingConditionOptions)} /></Field></Col>
      <Col xs={24} sm={12} lg={8}><Field label="کاربری"><Select size="large" style={{ width: '100%' }} value={form.landUse || undefined} onChange={(value) => setField('landUse', value ?? '')} options={selectOptions(landUseOptions)} /></Field></Col>
      <Col xs={24} sm={12} lg={8}><Field label="نوع سند"><Select size="large" style={{ width: '100%' }} value={form.deedType || undefined} onChange={(value) => setField('deedType', value ?? '')} options={selectOptions(deedTypeOptions)} /></Field></Col>
      <Col xs={24} sm={12} lg={8}><Field label="وضعیت مالکیت"><Select size="large" style={{ width: '100%' }} value={form.ownershipStatus || undefined} onChange={(value) => setField('ownershipStatus', value ?? '')} options={selectOptions(ownershipStatusOptions)} /></Field></Col>
    </Row></Card>
  )

  const registryTab = (
    <Card className="sakhtyar-property-subcard"><Row gutter={[14, 14]}>
      {[
        ['پلاک اصلی', 'registryMainNo'], ['پلاک فرعی', 'registrySubNo'], ['بخش ثبتی', 'registrySection'], ['کد پستی', 'postalCode'],
      ].map(([label, key]) => <Col xs={24} sm={12} key={key}><Field label={label}><Input size="large" value={form[key as keyof PropertyForm]} onChange={(e) => setField(key as keyof PropertyForm, e.target.value)} /></Field></Col>)}
    </Row></Card>
  )

  const extraTab = (
    <Card className="sakhtyar-property-subcard"><Space direction="vertical" size={14} style={{ width: '100%' }}>
      <Typography.Title level={4}>اطلاعات تکمیلی</Typography.Title>
      <Typography.Text type="secondary">فیلدهای انعطاف‌پذیر ذخیره‌شده در JSONB{property.data ? ` — نسخه ${property.data.attributesSchemaVersion}` : ''}</Typography.Text>
      <pre className="sakhtyar-json-preview">{JSON.stringify(property.data?.attributes ?? {}, null, 2)}</pre>
    </Space></Card>
  )

  const tabItems = [
    { key: 'location', label: 'موقعیت و آدرس', icon: <EnvironmentOutlined />, children: locationTab },
    { key: 'dimensions', label: 'ابعاد و ویژگی‌ها', icon: <AppstoreOutlined />, children: dimensionsTab },
    { key: 'type', label: 'نوع ملک', icon: <HomeOutlined />, children: typeTab },
    { key: 'registry', label: 'اطلاعات ثبتی', icon: <FileTextOutlined />, children: registryTab },
    { key: 'extra', label: 'اطلاعات تکمیلی', icon: <UnorderedListOutlined />, children: extraTab },
  ]

  return (
    <Space direction="vertical" size={14} style={{ width: '100%' }}>
      {!canWrite ? <Alert type="info" showIcon message="دسترسی شما فقط برای مشاهده مشخصات ملک است." /> : null}
      {property.isError ? <Alert type="error" showIcon message="دریافت مشخصات ملک ناموفق بود." /> : null}
      {property.data === null ? <Alert type="info" showIcon message="برای این پرونده هنوز رکورد ملک ایجاد نشده است. با ذخیره فرم، رکورد Property ایجاد می‌شود." /> : null}
      {saved ? <Alert type="success" showIcon message="مشخصات ملک با موفقیت ذخیره شد." /> : null}
      {save.isError ? <Alert type="error" showIcon message={save.error instanceof Error ? save.error.message : 'ذخیره مشخصات ملک ناموفق بود.'} /> : null}
      {invalidNumbers ? <Alert type="warning" showIcon message="یکی از مقادیر عددی واردشده معتبر نیست." /> : null}
      <Card className="sakhtyar-property-tabs-card"><Tabs defaultActiveKey="location" items={tabItems} size="large" tabBarExtraContent={saveButton} /></Card>
    </Space>
  )
}
