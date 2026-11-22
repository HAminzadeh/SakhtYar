export function formatMoney(
  value: number,
  currencyCode: string,
  locale: string,
) {
  if (currencyCode === 'TOMAN') {
    return `${new Intl.NumberFormat(locale).format(value)} ${locale.startsWith('fa') ? 'تومان' : 'Toman'}`
  }
  try {
    return new Intl.NumberFormat(locale, {
      style: 'currency',
      currency: currencyCode,
      maximumFractionDigits: 2,
    }).format(value)
  } catch {
    return `${new Intl.NumberFormat(locale).format(value)} ${currencyCode}`
  }
}

export function formatNumber(value: number, locale: string) {
  return new Intl.NumberFormat(locale).format(value)
}

export function formatDate(value: string | Date, locale: string) {
  return new Intl.DateTimeFormat(locale).format(new Date(value))
}