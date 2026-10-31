export function roleLabel(role?: string | null) {
  switch (role) {
    case 'ADMIN':
      return 'مدیر سیستم'
    case 'PROJECT_MANAGER':
      return 'مدیر پروژه'
    case 'ANALYST':
      return 'تحلیل‌گر'
    case 'LEGAL_EXPERT':
      return 'کارشناس حقوقی'
    case 'TESTER':
      return 'تستر'
    case 'READ_ONLY':
      return 'فقط مشاهده'
    default:
      return role || ''
  }
}

export function userStatusLabel(status?: string | null) {
  switch (status) {
    case 'ACTIVE':
      return 'فعال'
    case 'PENDING':
      return 'در انتظار تأیید'
    case 'SUSPENDED':
      return 'تعلیق‌شده'
    default:
      return status || ''
  }
}

export function safeDisplayName(
  value?: string | null,
  fallback = 'کاربر ساخت‌یار',
) {
  let normalized = value?.trim() ?? ''

  normalized = normalized
    .replace(/^[;:،,|\\/\s]+/, '')
    .replace(/[;:،,|\\/\s]+$/, '')
    .trim()

  if (!normalized) return fallback

  const lower = normalized.toLowerCase()
  const corrupted =
    /[ÃÂØÙÛ�]|â€|Æ|Ð|Ñ/.test(normalized) ||
    normalized.length > 180

  if (
    corrupted ||
    lower === 'admin' ||
    lower === 'system administrator'
  ) {
    return fallback
  }

  return normalized
}
