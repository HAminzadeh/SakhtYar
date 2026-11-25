/** Public formatting helpers; all monetary calculations remain server-side BigDecimal. */
export function formatMoney(value:number,currencyCode:string,locale:string){
  if(currencyCode==='TOMAN'){
    return `${new Intl.NumberFormat(locale,{maximumFractionDigits:0}).format(value)} ${locale.startsWith('fa')?'تومان':'Toman'}`
  }
  try{
    return new Intl.NumberFormat(locale,{style:'currency',currency:currencyCode,
      maximumFractionDigits:currencyCode==='IRR'?0:2}).format(value)
  }catch{
    return `${new Intl.NumberFormat(locale).format(value)} ${currencyCode}`
  }
}
export function formatNumber(value:number,locale:string){
  return new Intl.NumberFormat(locale).format(value)
}
/** Explicit browser calendar choice; ISO timestamps remain unchanged in backend storage. */
export function formatZonedDate(value:string|Date,locale:string,timeZone='Asia/Tehran',calendar:'jalali'|'gregorian'='jalali'){
  const date=value instanceof Date?value:new Date(value)
  if(Number.isNaN(date.getTime()))return '—'
  const taggedLocale=calendar==='jalali'?'fa-IR-u-ca-persian':locale.startsWith('fa')?'fa-IR-u-ca-gregory':locale
  return new Intl.DateTimeFormat(taggedLocale,{timeZone,year:'numeric',month:'2-digit',day:'2-digit'}).format(date)
}
export function formatDate(value:string|Date,locale:string){
  return formatZonedDate(value,locale,locale.startsWith('fa')?'Asia/Tehran':'UTC',
    locale.startsWith('fa')?'jalali':'gregorian')
}
export function formatSquareMeters(value:number,locale:string){
  return `${formatNumber(value,locale)} ${locale.startsWith('fa')?'مترمربع':'m²'}`
}
