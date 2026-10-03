import { GlobalOutlined } from '@ant-design/icons'
import { Select } from 'antd'
import { useI18n } from '../i18n/LanguageProvider'

export function LanguageSwitcher() {
  const { language, setLanguage, t } = useI18n()
  return (
    <Select
      aria-label={t('language')}
      prefix={<GlobalOutlined />}
      value={language}
      onChange={setLanguage}
      options={[
        { value: 'fa', label: 'فارسی' },
        { value: 'en', label: 'English' },
      ]}
      style={{ minWidth: 120 }}
    />
  )
}