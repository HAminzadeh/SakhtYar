import AddRoundedIcon from '@mui/icons-material/AddRounded'
import DeleteOutlineRoundedIcon from '@mui/icons-material/DeleteOutlineRounded'
import EditRoundedIcon from '@mui/icons-material/EditRounded'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  IconButton,
  LinearProgress,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { ApiError, api } from '../../api/client'
import type { OwnerItem } from '../../api/types'

type OwnerForm = {
  firstName: string
  lastName: string
  nationalId: string
  mobile: string
  ownershipNumerator: string
  ownershipDenominator: string
  primaryContact: boolean
}

const emptyForm: OwnerForm = {
  firstName: '',
  lastName: '',
  nationalId: '',
  mobile: '',
  ownershipNumerator: '1',
  ownershipDenominator: '1',
  primaryContact: false,
}

function percent(numerator: number, denominator: number) {
  if (!denominator) return 0
  return (numerator / denominator) * 100
}

function faPercent(value: number) {
  return new Intl.NumberFormat('fa-IR', {
    maximumFractionDigits: 2,
  }).format(value)
}

export function OwnersPanel({ caseId }: { caseId: string }) {
  const queryClient = useQueryClient()
  const [dialogOpen, setDialogOpen] = useState(false)
  const [editing, setEditing] = useState<OwnerItem | null>(null)
  const [form, setForm] = useState<OwnerForm>(emptyForm)

  const ownersQuery = useQuery({
    queryKey: ['owners', caseId],
    queryFn: async (): Promise<OwnerItem[] | null> => {
      try {
        return await api<OwnerItem[]>(
          `/api/v1/cases/${caseId}/owners`,
        )
      } catch (error) {
        if (error instanceof ApiError && error.status === 404) {
          return null
        }
        throw error
      }
    },
    retry: false,
  })

  const owners = ownersQuery.data ?? []

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

  const primaryContact = owners.find((owner) => owner.primaryContact)

  const currentNumerator = Number(form.ownershipNumerator)
  const currentDenominator = Number(form.ownershipDenominator)
  const currentShare =
    Number.isFinite(currentNumerator) &&
    Number.isFinite(currentDenominator) &&
    currentDenominator > 0
      ? percent(currentNumerator, currentDenominator)
      : 0

  const existingPercentWithoutEditing = owners.reduce((sum, owner) => {
    if (editing && owner.id === editing.id) return sum

    return (
      sum +
      percent(
        owner.ownershipNumerator,
        owner.ownershipDenominator,
      )
    )
  }, 0)

  const proposedTotal = existingPercentWithoutEditing + currentShare

  const invalidShare =
    !Number.isInteger(currentNumerator) ||
    !Number.isInteger(currentDenominator) ||
    currentNumerator <= 0 ||
    currentDenominator <= 0 ||
    currentNumerator > currentDenominator ||
    proposedTotal > 100.000001

  const saveOwner = useMutation({
    mutationFn: () => {
      const payload = {
        firstName: form.firstName.trim(),
        lastName: form.lastName.trim(),
        nationalId: form.nationalId.trim() || null,
        mobile: form.mobile.trim() || null,
        ownershipNumerator: currentNumerator,
        ownershipDenominator: currentDenominator,
        primaryContact: form.primaryContact,
      }

      if (editing) {
        return api<OwnerItem>(
          `/api/v1/cases/${caseId}/owners/${editing.id}`,
          {
            method: 'PUT',
            body: JSON.stringify(payload),
          },
        )
      }

      return api<OwnerItem>(`/api/v1/cases/${caseId}/owners`, {
        method: 'POST',
        body: JSON.stringify(payload),
      })
    },
    onSuccess: () => {
      setDialogOpen(false)
      setEditing(null)
      setForm(emptyForm)
      queryClient.invalidateQueries({ queryKey: ['owners', caseId] })
    },
  })

  const deleteOwner = useMutation({
    mutationFn: (ownerId: string) =>
      api<void>(`/api/v1/cases/${caseId}/owners/${ownerId}`, {
        method: 'DELETE',
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['owners', caseId] })
    },
  })

  const openCreate = () => {
    setEditing(null)
    setForm(emptyForm)
    saveOwner.reset()
    setDialogOpen(true)
  }

  const openEdit = (owner: OwnerItem) => {
    setEditing(owner)
    setForm({
      firstName: owner.firstName,
      lastName: owner.lastName,
      nationalId: owner.nationalId ?? '',
      mobile: owner.mobile ?? '',
      ownershipNumerator: String(owner.ownershipNumerator),
      ownershipDenominator: String(owner.ownershipDenominator),
      primaryContact: owner.primaryContact,
    })
    saveOwner.reset()
    setDialogOpen(true)
  }

  const handleDelete = (owner: OwnerItem) => {
    const accepted = window.confirm(
      `مالک «${owner.firstName} ${owner.lastName}» حذف شود؟`,
    )

    if (accepted) {
      deleteOwner.mutate(owner.id)
    }
  }

  const canSave =
    form.firstName.trim().length > 0 &&
    form.lastName.trim().length > 0 &&
    !invalidShare &&
    !saveOwner.isPending

  return (
    <>
      <Card variant="outlined">
        <CardContent>
          <Stack spacing={3}>
            <Stack
              direction={{ xs: 'column', md: 'row' }}
              justifyContent="space-between"
              alignItems={{ xs: 'stretch', md: 'center' }}
              gap={2}
            >
              <div>
                <Typography variant="h6" fontWeight={700}>
                  مالکین ملک
                </Typography>
                <Typography color="text.secondary" variant="body2">
                  مالکین، سهم مالکیت و رابط اصلی پرونده
                </Typography>
              </div>

              <Button
                variant="contained"
                startIcon={<AddRoundedIcon />}
                onClick={openCreate}
                disabled={ownersQuery.data === null}
              >
                افزودن مالک
              </Button>
            </Stack>

            {ownersQuery.data === null && (
              <Alert severity="warning">
                ابتدا در تب «مشخصات ملک» اطلاعات ملک را ذخیره کنید؛ سپس
                امکان ثبت مالکین فعال می‌شود.
              </Alert>
            )}

            {ownersQuery.isError && (
              <Alert severity="error">
                دریافت اطلاعات مالکین ناموفق بود.
              </Alert>
            )}

            {deleteOwner.isError && (
              <Alert severity="error">
                حذف مالک ناموفق بود.
              </Alert>
            )}

            {ownersQuery.data !== null && (
              <>
                <Stack
                  direction={{ xs: 'column', md: 'row' }}
                  gap={2}
                >
                  <Box
                    sx={{
                      flex: 1,
                      p: 2,
                      border: 1,
                      borderColor: 'divider',
                      borderRadius: 2,
                    }}
                  >
                    <Typography variant="body2" color="text.secondary">
                      تعداد مالک
                    </Typography>
                    <Typography variant="h5" fontWeight={800}>
                      {owners.length.toLocaleString('fa-IR')} نفر
                    </Typography>
                  </Box>

                  <Box
                    sx={{
                      flex: 1,
                      p: 2,
                      border: 1,
                      borderColor: 'divider',
                      borderRadius: 2,
                    }}
                  >
                    <Typography variant="body2" color="text.secondary">
                      مجموع سهم ثبت‌شده
                    </Typography>
                    <Typography variant="h5" fontWeight={800}>
                      {faPercent(totalPercent)}٪
                    </Typography>
                  </Box>

                  <Box
                    sx={{
                      flex: 1,
                      p: 2,
                      border: 1,
                      borderColor: 'divider',
                      borderRadius: 2,
                    }}
                  >
                    <Typography variant="body2" color="text.secondary">
                      رابط اصلی
                    </Typography>
                    <Typography variant="h6" fontWeight={700}>
                      {primaryContact
                        ? `${primaryContact.firstName} ${primaryContact.lastName}`
                        : 'تعیین نشده'}
                    </Typography>
                  </Box>
                </Stack>

                <Box>
                  <Stack
                    direction="row"
                    justifyContent="space-between"
                    mb={0.75}
                  >
                    <Typography variant="body2">
                      پوشش سهم مالکیت
                    </Typography>
                    <Typography variant="body2" fontWeight={700}>
                      {faPercent(totalPercent)}٪
                    </Typography>
                  </Stack>

                  <LinearProgress
                    variant="determinate"
                    value={Math.min(totalPercent, 100)}
                    sx={{ height: 9, borderRadius: 99 }}
                  />

                  {totalPercent < 99.999 && owners.length > 0 && (
                    <Typography
                      variant="caption"
                      color="text.secondary"
                      display="block"
                      mt={0.75}
                    >
                      {faPercent(100 - totalPercent)}٪ از سهم مالکیت هنوز
                      ثبت نشده است.
                    </Typography>
                  )}
                </Box>

                <TableContainer>
                  <Table>
                    <TableHead>
                      <TableRow>
                        <TableCell>نام و نام خانوادگی</TableCell>
                        <TableCell>کد ملی</TableCell>
                        <TableCell>موبایل</TableCell>
                        <TableCell>سهم</TableCell>
                        <TableCell>وضعیت</TableCell>
                        <TableCell align="left">عملیات</TableCell>
                      </TableRow>
                    </TableHead>

                    <TableBody>
                      {owners.length === 0 && (
                        <TableRow>
                          <TableCell colSpan={6}>
                            <Typography color="text.secondary">
                              هنوز مالکی ثبت نشده است.
                            </Typography>
                          </TableCell>
                        </TableRow>
                      )}

                      {owners.map((owner) => (
                        <TableRow key={owner.id} hover>
                          <TableCell>
                            <Typography fontWeight={700}>
                              {owner.firstName} {owner.lastName}
                            </Typography>
                          </TableCell>

                          <TableCell>
                            {owner.nationalId || '-'}
                          </TableCell>

                          <TableCell>{owner.mobile || '-'}</TableCell>

                          <TableCell>
                            <Stack spacing={0.25}>
                              <Typography>
                                {owner.ownershipNumerator.toLocaleString(
                                  'fa-IR',
                                )}
                                /
                                {owner.ownershipDenominator.toLocaleString(
                                  'fa-IR',
                                )}
                              </Typography>
                              <Typography
                                variant="caption"
                                color="text.secondary"
                              >
                                {faPercent(
                                  percent(
                                    owner.ownershipNumerator,
                                    owner.ownershipDenominator,
                                  ),
                                )}
                                ٪
                              </Typography>
                            </Stack>
                          </TableCell>

                          <TableCell>
                            {owner.primaryContact ? (
                              <Chip
                                size="small"
                                color="primary"
                                label="رابط اصلی"
                              />
                            ) : (
                              '-'
                            )}
                          </TableCell>

                          <TableCell align="left">
                            <Tooltip title="ویرایش">
                              <IconButton
                                onClick={() => openEdit(owner)}
                                size="small"
                              >
                                <EditRoundedIcon fontSize="small" />
                              </IconButton>
                            </Tooltip>

                            <Tooltip title="حذف">
                              <IconButton
                                onClick={() => handleDelete(owner)}
                                size="small"
                                color="error"
                                disabled={deleteOwner.isPending}
                              >
                                <DeleteOutlineRoundedIcon fontSize="small" />
                              </IconButton>
                            </Tooltip>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              </>
            )}
          </Stack>
        </CardContent>
      </Card>

      <Dialog
        open={dialogOpen}
        onClose={() => setDialogOpen(false)}
        fullWidth
        maxWidth="sm"
      >
        <DialogTitle>
          {editing ? 'ویرایش مالک' : 'افزودن مالک'}
        </DialogTitle>

        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            {saveOwner.isError && (
              <Alert severity="error">
                {saveOwner.error instanceof Error
                  ? saveOwner.error.message
                  : 'ذخیره مالک ناموفق بود.'}
              </Alert>
            )}

            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <TextField
                fullWidth
                label="نام"
                value={form.firstName}
                onChange={(e) =>
                  setForm({ ...form, firstName: e.target.value })
                }
              />
              <TextField
                fullWidth
                label="نام خانوادگی"
                value={form.lastName}
                onChange={(e) =>
                  setForm({ ...form, lastName: e.target.value })
                }
              />
            </Stack>

            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <TextField
                fullWidth
                label="کد ملی"
                value={form.nationalId}
                onChange={(e) =>
                  setForm({ ...form, nationalId: e.target.value })
                }
              />
              <TextField
                fullWidth
                label="شماره موبایل"
                value={form.mobile}
                onChange={(e) =>
                  setForm({ ...form, mobile: e.target.value })
                }
              />
            </Stack>

            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
              <TextField
                fullWidth
                label="صورت سهم"
                type="number"
                value={form.ownershipNumerator}
                onChange={(e) =>
                  setForm({
                    ...form,
                    ownershipNumerator: e.target.value,
                  })
                }
                slotProps={{ htmlInput: { min: 1, step: 1 } }}
              />

              <TextField
                fullWidth
                label="مخرج سهم"
                type="number"
                value={form.ownershipDenominator}
                onChange={(e) =>
                  setForm({
                    ...form,
                    ownershipDenominator: e.target.value,
                  })
                }
                slotProps={{ htmlInput: { min: 1, step: 1 } }}
              />
            </Stack>

            <Alert severity={invalidShare ? 'warning' : 'info'}>
              سهم این مالک: {faPercent(currentShare)}٪ — مجموع پیشنهادی
              سهم مالکین: {faPercent(proposedTotal)}٪
            </Alert>

            <FormControlLabel
              control={
                <Checkbox
                  checked={form.primaryContact}
                  onChange={(e) =>
                    setForm({
                      ...form,
                      primaryContact: e.target.checked,
                    })
                  }
                />
              }
              label="این مالک رابط اصلی پرونده است"
            />
          </Stack>
        </DialogContent>

        <DialogActions>
          <Button onClick={() => setDialogOpen(false)}>
            انصراف
          </Button>
          <Button
            variant="contained"
            disabled={!canSave}
            onClick={() => saveOwner.mutate()}
          >
            {saveOwner.isPending ? 'در حال ذخیره...' : 'ذخیره مالک'}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  )
}
