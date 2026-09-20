import ApartmentRoundedIcon from '@mui/icons-material/ApartmentRounded'
import DescriptionRoundedIcon from '@mui/icons-material/DescriptionRounded'
import LocationOnRoundedIcon from '@mui/icons-material/LocationOnRounded'
import SaveRoundedIcon from '@mui/icons-material/SaveRounded'
import StraightenRoundedIcon from '@mui/icons-material/StraightenRounded'
import {
  Alert,
  Box,
  Button,
  Grid,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { ApiError, api } from '../../api/client'
import type { CaseItem, PropertyItem } from '../../api/types'
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
import { FieldGroup } from '../../ui/FieldGroup'
import {
  FieldCell,
  ResponsiveFieldGrid,
} from '../../ui/ResponsiveFieldGrid'
import { SectionCard } from '../../ui/SectionCard'

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

function attributeString(
  item: PropertyItem | null | undefined,
  key: string,
) {
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
    deedType: attributeString(item, 'deedType'),
    ownershipStatus: attributeString(item, 'ownershipStatus'),
    cornerPosition: attributeString(item, 'cornerPosition'),
    landUse: attributeString(item, 'landUse'),
    registryMainNo: item.registryMainNo ?? '',
    registrySubNo: item.registrySubNo ?? '',
    registrySection: item.registrySection ?? '',
    postalCode: item.postalCode ?? '',
    latitude: item.latitude == null ? '' : String(item.latitude),
    longitude: item.longitude == null ? '' : String(item.longitude),
  }
}

function nullableNumber(value: string) {
  const trimmed = value.trim()
  return trimmed === '' ? null : Number(trimmed)
}

function nullableInteger(value: string) {
  const number = nullableNumber(value)
  return number == null ? null : Math.trunc(number)
}

function SelectField({
  label,
  value,
  onChange,
  options,
}: {
  label: string
  value: string
  onChange: (value: string) => void
  options: OptionItem[]
}) {
  return (
    <TextField
      select
      fullWidth
      label={label}
      value={value}
      onChange={(event) => onChange(event.target.value)}
    >
      {options.map((option) => (
        <MenuItem
          key={option.value || `${label}-empty`}
          value={option.value}
        >
          {option.label}
        </MenuItem>
      ))}
    </TextField>
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
  const [form, setForm] = useState<PropertyForm>(() => fromCase(caseItem))
  const [saved, setSaved] = useState(false)

  const property = useQuery({
    queryKey: ['property', caseId],
    queryFn: async (): Promise<PropertyItem | null> => {
      try {
        return await api<PropertyItem>(`/api/v1/cases/${caseId}/property`)
      } catch (error) {
        if (error instanceof ApiError && error.status === 404) return null
        throw error
      }
    },
    retry: false,
  })

  useEffect(() => {
    if (property.data) {
      setForm(fromProperty(property.data))
    } else if (property.data === null) {
      setForm(fromCase(caseItem))
    }
  }, [property.data, caseItem])

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
          landAreaM2: nullableNumber(form.landAreaM2),
          frontageM: nullableNumber(form.frontageM),
          passageWidthM: nullableNumber(form.passageWidthM),
          buildingAreaM2: nullableNumber(form.buildingAreaM2),
          constructionYear: nullableInteger(form.constructionYear),
          existingFloors: nullableInteger(form.existingFloors),
          existingUnits: nullableInteger(form.existingUnits),
          orientation: form.orientation || null,
          propertyType: form.propertyType || null,
          buildingCondition: form.buildingCondition || null,
          registryMainNo: form.registryMainNo.trim() || null,
          registrySubNo: form.registrySubNo.trim() || null,
          registrySection: form.registrySection.trim() || null,
          postalCode: form.postalCode.trim() || null,
          latitude: nullableNumber(form.latitude),
          longitude: nullableNumber(form.longitude),
          attributes: {
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

  const setField = (key: keyof PropertyForm, value: string) => {
    setSaved(false)
    setForm((current) => ({ ...current, [key]: value }))
  }

  const landArea = nullableNumber(form.landAreaM2)
  const frontage = nullableNumber(form.frontageM)
  const passageWidth = nullableNumber(form.passageWidthM)
  const buildingArea = nullableNumber(form.buildingAreaM2)
  const year = nullableInteger(form.constructionYear)
  const floors = nullableInteger(form.existingFloors)
  const units = nullableInteger(form.existingUnits)
  const latitude = nullableNumber(form.latitude)
  const longitude = nullableNumber(form.longitude)

  const invalidNumbers =
    (landArea != null && (!Number.isFinite(landArea) || landArea <= 0)) ||
    (frontage != null && (!Number.isFinite(frontage) || frontage <= 0)) ||
    (passageWidth != null &&
      (!Number.isFinite(passageWidth) || passageWidth <= 0)) ||
    (buildingArea != null &&
      (!Number.isFinite(buildingArea) || buildingArea <= 0)) ||
    (year != null && (year < 1000 || year > 2500)) ||
    (floors != null && (floors < 0 || floors > 200)) ||
    (units != null && (units < 0 || units > 10000)) ||
    (latitude != null &&
      (!Number.isFinite(latitude) || latitude < -90 || latitude > 90)) ||
    (longitude != null &&
      (!Number.isFinite(longitude) || longitude < -180 || longitude > 180))

  return (
    <Stack spacing={2}>
      {property.isError && (
        <Alert severity="error">
          دریافت مشخصات ملک ناموفق بود.
        </Alert>
      )}

      {property.data === null && (
        <Alert severity="info">
          برای این پرونده هنوز رکورد ملک ایجاد نشده است. با ذخیره فرم،
          رکورد Property ایجاد می‌شود.
        </Alert>
      )}

      {saved && (
        <Alert severity="success">
          مشخصات ملک با موفقیت ذخیره شد.
        </Alert>
      )}

      {save.isError && (
        <Alert severity="error">
          {save.error instanceof Error
            ? save.error.message
            : 'ذخیره مشخصات ملک ناموفق بود.'}
        </Alert>
      )}

      <SectionCard
        title="موقعیت و آدرس"
        description="اطلاعات مکانی و ثبتی پایه ملک"
        icon={<LocationOnRoundedIcon />}
      >
        <FieldGroup
          title="موقعیت"
          description="اطلاعاتی که در نقشه، جستجو و گزارش‌ها استفاده می‌شوند."
        >
          <ResponsiveFieldGrid>
            <FieldCell>
              <TextField
                fullWidth
                label="استان"
                value={form.province}
                onChange={(e) => setField('province', e.target.value)}
              />
            </FieldCell>
            <FieldCell>
              <TextField
                fullWidth
                label="شهر"
                value={form.city}
                onChange={(e) => setField('city', e.target.value)}
              />
            </FieldCell>
            <FieldCell>
              <TextField
                fullWidth
                label="منطقه"
                value={form.district}
                onChange={(e) => setField('district', e.target.value)}
              />
            </FieldCell>
            <FieldCell>
              <TextField
                fullWidth
                label="محله"
                value={form.neighborhood}
                onChange={(e) => setField('neighborhood', e.target.value)}
              />
            </FieldCell>
            <FieldCell>
              <TextField
                fullWidth
                label="عرض جغرافیایی"
                type="number"
                value={form.latitude}
                onChange={(e) => setField('latitude', e.target.value)}
              />
            </FieldCell>
            <FieldCell>
              <TextField
                fullWidth
                label="طول جغرافیایی"
                type="number"
                value={form.longitude}
                onChange={(e) => setField('longitude', e.target.value)}
              />
            </FieldCell>
            <FieldCell wide>
              <TextField
                fullWidth
                label="آدرس کامل"
                multiline
                minRows={3}
                value={form.address}
                onChange={(e) => setField('address', e.target.value)}
              />
            </FieldCell>
          </ResponsiveFieldGrid>
        </FieldGroup>
      </SectionCard>

      <SectionCard
        title="ابعاد و ویژگی‌های ساختمانی"
        description="ورودی‌های اصلی تحلیل ساخت، تراکم و ارزش ملک"
        icon={<StraightenRoundedIcon />}
      >
        <ResponsiveFieldGrid>
          <FieldCell>
            <TextField
              fullWidth
              type="number"
              label="مساحت زمین (متر مربع)"
              value={form.landAreaM2}
              onChange={(e) => setField('landAreaM2', e.target.value)}
            />
          </FieldCell>

          <FieldCell>
            <TextField
              fullWidth
              type="number"
              label="بر ملک (متر)"
              value={form.frontageM}
              onChange={(e) => setField('frontageM', e.target.value)}
            />
          </FieldCell>

          <FieldCell>
            <TextField
              fullWidth
              type="number"
              label="عرض گذر (متر)"
              value={form.passageWidthM}
              onChange={(e) => setField('passageWidthM', e.target.value)}
            />
          </FieldCell>

          <FieldCell>
            <TextField
              fullWidth
              type="number"
              label="زیربنای موجود (متر مربع)"
              value={form.buildingAreaM2}
              onChange={(e) => setField('buildingAreaM2', e.target.value)}
            />
          </FieldCell>

          <FieldCell>
            <TextField
              fullWidth
              type="number"
              label="سال ساخت"
              value={form.constructionYear}
              onChange={(e) => setField('constructionYear', e.target.value)}
            />
          </FieldCell>

          <FieldCell>
            <TextField
              fullWidth
              type="number"
              label="تعداد طبقات موجود"
              value={form.existingFloors}
              onChange={(e) => setField('existingFloors', e.target.value)}
            />
          </FieldCell>

          <FieldCell>
            <TextField
              fullWidth
              type="number"
              label="تعداد واحدهای موجود"
              value={form.existingUnits}
              onChange={(e) => setField('existingUnits', e.target.value)}
            />
          </FieldCell>

          <FieldCell>
            <SelectField
              label="جهت ملک"
              value={form.orientation}
              onChange={(value) => setField('orientation', value)}
              options={orientationOptions}
            />
          </FieldCell>

          <FieldCell>
            <SelectField
              label="موقعیت ملک"
              value={form.cornerPosition}
              onChange={(value) => setField('cornerPosition', value)}
              options={cornerPositionOptions}
            />
          </FieldCell>
        </ResponsiveFieldGrid>
      </SectionCard>

      <SectionCard
        title="نوع ملک و وضعیت حقوقی"
        description="اطلاعات طبقه‌بندی‌شده برای تحلیل، فیلتر و قرارداد"
        icon={<ApartmentRoundedIcon />}
      >
        <ResponsiveFieldGrid>
          <FieldCell>
            <SelectField
              label="نوع ملک"
              value={form.propertyType}
              onChange={(value) => setField('propertyType', value)}
              options={propertyTypeOptions}
            />
          </FieldCell>

          <FieldCell>
            <SelectField
              label="وضعیت بنا"
              value={form.buildingCondition}
              onChange={(value) => setField('buildingCondition', value)}
              options={buildingConditionOptions}
            />
          </FieldCell>

          <FieldCell>
            <SelectField
              label="کاربری"
              value={form.landUse}
              onChange={(value) => setField('landUse', value)}
              options={landUseOptions}
            />
          </FieldCell>

          <FieldCell>
            <SelectField
              label="نوع سند"
              value={form.deedType}
              onChange={(value) => setField('deedType', value)}
              options={deedTypeOptions}
            />
          </FieldCell>

          <FieldCell>
            <SelectField
              label="وضعیت مالکیت"
              value={form.ownershipStatus}
              onChange={(value) => setField('ownershipStatus', value)}
              options={ownershipStatusOptions}
            />
          </FieldCell>
        </ResponsiveFieldGrid>
      </SectionCard>

      <SectionCard
        title="اطلاعات ثبتی"
        description="اطلاعات رسمی پرونده و شناسه‌های ملک"
        icon={<DescriptionRoundedIcon />}
      >
        <ResponsiveFieldGrid>
          <FieldCell>
            <TextField
              fullWidth
              label="پلاک اصلی"
              value={form.registryMainNo}
              onChange={(e) => setField('registryMainNo', e.target.value)}
            />
          </FieldCell>

          <FieldCell>
            <TextField
              fullWidth
              label="پلاک فرعی"
              value={form.registrySubNo}
              onChange={(e) => setField('registrySubNo', e.target.value)}
            />
          </FieldCell>

          <FieldCell>
            <TextField
              fullWidth
              label="بخش ثبتی"
              value={form.registrySection}
              onChange={(e) => setField('registrySection', e.target.value)}
            />
          </FieldCell>

          <FieldCell>
            <TextField
              fullWidth
              label="کد پستی"
              value={form.postalCode}
              onChange={(e) => setField('postalCode', e.target.value)}
            />
          </FieldCell>
        </ResponsiveFieldGrid>
      </SectionCard>

      {invalidNumbers && (
        <Alert severity="warning">
          یکی از مقادیر عددی واردشده معتبر نیست.
        </Alert>
      )}

      <Box
        sx={{
          position: { xs: 'sticky', sm: 'static' },
          bottom: { xs: 76, sm: 'auto' },
          zIndex: 5,
          p: { xs: 1, sm: 0 },
          mx: { xs: -1, sm: 0 },
          borderRadius: 2,
          bgcolor: { xs: 'rgba(246,248,252,.96)', sm: 'transparent' },
          backdropFilter: { xs: 'blur(10px)', sm: 'none' },
        }}
      >
        <Stack direction="row" justifyContent="flex-end">
          <Button
            variant="contained"
            size="large"
            startIcon={<SaveRoundedIcon />}
            onClick={() => save.mutate()}
            disabled={save.isPending || invalidNumbers}
            fullWidth={false}
            sx={{ minWidth: { xs: '100%', sm: 210 } }}
          >
            {save.isPending ? 'در حال ذخیره...' : 'ذخیره مشخصات ملک'}
          </Button>
        </Stack>
      </Box>

      {property.data &&
        Object.keys(property.data.attributes ?? {}).length > 0 && (
          <SectionCard
            title="اطلاعات تکمیلی"
            description={`فیلدهای انعطاف‌پذیر ذخیره‌شده در JSONB — نسخه ${property.data.attributesSchemaVersion}`}
          >
            <Box
              component="pre"
              dir="ltr"
              sx={{
                m: 0,
                p: 2,
                borderRadius: 2,
                bgcolor: 'action.hover',
                overflow: 'auto',
                fontSize: 13,
                maxHeight: 320,
              }}
            >
              {JSON.stringify(property.data.attributes, null, 2)}
            </Box>
          </SectionCard>
        )}
    </Stack>
  )
}
