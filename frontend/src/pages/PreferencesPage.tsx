import {
  Alert,
  App as AntdApp,
  Button,
  Card,
  Col,
  Row,
  Select,
  Space,
  Statistic,
  Tag,
  Typography,
} from 'antd'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { ApiError, api } from '../api/client'
import { useAuth } from '../auth/AuthProvider'
import { useI18n } from '../i18n/LanguageProvider'
import { themeChoices, type ThemeCode } from '../ui/themePresets'

type Item = {
  id: string
  code?: string
  isoAlpha2?: string
  nameEn: string
  nameNative?: string | null
}

type Pref = {
  userId?: string
  languageId?: string | null
  countryId?: string | null
  currencyId?: string | null
  languageCode?: string | null
  countryCode?: string | null
  currencyCode?: string | null
  timezone?: string | null
  theme: ThemeCode
  dateFormat?: string | null
  numberFormat?: string | null
  firstDayOfWeek?: number | null
}

type MasterDataStatus = {
  running: boolean
  countryCount: number
  divisionCount: number
  cityCount: number
  currencyCount: number
  recentImports: Array<{
    dataset: string
    status: string
    recordCount: number
    startedAt?: string | null
    finishedAt?: string | null
    errorMessage?: string | null
  }>
}

function samePreference(a: Pref | null, b: Pref | null) {
  if (!a || !b) return false
  return (
    (a.languageId ?? null) === (b.languageId ?? null) &&
    (a.countryId ?? null) === (b.countryId ?? null) &&
    (a.currencyId ?? null) === (b.currencyId ?? null) &&
    (a.timezone ?? null) === (b.timezone ?? null) &&
    a.theme === b.theme &&
    (a.dateFormat ?? null) === (b.dateFormat ?? null) &&
    (a.numberFormat ?? null) === (b.numberFormat ?? null) &&
    (a.firstDayOfWeek ?? null) === (b.firstDayOfWeek ?? null)
  )
}

export function PreferencesPage() {
  const { message } = AntdApp.useApp()
  const queryClient = useQueryClient()
  const { t, language, setLanguage, theme, setTheme } = useI18n()
  const { hasPermission } = useAuth()
  const canAdmin = hasPermission('USER_MANAGE')

  const [draft, setDraft] = useState<Pref | null>(null)
  const [lastError, setLastError] = useState<string | null>(null)
  const [changeNotice, setChangeNotice] = useState<string | null>(null)

  const languages = useQuery({
    queryKey: ['global-languages'],
    queryFn: () => api<Item[]>('/api/v1/global/catalog/languages'),
  })

  const countries = useQuery({
    queryKey: ['global-countries'],
    queryFn: () => api<Item[]>('/api/v1/global/catalog/countries'),
  })

  const currencies = useQuery({
    queryKey: ['global-currencies'],
    queryFn: () => api<Item[]>('/api/v1/global/catalog/currencies'),
  })

  const pref = useQuery({
    queryKey: ['preferences-me'],
    queryFn: () => api<Pref>('/api/v1/global/preferences/me'),
  })

  useEffect(() => {
    if (!pref.data) return
    setDraft(pref.data)
    setLastError(null)
  }, [pref.data])

  const dirty = useMemo(
    () => Boolean(draft && pref.data && !samePreference(draft, pref.data)),
    [draft, pref.data],
  )

  useEffect(() => {
    const onBeforeUnload = (event: BeforeUnloadEvent) => {
      if (!dirty) return
      event.preventDefault()
      event.returnValue = ''
    }
    window.addEventListener('beforeunload', onBeforeUnload)
    return () => window.removeEventListener('beforeunload', onBeforeUnload)
  }, [dirty])

  const masterData = useQuery({
    queryKey: ['global-master-data-status'],
    enabled: canAdmin,
    refetchInterval: (q) => (q.state.data?.running ? 2500 : false),
    queryFn: () => api<MasterDataStatus>('/api/v1/global/master-data/status'),
  })

  const importMasterData = useMutation({
    mutationFn: () =>
      api<{ started: boolean; running: boolean }>(
        '/api/v1/global/master-data/import/geonames/async',
        { method: 'POST' },
      ),
    onSuccess: (result) => {
      void masterData.refetch()
      message.success(
        result.started
          ? language === 'fa'
            ? 'همگام‌سازی GeoNames شروع شد.'
            : 'GeoNames synchronization started.'
          : language === 'fa'
            ? 'همگام‌سازی GeoNames از قبل در حال اجراست.'
            : 'GeoNames synchronization is already running.',
      )
    },
    onError: (error) => {
      const text =
        error instanceof Error
          ? error.message
          : language === 'fa'
            ? 'شروع همگام‌سازی ناموفق بود.'
            : 'Failed to start synchronization.'
      message.error(text)
    },
  })

  const save = useMutation({
    mutationFn: async (value: Pref) => {
      const saved = await api<Pref>('/api/v1/global/preferences/me', {
        method: 'PUT',
        body: JSON.stringify({
          languageId: value.languageId ?? null,
          countryId: value.countryId ?? null,
          currencyId: value.currencyId ?? null,
          timezone: value.timezone ?? null,
          theme: value.theme,
          dateFormat: value.dateFormat ?? null,
          numberFormat: value.numberFormat ?? null,
          firstDayOfWeek: value.firstDayOfWeek ?? null,
        }),
      })

      // Explicit GET round-trip: a success response is not enough; verify what the server persisted.
      const verified = await api<Pref>('/api/v1/global/preferences/me')

      if (!samePreference(saved, verified)) {
        throw new Error(
          language === 'fa'
            ? 'تنظیمات ذخیره شد، اما نتیجه بازخوانی با مقدار ذخیره‌شده یکسان نیست.'
            : 'Settings were saved, but the read-back verification did not match.',
        )
      }

      return verified
    },
    onSuccess: (value) => {
      setLastError(null)
      setChangeNotice(null)
      setDraft(value)
      queryClient.setQueryData(['preferences-me'], value)

      if (value.languageCode === 'fa' || value.languageCode === 'en') {
        setLanguage(value.languageCode)
      }
      setTheme(value.theme)

      message.success(
        language === 'fa'
          ? 'تنظیمات با موفقیت ذخیره و تأیید شد.'
          : 'Settings were saved and verified successfully.',
      )
    },
    onError: (error) => {
      const text =
        error instanceof ApiError && error.status === 403
          ? language === 'fa'
            ? 'درخواست ذخیره توسط CSRF/Security رد شد. توکن امنیتی تازه‌سازی و درخواست دوباره امتحان شد، اما همچنان 403 دریافت شد.'
            : 'The save request was rejected by CSRF/Security. The security token was refreshed and retried, but the server still returned 403.'
          : error instanceof ApiError && error.status === 401
            ? language === 'fa'
              ? `ذخیره تنظیمات به علت احراز هویت رد شد: ${error.message}`
              : `Settings save was rejected by authentication: ${error.message}`
          : error instanceof ApiError
            ? `${error.message} (${error.status})`
          : error instanceof Error
            ? error.message
            : language === 'fa'
              ? 'ذخیره تنظیمات ناموفق بود.'
              : 'Failed to save settings.'
      setLastError(text)
      message.error(text, 6)
    },
  })

  const markChanged = (noticeFa: string, noticeEn: string) => {
    setLastError(null)
    setChangeNotice(language === 'fa' ? noticeFa : noticeEn)
  }

  const update = <K extends keyof Pref>(key: K, value: Pref[K]) => {
    setDraft((current) => (current ? { ...current, [key]: value } : current))
  }

  const changeLanguage = (languageId: string) => {
    update('languageId', languageId)
    const code = languages.data?.find((x) => x.id === languageId)?.code

    if (code === 'fa' || code === 'en') {
      setLanguage(code)
      setLastError(null)
      setChangeNotice(
        code === 'fa'
          ? 'زبان به‌صورت پیش‌نمایش تغییر کرد؛ برای ماندگار شدن روی «ذخیره و تأیید» بزنید.'
          : 'Language preview changed. Click Save & Verify to persist it.',
      )
      return
    }

    markChanged(
      'زبان تغییر کرد؛ برای ماندگار شدن ذخیره کنید.',
      'Language changed. Save to persist it.',
    )
  }

  const changeTheme = (nextTheme: ThemeCode) => {
    update('theme', nextTheme)
    setTheme(nextTheme)
    markChanged(
      'پوسته به‌صورت پیش‌نمایش اعمال شد؛ برای ماندگار شدن روی «ذخیره» بزنید.',
      'Theme preview applied. Click Save to persist it.',
    )
  }

  const resetDraft = () => {
    if (!pref.data) return
    setDraft(pref.data)
    setLastError(null)
    setChangeNotice(null)

    if (pref.data.languageCode === 'fa' || pref.data.languageCode === 'en') {
      setLanguage(pref.data.languageCode)
    }
    setTheme(pref.data.theme)

    message.info(
      language === 'fa'
        ? 'تغییرات ذخیره‌نشده لغو شد.'
        : 'Unsaved changes were discarded.',
    )
  }

  if (pref.isLoading || !draft) return <Card loading />

  return (
    <div className="sakhtyar-page-stack sakhtyar-settings-page">
      <Card className="sakhtyar-settings-card">
        <Space
          align="center"
          style={{ width: '100%', justifyContent: 'space-between', marginBottom: 14 }}
        >
          <Typography.Title level={2} style={{ margin: 0 }}>
            {t('settings')}
          </Typography.Title>
          {dirty ? (
            <Tag color="gold">
              {language === 'fa' ? 'تغییرات ذخیره نشده' : 'Unsaved changes'}
            </Tag>
          ) : (
            <Tag color="green">
              {language === 'fa' ? 'همگام با سرور' : 'Synced with server'}
            </Tag>
          )}
        </Space>

        {lastError ? (
          <Alert
            type="error"
            showIcon
            closable
            message={language === 'fa' ? 'ذخیره انجام نشد' : 'Save failed'}
            description={lastError}
            style={{ marginBottom: 14 }}
          />
        ) : null}

        {changeNotice && dirty ? (
          <Alert
            type="info"
            showIcon
            message={changeNotice}
            style={{ marginBottom: 14 }}
          />
        ) : null}

        <Row gutter={[14, 4]}>
          <Col xs={24} md={12}>
            <label className="sakhtyar-field-caption">{t('language')}</label>
            <Select
              value={draft.languageId ?? undefined}
              style={{ width: '100%' }}
              onChange={changeLanguage}
              options={(languages.data ?? []).map((x) => ({
                value: x.id,
                label: x.nameNative || x.nameEn,
              }))}
            />
          </Col>

          <Col xs={24} md={12}>
            <label className="sakhtyar-field-caption">{t('country')}</label>
            <Select
              showSearch
              allowClear
              optionFilterProp="label"
              value={draft.countryId ?? undefined}
              style={{ width: '100%' }}
              onChange={(value) => {
                update('countryId', value ?? null)
                markChanged(
                  'کشور پیش‌فرض تغییر کرد؛ برای ماندگار شدن ذخیره کنید.',
                  'Default country changed. Save to persist it.',
                )
              }}
              options={(countries.data ?? []).map((x) => ({
                value: x.id,
                label: x.nameNative || x.nameEn,
              }))}
            />
          </Col>

          <Col xs={24} md={12} style={{ marginTop: 14 }}>
            <label className="sakhtyar-field-caption">{t('currency')}</label>
            <Select
              showSearch
              optionFilterProp="label"
              value={draft.currencyId ?? undefined}
              style={{ width: '100%' }}
              onChange={(value) => {
                update('currencyId', value)
                markChanged(
                  'ارز پیش‌فرض تغییر کرد؛ برای ماندگار شدن ذخیره کنید.',
                  'Default currency changed. Save to persist it.',
                )
              }}
              options={(currencies.data ?? []).map((x) => ({
                value: x.id,
                label: `${x.code} — ${x.nameEn}`,
              }))}
            />
          </Col>

          <Col xs={24} md={12} style={{ marginTop: 14 }}>
            <label className="sakhtyar-field-caption">{t('timezone')}</label>
            <Select
              showSearch
              value={draft.timezone ?? undefined}
              style={{ width: '100%' }}
              onChange={(value) => {
                update('timezone', value)
                markChanged(
                  'منطقه زمانی تغییر کرد؛ برای ماندگار شدن ذخیره کنید.',
                  'Time zone changed. Save to persist it.',
                )
              }}
              options={Intl.supportedValuesOf('timeZone').map((x) => ({
                value: x,
                label: x,
              }))}
            />
          </Col>
        </Row>

        <div style={{ marginTop: 22 }}>
          <Typography.Text strong>{t('theme')}</Typography.Text>
          <Typography.Paragraph type="secondary" style={{ marginTop: 6 }}>
            {t('themeHint')}
          </Typography.Paragraph>

          <div className="sakhtyar-theme-grid">
            {themeChoices.map((item) => {
              const selected = draft.theme === item.code
              return (
                <button
                  type="button"
                  key={item.code}
                  className={`sakhtyar-theme-option${selected ? ' is-selected' : ''}`}
                  onClick={() => changeTheme(item.code)}
                >
                  <span className="sakhtyar-theme-swatches">
                    {item.swatches.map((color) => (
                      <i key={color} style={{ background: color }} />
                    ))}
                  </span>
                  <strong>{language === 'fa' ? item.labelFa : item.labelEn}</strong>
                </button>
              )
            })}
          </div>
        </div>

        <Space style={{ marginTop: 20 }}>
          <Button
            type="primary"
            disabled={!dirty || save.isPending}
            loading={save.isPending}
            onClick={() => save.mutate(draft)}
          >
            {language === 'fa' ? 'ذخیره و تأیید' : 'Save & Verify'}
          </Button>
          <Button disabled={!dirty || save.isPending} onClick={resetDraft}>
            {language === 'fa' ? 'لغو تغییرات' : 'Discard Changes'}
          </Button>
        </Space>
      </Card>

      {canAdmin ? (
        <Card className="sakhtyar-settings-card">
          <Typography.Title level={3}>{t('masterData')}</Typography.Title>
          <Typography.Paragraph type="secondary">
            {t('masterDataHint')}
          </Typography.Paragraph>

          <Row gutter={[12, 12]} style={{ marginBottom: 16 }}>
            <Col xs={12} md={6}>
              <Statistic title={t('countries')} value={masterData.data?.countryCount ?? 0} />
            </Col>
            <Col xs={12} md={6}>
              <Statistic title={t('divisions')} value={masterData.data?.divisionCount ?? 0} />
            </Col>
            <Col xs={12} md={6}>
              <Statistic title={t('cities')} value={masterData.data?.cityCount ?? 0} />
            </Col>
            <Col xs={12} md={6}>
              <Statistic title={t('currencies')} value={masterData.data?.currencyCount ?? 0} />
            </Col>
          </Row>

          <Button
            type="primary"
            loading={importMasterData.isPending || masterData.data?.running}
            disabled={Boolean(masterData.data?.running)}
            onClick={() => importMasterData.mutate()}
          >
            {masterData.data?.running ? t('importRunning') : t('startImport')}
          </Button>

          {masterData.data?.recentImports?.[0] ? (
            <Typography.Paragraph type="secondary" style={{ marginTop: 12 }}>
              {masterData.data.recentImports[0].dataset}
              {' — '}
              {masterData.data.recentImports[0].status}
              {' · '}
              {masterData.data.recentImports[0].recordCount.toLocaleString()}
            </Typography.Paragraph>
          ) : null}
        </Card>
      ) : null}
    </div>
  )
}