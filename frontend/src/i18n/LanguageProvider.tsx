import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import enUS from 'antd/locale/en_US'
import faIR from 'antd/locale/fa_IR'
import { messages, type LanguageCode, type MessageKey } from './messages'

type Value = {
  language: LanguageCode
  direction: 'rtl' | 'ltr'
  antdLocale: typeof faIR
  setLanguage: (value: LanguageCode) => void
  t: (key: MessageKey) => string
  locale: string
}

const Ctx = createContext<Value | null>(null)
const STORAGE_KEY = 'sakhtyar.language'

export function LanguageProvider({ children }: { children: ReactNode }) {
  const [language, setLanguageState] = useState<LanguageCode>(() => {
    const saved = localStorage.getItem(STORAGE_KEY)
    return saved === 'en' ? 'en' : 'fa'
  })

  const setLanguage = (value: LanguageCode) => {
    localStorage.setItem(STORAGE_KEY, value)
    setLanguageState(value)
  }

  const direction = language === 'fa' ? 'rtl' : 'ltr'
  const locale = language === 'fa' ? 'fa-IR' : 'en-US'

  useEffect(() => {
    document.documentElement.lang = language
    document.documentElement.dir = direction
  }, [language, direction])

  const value = useMemo<Value>(() => ({
    language,
    direction,
    locale,
    antdLocale: language === 'fa' ? faIR : enUS,
    setLanguage,
    t: (key) => messages[language][key],
  }), [language, direction, locale])

  return <Ctx.Provider value={value}>{children}</Ctx.Provider>
}

export function useI18n() {
  const value = useContext(Ctx)
  if (!value) throw new Error('useI18n must be used inside LanguageProvider')
  return value
}