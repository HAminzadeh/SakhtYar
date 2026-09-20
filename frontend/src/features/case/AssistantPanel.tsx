import CheckRoundedIcon from '@mui/icons-material/CheckRounded'
import PsychologyRoundedIcon from '@mui/icons-material/PsychologyRounded'
import SaveRoundedIcon from '@mui/icons-material/SaveRounded'
import SendRoundedIcon from '@mui/icons-material/SendRounded'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Divider,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { api } from '../../api/client'
import type {
  AgentMessage,
  AgentWorkflowResult,
  GlossaryEntry,
  PropertyItem,
} from '../../api/types'

function conversationIdFor(caseId: string) {
  const key = `sakhtyar-agent-conversation:${caseId}`
  const existing = window.localStorage.getItem(key)
  if (existing) return existing
  const id = crypto.randomUUID()
  window.localStorage.setItem(key, id)
  return id
}

function parseWorkflow(payloadText?: string | null) {
  if (!payloadText) return null
  try {
    return JSON.parse(payloadText) as AgentWorkflowResult
  } catch {
    return null
  }
}

function stringArray(value: unknown) {
  if (!Array.isArray(value)) return []
  return value.filter((item): item is string => typeof item === 'string')
}

function record(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return {}
  return value as Record<string, unknown>
}

function statusColor(status: AgentWorkflowResult['status']) {
  switch (status) {
    case 'SUCCESS':
      return 'success' as const
    case 'NEEDS_INPUT':
      return 'warning' as const
    case 'FAILED':
      return 'error' as const
    default:
      return 'info' as const
  }
}

export function AssistantPanel({ caseId }: { caseId: string }) {
  const queryClient = useQueryClient()
  const [conversationId] = useState(() => conversationIdFor(caseId))
  const [message, setMessage] = useState('')
  const [lastResult, setLastResult] = useState<AgentWorkflowResult | null>(null)
  const [definitions, setDefinitions] = useState<Record<string, string>>({})
  const [drafts, setDrafts] = useState<Record<string, GlossaryEntry>>({})
  const [propertyApplied, setPropertyApplied] = useState(false)

  const history = useQuery({
    queryKey: ['agent-messages', conversationId],
    queryFn: () =>
      api<AgentMessage[]>(
        `/api/v1/agents/conversations/${conversationId}/messages`,
      ),
  })

  const effectiveResult = useMemo(() => {
    if (lastResult) return lastResult
    const messages = history.data ?? []
    for (let index = messages.length - 1; index >= 0; index -= 1) {
      if (messages[index].role === 'ASSISTANT') {
        const parsed = parseWorkflow(messages[index].payloadText)
        if (parsed) return parsed
      }
    }
    return null
  }, [history.data, lastResult])

  const chat = useMutation({
    mutationFn: (text: string) =>
      api<AgentWorkflowResult>('/api/v1/agents/chat', {
        method: 'POST',
        body: JSON.stringify({
          conversationId,
          caseId,
          message: text,
          parameters: {},
        }),
      }),
    onSuccess: (result) => {
      setLastResult(result)
      setMessage('')
      setPropertyApplied(false)
      queryClient.invalidateQueries({
        queryKey: ['agent-messages', conversationId],
      })
    },
  })

  const createDraft = useMutation({
    mutationFn: ({ term, meaning }: { term: string; meaning: string }) =>
      api<GlossaryEntry>('/api/v1/agents/glossary/drafts', {
        method: 'POST',
        body: JSON.stringify({ term, meaning, aliases: [] }),
      }),
    onSuccess: (entry) => {
      setDrafts((current) => ({ ...current, [entry.term]: entry }))
    },
  })

  const approveDraft = useMutation({
    mutationFn: (entry: GlossaryEntry) =>
      api<GlossaryEntry>(`/api/v1/agents/glossary/${entry.id}/approve`, {
        method: 'POST',
      }),
    onSuccess: (entry) => {
      setDrafts((current) => ({ ...current, [entry.term]: entry }))
    },
  })

  const persianData = record(effectiveResult?.results.PERSIAN?.data)
  const normalizedParameters = record(persianData.normalizedParameters)
  const unknownTerms = stringArray(persianData.unknownTerms)

  const applyToProperty = useMutation({
    mutationFn: () =>
      api<PropertyItem>(`/api/v1/cases/${caseId}/property/facts`, {
        method: 'PATCH',
        body: JSON.stringify({
          facts: normalizedParameters,
        }),
      }),
    onSuccess: (saved) => {
      setPropertyApplied(true)
      queryClient.setQueryData(['property', caseId], saved)
      queryClient.invalidateQueries({ queryKey: ['case', caseId] })
      queryClient.invalidateQueries({ queryKey: ['cases'] })
      queryClient.invalidateQueries({ queryKey: ['owners', caseId] })
    },
  })

  const submit = () => {
    const text = message.trim()
    if (!text || chat.isPending) return
    chat.mutate(text)
  }

  return (
    <Stack spacing={2}>
      <Card variant="outlined">
        <CardContent>
          <Stack spacing={2}>
            <Stack direction="row" spacing={1} alignItems="center">
              <PsychologyRoundedIcon color="primary" />
              <Box>
                <Typography variant="h6" fontWeight={800}>
                  دستیار هوشمند پرونده
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Persian Agent اطلاعات صریح کاربر را استخراج می‌کند؛ ذخیره روی پرونده فقط با تأیید شما انجام می‌شود.
                </Typography>
              </Box>
            </Stack>

            <Stack direction="row" gap={1} flexWrap="wrap">
              {[
                'این ملک را تحلیل کن',
                'برای ساخت چه اطلاعاتی کم داریم؟',
                'ارزش زمین را با داده‌های موجود بررسی کن',
                'برای مشارکت چه ورودی‌هایی لازم است؟',
              ].map((suggestion) => (
                <Chip
                  key={suggestion}
                  label={suggestion}
                  variant="outlined"
                  onClick={() => setMessage(suggestion)}
                />
              ))}
            </Stack>

            <TextField
              fullWidth
              multiline
              minRows={3}
              label="پیام فارسی"
              placeholder="مثلاً: ملک ۵۰۰ متره، بر ۱۲ متر، سال ساخت ۱۳۵۰، دو طبقه، جنوبی و آسانسور نداره."
              value={message}
              onChange={(event) => setMessage(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter' && !event.shiftKey) {
                  event.preventDefault()
                  submit()
                }
              }}
            />

            <Stack direction="row" justifyContent="flex-end">
              <Button
                variant="contained"
                endIcon={
                  chat.isPending
                    ? <CircularProgress size={18} />
                    : <SendRoundedIcon />
                }
                onClick={submit}
                disabled={!message.trim() || chat.isPending}
              >
                ارسال به دستیار
              </Button>
            </Stack>

            {chat.isError && (
              <Alert severity="error">
                {chat.error instanceof Error
                  ? chat.error.message
                  : 'اجرای Agent ناموفق بود.'}
              </Alert>
            )}
          </Stack>
        </CardContent>
      </Card>

      <Card variant="outlined">
        <CardContent>
          <Stack spacing={2}>
            <Typography fontWeight={700}>گفتگو</Typography>
            {history.isLoading && <CircularProgress size={24} />}

            {!history.isLoading && (history.data?.length ?? 0) === 0 && (
              <Typography color="text.secondary" variant="body2">
                هنوز پیامی در این گفتگو ثبت نشده است.
              </Typography>
            )}

            {(history.data ?? []).map((item) => (
              <Stack
                key={item.id}
                alignItems={item.role === 'USER' ? 'flex-start' : 'flex-end'}
              >
                <Box
                  sx={{
                    maxWidth: '88%',
                    px: 2,
                    py: 1.25,
                    borderRadius: 2,
                    bgcolor:
                      item.role === 'USER'
                        ? 'action.hover'
                        : 'action.selected',
                    border: '1px solid',
                    borderColor: 'divider',
                  }}
                >
                  <Typography variant="body2">{item.content}</Typography>
                </Box>
              </Stack>
            ))}
          </Stack>
        </CardContent>
      </Card>

      {effectiveResult && (
        <Card variant="outlined">
          <CardContent>
            <Stack spacing={2}>
              <Stack
                direction={{ xs: 'column', sm: 'row' }}
                justifyContent="space-between"
                gap={1}
              >
                <Box>
                  <Typography fontWeight={800}>نتیجه ساخت‌یافته</Typography>
                  <Typography variant="body2" color="text.secondary">
                    قرارداد {effectiveResult.schemaVersion} ·{' '}
                    {effectiveResult.intent} · {effectiveResult.workflow}
                  </Typography>
                </Box>

                <Chip
                  label={effectiveResult.status}
                  color={statusColor(effectiveResult.status)}
                  size="small"
                />
              </Stack>

              <Alert severity={statusColor(effectiveResult.status)}>
                {effectiveResult.message}
              </Alert>

              {(effectiveResult.assumptions ?? []).length > 0 && (
                <Alert severity="warning">
                  فرضیات فعال: {effectiveResult.assumptions.join('، ')}
                </Alert>
              )}

              {(effectiveResult.missingFields ?? []).length > 0 && (
                <Box>
                  <Typography fontWeight={700} variant="body2" mb={1}>
                    اطلاعات مورد نیاز
                  </Typography>
                  <Stack direction="row" gap={1} flexWrap="wrap">
                    {effectiveResult.missingFields.map((field) => (
                      <Chip key={field} label={field} size="small" />
                    ))}
                  </Stack>
                </Box>
              )}

              {Object.keys(normalizedParameters).length > 0 && (
                <>
                  <Divider />

                  <Box>
                    <Typography fontWeight={700} variant="body2" mb={1}>
                      فیلدهای استخراج‌شده
                    </Typography>
                    <Box
                      component="pre"
                      dir="ltr"
                      sx={{
                        m: 0,
                        p: 1.5,
                        borderRadius: 1,
                        bgcolor: 'action.hover',
                        overflow: 'auto',
                        fontSize: 13,
                      }}
                    >
                      {JSON.stringify(normalizedParameters, null, 2)}
                    </Box>
                  </Box>

                  <Stack
                    direction="row"
                    gap={1}
                    alignItems="center"
                    flexWrap="wrap"
                  >
                    <Button
                      variant="outlined"
                      startIcon={<SaveRoundedIcon />}
                      disabled={applyToProperty.isPending}
                      onClick={() => applyToProperty.mutate()}
                    >
                      اعمال همه اطلاعات استخراج‌شده روی ملک
                    </Button>

                    {propertyApplied && (
                      <Chip
                        color="success"
                        icon={<CheckRoundedIcon />}
                        label="در پرونده ذخیره شد"
                      />
                    )}
                  </Stack>

                  <Typography variant="caption" color="text.secondary">
                    فیلدهای استاندارد در ستون‌های Property و سایر اطلاعات در JSONB ذخیره و Merge می‌شوند.
                  </Typography>

                  {applyToProperty.isError && (
                    <Alert severity="error">
                      {applyToProperty.error instanceof Error
                        ? applyToProperty.error.message
                        : 'اعمال روی پرونده ناموفق بود.'}
                    </Alert>
                  )}
                </>
              )}

              {unknownTerms.length > 0 && (
                <>
                  <Divider />
                  <Stack spacing={2}>
                    <Typography fontWeight={800}>
                      رفع ابهام واژه‌نامه
                    </Typography>
                    <Alert severity="warning">
                      Agent معنی این اصطلاح‌ها را حدس نزده است. تعریف را ثبت و سپس تأیید کنید؛ بعد درخواست را دوباره ارسال کنید.
                    </Alert>

                    {unknownTerms.map((term) => {
                      const draft = drafts[term]
                      return (
                        <Stack key={term} spacing={1}>
                          <Typography fontWeight={700}>{term}</Typography>

                          <TextField
                            size="small"
                            label="معنی این اصطلاح"
                            value={definitions[term] ?? ''}
                            onChange={(event) =>
                              setDefinitions((current) => ({
                                ...current,
                                [term]: event.target.value,
                              }))
                            }
                            disabled={draft?.status === 'APPROVED'}
                          />

                          <Stack direction="row" gap={1}>
                            {!draft && (
                              <Button
                                size="small"
                                variant="outlined"
                                disabled={
                                  !definitions[term]?.trim() ||
                                  createDraft.isPending
                                }
                                onClick={() =>
                                  createDraft.mutate({
                                    term,
                                    meaning: definitions[term].trim(),
                                  })
                                }
                              >
                                ثبت پیش‌نویس
                              </Button>
                            )}

                            {draft?.status === 'DRAFT' && (
                              <Button
                                size="small"
                                variant="contained"
                                color="success"
                                disabled={approveDraft.isPending}
                                onClick={() => approveDraft.mutate(draft)}
                              >
                                تأیید انسانی واژه
                              </Button>
                            )}

                            {draft?.status === 'APPROVED' && (
                              <Chip
                                color="success"
                                label="تأیید شد"
                                size="small"
                              />
                            )}
                          </Stack>
                        </Stack>
                      )
                    })}
                  </Stack>
                </>
              )}
            </Stack>
          </CardContent>
        </Card>
      )}
    </Stack>
  )
}
