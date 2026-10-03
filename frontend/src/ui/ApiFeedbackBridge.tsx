import { App as AntdApp } from 'antd'
import { useEffect } from 'react'
import type { ApiErrorDetail } from '../api/client'
import { useI18n } from '../i18n/LanguageProvider'

function localize(detail: ApiErrorDetail, language: 'fa' | 'en') {
  if (language === 'en') return detail.message

  const known: Record<string, string> = {
    'Total ownership shares cannot exceed 100 percent.':
      'جمع سهم مالکین نمی‌تواند بیشتر از ۱۰۰٪ باشد.',
    'Ownership numerator cannot be greater than denominator.':
      'صورت سهم نمی‌تواند از مخرج سهم بیشتر باشد.',
    'An owner with this national ID already exists for this property.':
      'برای این ملک قبلاً مالکی با این کد ملی ثبت شده است.',
    'Owner not found.':
      'مالک موردنظر پیدا نشد.',
    'Property not found for case. Save property information first.':
      'ابتدا مشخصات ملک این پرونده را ذخیره کنید.',
    'Authentication is required or the session is not valid.':
      'احراز هویت معتبر نیست یا نشست کاربری منقضی شده است.',
    'Access denied or CSRF validation failed.':
      'دسترسی درخواست رد شد یا اعتبارسنجی امنیتی CSRF ناموفق بود.',
    'One or more fields are invalid.':
      'یک یا چند مقدار واردشده معتبر نیست.',
  }

  return known[detail.message] ?? detail.message
}

export function ApiFeedbackBridge() {
  const { message } = AntdApp.useApp()
  const { language } = useI18n()

  useEffect(() => {
    const handler = (event: Event) => {
      const detail = (event as CustomEvent<ApiErrorDetail>).detail
      if (!detail) return
      message.error({
        content: localize(detail, language),
        duration: 5,
      })
    }

    window.addEventListener('sakhtyar:api-error', handler)
    return () => window.removeEventListener('sakhtyar:api-error', handler)
  }, [language, message])

  return null
}