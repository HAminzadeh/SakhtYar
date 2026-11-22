/** Parse property numeric fields without silently turning Persian digits into NaN/null. */
export function propertyNumber(value: string): number | null {
  const localized = value.trim()
    .replace(/[۰-۹]/g, (digit) => String(digit.charCodeAt(0) - 0x06f0))
    .replace(/[٠-٩]/g, (digit) => String(digit.charCodeAt(0) - 0x0660))
    .replace(/٫/g, '.')
  if (localized.includes('٬') && !/^[+-]?\d{1,3}(?:٬\d{3})+(?:\.\d+)?$/.test(localized)) {
    return Number.NaN
  }
  const normalized = localized.replace(/٬/g, '')
  if (normalized === '') return null
  if (!/^[+-]?(?:\d+(?:\.\d+)?|\.\d+)$/.test(normalized)) return Number.NaN
  const result = Number(normalized)
  return Number.isFinite(result) ? result : Number.NaN
}

export function propertyInteger(value: string): number | null {
  const number = propertyNumber(value)
  return number == null ? null : Number.isInteger(number) ? number : Number.NaN
}

export function invalidPropertyNumbers(form: {
  landAreaM2: string; frontageM: string; passageWidthM: string; buildingAreaM2: string
  constructionYear: string; existingFloors: string; existingUnits: string
  latitude: string; longitude: string
}): boolean {
  const dimensions = [form.landAreaM2, form.frontageM, form.passageWidthM, form.buildingAreaM2]
    .map(propertyNumber)
  if (dimensions.some((x) => x !== null && (!Number.isFinite(x) || x <= 0))) return true
  const [year, floors, units] = [form.constructionYear, form.existingFloors, form.existingUnits]
    .map(propertyInteger)
  if (year !== null && (!Number.isInteger(year) || year < 1000 || year > 2500)) return true
  if (floors !== null && (!Number.isInteger(floors) || floors < 0 || floors > 200)) return true
  if (units !== null && (!Number.isInteger(units) || units < 0 || units > 10000)) return true
  const latitude = propertyNumber(form.latitude)
  const longitude = propertyNumber(form.longitude)
  if ((latitude === null) !== (longitude === null)) return true
  if (latitude !== null && (!Number.isFinite(latitude) || latitude < -90 || latitude > 90)) return true
  if (longitude !== null && (!Number.isFinite(longitude) || longitude < -180 || longitude > 180)) return true
  return false
}
