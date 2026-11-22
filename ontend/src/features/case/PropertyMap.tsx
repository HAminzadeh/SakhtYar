import { AimOutlined, EnvironmentOutlined, SaveOutlined, SearchOutlined } from '@ant-design/icons'
import { Alert, Button, Card, Input, List, Space, Spin, Tag, Typography } from 'antd'
import maplibregl from '@neshan-maps-platform/maplibre-sdk'
import '@neshan-maps-platform/maplibre-sdk/style.css'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useRef, useState } from 'react'
import { ApiError, api } from '../../api/client'
import type { GeoSearchResult, GeoStatus, PropertyItem, ReverseGeocodeResult } from '../../api/types'

type Point = { latitude: number; longitude: number }
type MapInstance = InstanceType<typeof maplibregl.Map>
type MarkerInstance = InstanceType<typeof maplibregl.Marker>
const TEHRAN: Point = { latitude: 35.6892, longitude: 51.389 }

function propertyPayload(property: PropertyItem, point: Point, reverse?: ReverseGeocodeResult | null, applyAddress = false) {
  return {
    province: applyAddress && reverse?.province ? reverse.province : property.province ?? null,
    city: applyAddress && reverse?.city ? reverse.city : property.city ?? null,
    district: applyAddress && reverse?.district ? reverse.district : property.district ?? null,
    neighborhood: applyAddress && reverse?.neighborhood ? reverse.neighborhood : property.neighborhood ?? null,
    address: applyAddress && reverse?.formattedAddress ? reverse.formattedAddress : property.address ?? null,
    landAreaM2: property.landAreaM2 ?? null,
    frontageM: property.frontageM ?? null,
    passageWidthM: property.passageWidthM ?? null,
    buildingAreaM2: property.buildingAreaM2 ?? null,
    constructionYear: property.constructionYear ?? null,
    existingFloors: property.existingFloors ?? null,
    existingUnits: property.existingUnits ?? null,
    orientation: property.orientation ?? null,
    propertyType: property.propertyType ?? null,
    buildingCondition: property.buildingCondition ?? null,
    registryMainNo: property.registryMainNo ?? null,
    registrySubNo: property.registrySubNo ?? null,
    registrySection: property.registrySection ?? null,
    postalCode: property.postalCode ?? null,
    latitude: point.latitude,
    longitude: point.longitude,
    attributes: property.attributes ?? {},
  }
}

export function PropertyMap({ caseId, compact = false }: { caseId: string; compact?: boolean }) {
  const queryClient = useQueryClient()
  const mapContainerRef = useRef<HTMLDivElement | null>(null)
  const mapRef = useRef<MapInstance | null>(null)
  const markerRef = useRef<MarkerInstance | null>(null)
  const [point, setPoint] = useState<Point | null>(null)
  const [query, setQuery] = useState('')
  const [searchResults, setSearchResults] = useState<GeoSearchResult[]>([])
  const [reverse, setReverse] = useState<ReverseGeocodeResult | null>(null)
  const [mapError, setMapError] = useState<string | null>(null)
  const mapApiKey = import.meta.env.VITE_NESHAN_MAP_API_KEY?.trim() ?? ''

  const propertyQuery = useQuery({
    queryKey: ['property', caseId],
    queryFn: async (): Promise<PropertyItem | null> => {
      try { return await api<PropertyItem>(`/api/v1/cases/${caseId}/property`) }
      catch (error) { if (error instanceof ApiError && error.status === 404) return null; throw error }
    }, retry: false,
  })
  const geoStatus = useQuery({ queryKey: ['geo-status'], queryFn: () => api<GeoStatus>('/api/v1/geo/status'), retry: false })

  useEffect(() => {
    if (propertyQuery.data?.latitude != null && propertyQuery.data.longitude != null) {
      setPoint({ latitude: propertyQuery.data.latitude, longitude: propertyQuery.data.longitude })
    }
  }, [propertyQuery.data?.latitude, propertyQuery.data?.longitude])

  const initialCenter = useMemo<Point>(() => {
    if (propertyQuery.data?.latitude != null && propertyQuery.data.longitude != null) return { latitude: propertyQuery.data.latitude, longitude: propertyQuery.data.longitude }
    return TEHRAN
  }, [propertyQuery.data?.latitude, propertyQuery.data?.longitude])

  const reverseMutation = useMutation({ mutationFn: (selected: Point) => api<ReverseGeocodeResult>(`/api/v1/geo/reverse?lat=${encodeURIComponent(selected.latitude)}&lng=${encodeURIComponent(selected.longitude)}`), onSuccess: setReverse, onError: () => setReverse(null) })
  const searchMutation = useMutation({ mutationFn: () => api<GeoSearchResult[]>(`/api/v1/geo/geocode?address=${encodeURIComponent(query.trim())}`), onSuccess: setSearchResults })
  const saveLocation = useMutation({
    mutationFn: ({ applyAddress }: { applyAddress: boolean }) => {
      const property = propertyQuery.data
      if (!property || !point) throw new Error('اطلاعات ملک یا مختصات در دسترس نیست.')
      return api<PropertyItem>(`/api/v1/cases/${caseId}/property`, { method: 'PUT', body: JSON.stringify(propertyPayload(property, point, reverse, applyAddress)) })
    },
    onSuccess: (saved) => { queryClient.setQueryData(['property', caseId], saved); queryClient.invalidateQueries({ queryKey: ['case', caseId] }); queryClient.invalidateQueries({ queryKey: ['cases'] }) },
  })

  const updateMarker = (selected: Point, centerMap = false) => {
    setPoint(selected)
    const map = mapRef.current
    if (!map) return
    if (!markerRef.current) {
      markerRef.current = new maplibregl.Marker({ draggable: true }).setLngLat([selected.longitude, selected.latitude]).addTo(map)
      markerRef.current.on('dragend', () => {
        const lngLat = markerRef.current?.getLngLat(); if (!lngLat) return
        const dragged = { latitude: lngLat.lat, longitude: lngLat.lng }
        setPoint(dragged); reverseMutation.mutate(dragged)
      })
    } else markerRef.current.setLngLat([selected.longitude, selected.latitude])
    if (centerMap) map.flyTo({ center: [selected.longitude, selected.latitude], zoom: Math.max(map.getZoom(), 16) })
  }

  useEffect(() => {
    if (!mapContainerRef.current || !mapApiKey || propertyQuery.isLoading || mapRef.current) return
    try {
      const map = new maplibregl.Map({ container: mapContainerRef.current, style: 'https://static.neshan.org/sdk/maplibre/styles/light.json', center: [initialCenter.longitude, initialCenter.latitude], zoom: point ? 16 : 12, apiKey: mapApiKey })
      map.addControl(new maplibregl.NavigationControl(), 'top-left')
      map.on('click', (event) => { const selected = { latitude: event.lngLat.lat, longitude: event.lngLat.lng }; updateMarker(selected); reverseMutation.mutate(selected) })
      map.on('error', () => setMapError('بارگذاری بخشی از نقشه با خطا روبرو شد. کلید Web Map نشان و دسترسی شبکه را بررسی کنید.'))
      mapRef.current = map
      if (point) window.setTimeout(() => updateMarker(point), 0)
    } catch (error) { setMapError(error instanceof Error ? error.message : 'راه‌اندازی نقشه نشان ناموفق بود.') }
    return () => { markerRef.current?.remove(); markerRef.current = null; mapRef.current?.remove(); mapRef.current = null }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mapApiKey, propertyQuery.isLoading])

  useEffect(() => { if (point && mapRef.current) updateMarker(point) /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [point?.latitude, point?.longitude])

  const useSearchResult = (result: GeoSearchResult) => {
    const selected = { latitude: result.latitude, longitude: result.longitude }
    updateMarker(selected, true)
    setReverse(result.address ? { formattedAddress: result.address, city: result.city ?? null, neighborhood: result.neighborhood ?? null } : null)
    reverseMutation.mutate(selected)
    setSearchResults([])
  }

  const browserLocation = () => {
    if (!navigator.geolocation) { setMapError('مرورگر این دستگاه Geolocation را پشتیبانی نمی‌کند.'); return }
    navigator.geolocation.getCurrentPosition(
      (position) => { const selected = { latitude: position.coords.latitude, longitude: position.coords.longitude }; updateMarker(selected, true); reverseMutation.mutate(selected) },
      () => setMapError('دسترسی به موقعیت مرورگر داده نشد یا موقعیت قابل دریافت نبود.'),
      { enableHighAccuracy: true, timeout: 10_000 },
    )
  }

  if (propertyQuery.isLoading) return <Card className="sakhtyar-map-card"><div className="sakhtyar-map-loading"><Spin size="large" /></div></Card>
  if (propertyQuery.isError) return <Alert type="error" showIcon message="دریافت اطلاعات ملک برای نقشه ناموفق بود." />
  if (!propertyQuery.data) return <Alert type="warning" showIcon message="ابتدا در تب «مشخصات ملک» اطلاعات ملک را ذخیره کنید؛ سپس امکان ثبت موقعیت مکانی فعال می‌شود." />

  const mapBody = (
    <>
      {!mapApiKey ? <Alert type="warning" showIcon message="کلید Web Map نشان تنظیم نشده است." description="در frontend/.env.local مقدار VITE_NESHAN_MAP_API_KEY را قرار دهید و Vite را Restart کنید." /> : null}
      {mapError ? <Alert type="error" showIcon closable onClose={() => setMapError(null)} message={mapError} /> : null}
      <div className="sakhtyar-map-search">
        <Input size="large" prefix={<SearchOutlined />} placeholder="جستجوی آدرس ملک..." value={query} onChange={(event) => setQuery(event.target.value)} onPressEnter={() => { if (query.trim() && geoStatus.data?.providerAvailable) searchMutation.mutate() }} />
        <Button size="large" type="primary" icon={<SearchOutlined />} disabled={!query.trim() || searchMutation.isPending || !geoStatus.data?.providerAvailable} onClick={() => searchMutation.mutate()}>جستجو</Button>
        <Button size="large" icon={<AimOutlined />} onClick={browserLocation}>موقعیت من</Button>
      </div>
      {searchResults.length > 0 ? (
        <List className="sakhtyar-map-results" size="small" bordered dataSource={searchResults} renderItem={(result) => (
          <List.Item className="sakhtyar-map-result-item" onClick={() => useSearchResult(result)}>
            <List.Item.Meta avatar={<EnvironmentOutlined />} title={result.title || result.address || 'نتیجه نشان'} description={[result.address, result.neighborhood, result.city].filter(Boolean).join(' • ')} />
          </List.Item>
        )} />
      ) : null}
      <div ref={mapContainerRef} className={compact ? 'sakhtyar-map-canvas sakhtyar-map-canvas-compact' : 'sakhtyar-map-canvas'} />
      <div className="sakhtyar-map-footer">
        <Space wrap>
          {point ? <Tag icon={<AimOutlined />} color="blue">{point.latitude.toFixed(6)}، {point.longitude.toFixed(6)}</Tag> : null}
          {reverse?.formattedAddress ? <Typography.Text type="secondary">{reverse.formattedAddress}</Typography.Text> : null}
        </Space>
        <Space wrap>
          <Button disabled={!point || !reverse || saveLocation.isPending} onClick={() => saveLocation.mutate({ applyAddress: true })}>ذخیره با نشانی</Button>
          <Button type="primary" icon={<SaveOutlined />} disabled={!point || saveLocation.isPending} loading={saveLocation.isPending} onClick={() => saveLocation.mutate({ applyAddress: false })}>ذخیره موقعیت</Button>
        </Space>
      </div>
      {saveLocation.isSuccess ? <Alert type="success" showIcon message="موقعیت ملک با موفقیت ذخیره شد." /> : null}
      {saveLocation.isError ? <Alert type="error" showIcon message={saveLocation.error instanceof Error ? saveLocation.error.message : 'ذخیره موقعیت ناموفق بود.'} /> : null}
    </>
  )

  if (compact) return <Card className="sakhtyar-map-card sakhtyar-map-card-compact"><Space direction="vertical" size={12} style={{ width: '100%' }}>{mapBody}</Space></Card>
  return <Space direction="vertical" size={16} style={{ width: '100%' }}>
    {geoStatus.data && !geoStatus.data.providerAvailable ? <Alert type="info" showIcon message="سرویس تبدیل آدرس نشان در Backend غیرفعال است؛ نمایش نقشه همچنان در دسترس است." /> : null}
    <Card className="sakhtyar-map-card"><Space direction="vertical" size={14} style={{ width: '100%' }}>{mapBody}</Space></Card>
  </Space>
}
