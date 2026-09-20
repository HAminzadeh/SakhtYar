import LocationOnRoundedIcon from '@mui/icons-material/LocationOnRounded'
import MyLocationRoundedIcon from '@mui/icons-material/MyLocationRounded'
import SaveRoundedIcon from '@mui/icons-material/SaveRounded'
import SearchRoundedIcon from '@mui/icons-material/SearchRounded'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Divider,
  List,
  ListItemButton,
  ListItemText,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import maplibregl from '@neshan-maps-platform/maplibre-sdk'
import '@neshan-maps-platform/maplibre-sdk/style.css'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useRef, useState } from 'react'
import { ApiError, api } from '../../api/client'
import type {
  GeoSearchResult,
  GeoStatus,
  PropertyItem,
  ReverseGeocodeResult,
} from '../../api/types'

type Point = {
  latitude: number
  longitude: number
}

type MapInstance = InstanceType<typeof maplibregl.Map>
type MarkerInstance = InstanceType<typeof maplibregl.Marker>

const TEHRAN: Point = {
  latitude: 35.6892,
  longitude: 51.389,
}

function propertyPayload(
  property: PropertyItem,
  point: Point,
  reverse?: ReverseGeocodeResult | null,
  applyAddress = false,
) {
  return {
    province:
      applyAddress && reverse?.province
        ? reverse.province
        : property.province ?? null,
    city:
      applyAddress && reverse?.city
        ? reverse.city
        : property.city ?? null,
    district:
      applyAddress && reverse?.district
        ? reverse.district
        : property.district ?? null,
    neighborhood:
      applyAddress && reverse?.neighborhood
        ? reverse.neighborhood
        : property.neighborhood ?? null,
    address:
      applyAddress && reverse?.formattedAddress
        ? reverse.formattedAddress
        : property.address ?? null,
    landAreaM2: property.landAreaM2 ?? null,
    registryMainNo: property.registryMainNo ?? null,
    registrySubNo: property.registrySubNo ?? null,
    registrySection: property.registrySection ?? null,
    postalCode: property.postalCode ?? null,
    latitude: point.latitude,
    longitude: point.longitude,
  }
}

export function PropertyMap({ caseId }: { caseId: string }) {
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
      try {
        return await api<PropertyItem>(
          `/api/v1/cases/${caseId}/property`,
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

  const geoStatus = useQuery({
    queryKey: ['geo-status'],
    queryFn: () => api<GeoStatus>('/api/v1/geo/status'),
    retry: false,
  })

  useEffect(() => {
    if (
      propertyQuery.data?.latitude != null &&
      propertyQuery.data.longitude != null
    ) {
      setPoint({
        latitude: propertyQuery.data.latitude,
        longitude: propertyQuery.data.longitude,
      })
    }
  }, [
    propertyQuery.data?.latitude,
    propertyQuery.data?.longitude,
  ])

  const initialCenter = useMemo<Point>(() => {
    if (
      propertyQuery.data?.latitude != null &&
      propertyQuery.data.longitude != null
    ) {
      return {
        latitude: propertyQuery.data.latitude,
        longitude: propertyQuery.data.longitude,
      }
    }

    return TEHRAN
  }, [
    propertyQuery.data?.latitude,
    propertyQuery.data?.longitude,
  ])

  const reverseMutation = useMutation({
    mutationFn: (selected: Point) =>
      api<ReverseGeocodeResult>(
        `/api/v1/geo/reverse?lat=${encodeURIComponent(
          selected.latitude,
        )}&lng=${encodeURIComponent(selected.longitude)}`,
      ),
    onSuccess: setReverse,
    onError: () => setReverse(null),
  })

  const searchMutation = useMutation({
    mutationFn: () => {
      const center = point ?? initialCenter

      return api<GeoSearchResult[]>(
        `/api/v1/geo/search?term=${encodeURIComponent(
          query.trim(),
        )}&lat=${encodeURIComponent(
          center.latitude,
        )}&lng=${encodeURIComponent(center.longitude)}`,
      )
    },
    onSuccess: setSearchResults,
  })

  const saveLocation = useMutation({
    mutationFn: ({
      applyAddress,
    }: {
      applyAddress: boolean
    }) => {
      const property = propertyQuery.data

      if (!property || !point) {
        throw new Error('اطلاعات ملک یا مختصات در دسترس نیست.')
      }

      return api<PropertyItem>(
        `/api/v1/cases/${caseId}/property`,
        {
          method: 'PUT',
          body: JSON.stringify(
            propertyPayload(
              property,
              point,
              reverse,
              applyAddress,
            ),
          ),
        },
      )
    },
    onSuccess: (saved) => {
      queryClient.setQueryData(['property', caseId], saved)
      queryClient.invalidateQueries({ queryKey: ['case', caseId] })
      queryClient.invalidateQueries({ queryKey: ['cases'] })
    },
  })

  const updateMarker = (selected: Point, centerMap = false) => {
    setPoint(selected)

    const map = mapRef.current
    if (!map) return

    if (!markerRef.current) {
      markerRef.current = new maplibregl.Marker({
        draggable: true,
      })
        .setLngLat([selected.longitude, selected.latitude])
        .addTo(map)

      markerRef.current.on('dragend', () => {
        const lngLat = markerRef.current?.getLngLat()
        if (!lngLat) return

        const dragged = {
          latitude: lngLat.lat,
          longitude: lngLat.lng,
        }

        setPoint(dragged)
        reverseMutation.mutate(dragged)
      })
    } else {
      markerRef.current.setLngLat([
        selected.longitude,
        selected.latitude,
      ])
    }

    if (centerMap) {
      map.flyTo({
        center: [selected.longitude, selected.latitude],
        zoom: Math.max(map.getZoom(), 16),
      })
    }
  }

  useEffect(() => {
    if (
      !mapContainerRef.current ||
      !mapApiKey ||
      propertyQuery.isLoading ||
      mapRef.current
    ) {
      return
    }

    try {
      const map = new maplibregl.Map({
        container: mapContainerRef.current,
        style:
          'https://static.neshan.org/sdk/maplibre/styles/light.json',
        center: [
          initialCenter.longitude,
          initialCenter.latitude,
        ],
        zoom: point ? 16 : 12,
        apiKey: mapApiKey,
      })

      map.addControl(
        new maplibregl.NavigationControl(),
        'top-left',
      )

      map.on('click', (event) => {
        const selected = {
          latitude: event.lngLat.lat,
          longitude: event.lngLat.lng,
        }

        updateMarker(selected)
        reverseMutation.mutate(selected)
      })

      map.on('error', () => {
        setMapError(
          'بارگذاری بخشی از نقشه با خطا روبرو شد. کلید Web Map نشان و دسترسی شبکه را بررسی کنید.',
        )
      })

      mapRef.current = map

      if (point) {
        window.setTimeout(() => updateMarker(point), 0)
      }
    } catch (error) {
      setMapError(
        error instanceof Error
          ? error.message
          : 'راه‌اندازی نقشه نشان ناموفق بود.',
      )
    }

    return () => {
      markerRef.current?.remove()
      markerRef.current = null
      mapRef.current?.remove()
      mapRef.current = null
    }
    // Map is deliberately initialized only after the property load/key change.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mapApiKey, propertyQuery.isLoading])

  useEffect(() => {
    if (point && mapRef.current) {
      updateMarker(point)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [point?.latitude, point?.longitude])

  const useSearchResult = (result: GeoSearchResult) => {
    const selected = {
      latitude: result.latitude,
      longitude: result.longitude,
    }

    updateMarker(selected, true)
    setReverse(
      result.address
        ? {
            formattedAddress: result.address,
            city: result.city ?? null,
            neighborhood: result.neighborhood ?? null,
          }
        : null,
    )
    reverseMutation.mutate(selected)
    setSearchResults([])
  }

  const browserLocation = () => {
    if (!navigator.geolocation) {
      setMapError('مرورگر این دستگاه Geolocation را پشتیبانی نمی‌کند.')
      return
    }

    navigator.geolocation.getCurrentPosition(
      (position) => {
        const selected = {
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
        }
        updateMarker(selected, true)
        reverseMutation.mutate(selected)
      },
      () => {
        setMapError(
          'دسترسی به موقعیت مرورگر داده نشد یا موقعیت قابل دریافت نبود.',
        )
      },
      {
        enableHighAccuracy: true,
        timeout: 10_000,
      },
    )
  }

  if (propertyQuery.isLoading) {
    return (
      <Card variant="outlined">
        <CardContent>
          <Stack
            minHeight={320}
            alignItems="center"
            justifyContent="center"
          >
            <CircularProgress />
          </Stack>
        </CardContent>
      </Card>
    )
  }

  if (propertyQuery.isError) {
    return (
      <Alert severity="error">
        دریافت اطلاعات ملک برای نقشه ناموفق بود.
      </Alert>
    )
  }

  if (!propertyQuery.data) {
    return (
      <Alert severity="warning">
        ابتدا در تب «مشخصات ملک» اطلاعات ملک را ذخیره کنید؛ سپس
        امکان ثبت موقعیت مکانی فعال می‌شود.
      </Alert>
    )
  }

  return (
    <Stack spacing={2}>
      {!mapApiKey && (
        <Alert severity="warning">
          کلید Web Map نشان تنظیم نشده است. در فایل
          <strong> frontend/.env.local </strong>
          مقدار
          <strong> VITE_NESHAN_MAP_API_KEY </strong>
          را قرار دهید و Vite را Restart کنید.
        </Alert>
      )}

      {geoStatus.data && !geoStatus.data.providerAvailable && (
        <Alert severity="info">
          نقشه قابل نمایش است، اما جستجوی آدرس و Reverse Geocoding
          در Backend غیرفعال است. متغیرهای
          <strong> NESHAN_ENABLED=true </strong>
          و
          <strong> NESHAN_SERVICE_API_KEY </strong>
          را برای Backend تنظیم کنید.
        </Alert>
      )}

      {mapError && (
        <Alert severity="error" onClose={() => setMapError(null)}>
          {mapError}
        </Alert>
      )}

      <Card variant="outlined">
        <CardContent>
          <Stack spacing={2}>
            <Stack
              direction={{ xs: 'column', md: 'row' }}
              spacing={1}
            >
              <TextField
                fullWidth
                label="جستجوی آدرس یا مکان"
                placeholder="مثلاً: تهران، پیروزی، خیابان پنجم"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                onKeyDown={(event) => {
                  if (
                    event.key === 'Enter' &&
                    query.trim() &&
                    geoStatus.data?.providerAvailable
                  ) {
                    searchMutation.mutate()
                  }
                }}
              />

              <Button
                variant="contained"
                startIcon={<SearchRoundedIcon />}
                disabled={
                  !query.trim() ||
                  searchMutation.isPending ||
                  !geoStatus.data?.providerAvailable
                }
                onClick={() => searchMutation.mutate()}
                sx={{ minWidth: 120 }}
              >
                جستجو
              </Button>

              <Button
                variant="outlined"
                startIcon={<MyLocationRoundedIcon />}
                onClick={browserLocation}
                sx={{ minWidth: 150 }}
              >
                موقعیت من
              </Button>
            </Stack>

            {searchMutation.isError && (
              <Alert severity="error">
                {searchMutation.error instanceof Error
                  ? searchMutation.error.message
                  : 'جستجوی نشان ناموفق بود.'}
              </Alert>
            )}

            {searchResults.length > 0 && (
              <Box
                sx={{
                  border: 1,
                  borderColor: 'divider',
                  borderRadius: 2,
                  maxHeight: 260,
                  overflow: 'auto',
                }}
              >
                <List disablePadding>
                  {searchResults.map((result, index) => (
                    <ListItemButton
                      key={`${result.latitude}-${result.longitude}-${index}`}
                      divider={index < searchResults.length - 1}
                      onClick={() => useSearchResult(result)}
                    >
                      <ListItemText
                        primary={result.title || result.address || 'نتیجه نشان'}
                        secondary={[
                          result.address,
                          result.neighborhood,
                          result.city,
                        ]
                          .filter(Boolean)
                          .join(' • ')}
                      />
                    </ListItemButton>
                  ))}
                </List>
              </Box>
            )}

            <Box
              ref={mapContainerRef}
              sx={{
                height: { xs: 420, md: 560 },
                borderRadius: 2,
                overflow: 'hidden',
                bgcolor: 'grey.100',
                border: 1,
                borderColor: 'divider',
              }}
            />

            <Typography variant="caption" color="text.secondary">
              روی نقشه کلیک کنید یا Marker را جابه‌جا کنید تا مختصات
              جدید انتخاب شود.
            </Typography>
          </Stack>
        </CardContent>
      </Card>

      <Card variant="outlined">
        <CardContent>
          <Stack spacing={2}>
            <Stack
              direction={{ xs: 'column', md: 'row' }}
              justifyContent="space-between"
              gap={2}
            >
              <div>
                <Typography variant="h6" fontWeight={700}>
                  موقعیت انتخاب‌شده
                </Typography>
                <Typography color="text.secondary" variant="body2">
                  مختصات در Property ذخیره و توسط Trigger دیتابیس به
                  PostGIS geography(Point, 4326) همگام می‌شود.
                </Typography>
              </div>

              {point && (
                <Chip
                  icon={<LocationOnRoundedIcon />}
                  label={`${point.latitude.toFixed(7)}, ${point.longitude.toFixed(7)}`}
                  variant="outlined"
                />
              )}
            </Stack>

            <Divider />

            {reverseMutation.isPending && (
              <Typography color="text.secondary">
                در حال دریافت نشانی از نشان...
              </Typography>
            )}

            {reverse && (
              <Stack spacing={0.5}>
                <Typography fontWeight={700}>
                  {reverse.formattedAddress || 'نشانی کامل در پاسخ موجود نبود.'}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {[
                    reverse.province,
                    reverse.city,
                    reverse.district,
                    reverse.neighborhood,
                    reverse.routeName,
                    reverse.place,
                  ]
                    .filter(Boolean)
                    .join(' • ')}
                </Typography>
              </Stack>
            )}

            {reverseMutation.isError && (
              <Alert severity="warning">
                مختصات انتخاب شد، ولی Reverse Geocoding نشان انجام نشد.
                همچنان می‌توانید خود مختصات را ذخیره کنید.
              </Alert>
            )}

            {saveLocation.isSuccess && (
              <Alert severity="success">
                موقعیت ملک با موفقیت ذخیره شد.
              </Alert>
            )}

            {saveLocation.isError && (
              <Alert severity="error">
                {saveLocation.error instanceof Error
                  ? saveLocation.error.message
                  : 'ذخیره موقعیت ناموفق بود.'}
              </Alert>
            )}

            <Stack
              direction={{ xs: 'column', sm: 'row' }}
              justifyContent="flex-end"
              spacing={1}
            >
              <Button
                variant="outlined"
                disabled={
                  !point ||
                  !reverse ||
                  saveLocation.isPending
                }
                onClick={() =>
                  saveLocation.mutate({ applyAddress: true })
                }
              >
                ذخیره موقعیت + نشانی نشان
              </Button>

              <Button
                variant="contained"
                startIcon={<SaveRoundedIcon />}
                disabled={!point || saveLocation.isPending}
                onClick={() =>
                  saveLocation.mutate({ applyAddress: false })
                }
              >
                {saveLocation.isPending
                  ? 'در حال ذخیره...'
                  : 'ذخیره موقعیت'}
              </Button>
            </Stack>
          </Stack>
        </CardContent>
      </Card>
    </Stack>
  )
}
