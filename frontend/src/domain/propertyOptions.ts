export type OptionItem = {
  value: string
  label: string
}

export const orientationOptions: OptionItem[] = [
  { value: '', label: 'نامشخص' },
  { value: 'NORTH', label: 'شمالی' },
  { value: 'SOUTH', label: 'جنوبی' },
  { value: 'EAST', label: 'شرقی' },
  { value: 'WEST', label: 'غربی' },
  { value: 'NORTH_EAST', label: 'شمال شرقی' },
  { value: 'NORTH_WEST', label: 'شمال غربی' },
  { value: 'SOUTH_EAST', label: 'جنوب شرقی' },
  { value: 'SOUTH_WEST', label: 'جنوب غربی' },
]

export const propertyTypeOptions: OptionItem[] = [
  { value: '', label: 'انتخاب کنید' },
  { value: 'LAND', label: 'زمین' },
  { value: 'OLD_BUILDING', label: 'کلنگی' },
  { value: 'RESIDENTIAL_BUILDING', label: 'ساختمان مسکونی' },
  { value: 'APARTMENT', label: 'آپارتمان' },
  { value: 'VILLA', label: 'ویلایی' },
  { value: 'COMMERCIAL', label: 'تجاری' },
  { value: 'OFFICE', label: 'اداری' },
  { value: 'MIXED_USE', label: 'مختلط' },
  { value: 'INDUSTRIAL', label: 'صنعتی' },
  { value: 'GARDEN', label: 'باغ / باغچه' },
  { value: 'OTHER', label: 'سایر' },
]

export const buildingConditionOptions: OptionItem[] = [
  { value: '', label: 'انتخاب کنید' },
  { value: 'VACANT_LAND', label: 'زمین خالی' },
  { value: 'DEMOLITION_CANDIDATE', label: 'کلنگی / مناسب تخریب' },
  { value: 'NEEDS_RENOVATION', label: 'نیازمند بازسازی' },
  { value: 'HABITABLE', label: 'قابل سکونت' },
  { value: 'RENOVATED', label: 'بازسازی‌شده' },
  { value: 'NEW_BUILD', label: 'نوساز' },
  { value: 'UNDER_CONSTRUCTION', label: 'در حال ساخت' },
  { value: 'OTHER', label: 'سایر' },
]

export const deedTypeOptions: OptionItem[] = [
  { value: '', label: 'انتخاب کنید' },
  { value: 'SINGLE_PAGE', label: 'سند تک‌برگ' },
  { value: 'BOOKLET', label: 'سند دفترچه‌ای / منگوله‌دار' },
  { value: 'AGREEMENT', label: 'قولنامه‌ای' },
  { value: 'POWER_OF_ATTORNEY', label: 'وکالتی' },
  { value: 'ENDOWMENT', label: 'اوقافی' },
  { value: 'COOPERATIVE', label: 'تعاونی' },
  { value: 'OTHER', label: 'سایر' },
]

export const ownershipStatusOptions: OptionItem[] = [
  { value: '', label: 'انتخاب کنید' },
  { value: 'SIX_DANG', label: 'شش‌دانگ' },
  { value: 'SHARED', label: 'مشاع' },
  { value: 'PARTIAL', label: 'سهمی / دانگی' },
  { value: 'OTHER', label: 'سایر' },
]

export const cornerPositionOptions: OptionItem[] = [
  { value: '', label: 'انتخاب کنید' },
  { value: 'MID_BLOCK', label: 'میان‌قطعه / یک‌بر' },
  { value: 'CORNER', label: 'نبش / دوبر' },
  { value: 'THREE_FRONT', label: 'سه‌بر' },
  { value: 'FOUR_FRONT', label: 'چهاربر' },
  { value: 'OTHER', label: 'سایر' },
]

export const landUseOptions: OptionItem[] = [
  { value: '', label: 'انتخاب کنید' },
  { value: 'RESIDENTIAL', label: 'مسکونی' },
  { value: 'COMMERCIAL', label: 'تجاری' },
  { value: 'OFFICE', label: 'اداری' },
  { value: 'MIXED', label: 'مختلط' },
  { value: 'INDUSTRIAL', label: 'صنعتی' },
  { value: 'GARDEN', label: 'باغ / فضای سبز' },
  { value: 'WAREHOUSE', label: 'انبار' },
  { value: 'OTHER', label: 'سایر' },
]
