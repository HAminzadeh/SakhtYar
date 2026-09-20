export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
  ) {
    super(message)
  }
}

let refreshPromise: Promise<boolean> | null = null

function cookie(name: string) {
  const prefix = `${name}=`
  const value = document.cookie
    .split(';')
    .map((part) => part.trim())
    .find((part) => part.startsWith(prefix))
    ?.slice(prefix.length)

  return value ? decodeURIComponent(value) : null
}

function unsafe(method: string) {
  return !['GET', 'HEAD', 'OPTIONS'].includes(method.toUpperCase())
}

async function ensureCsrf() {
  let token = cookie('XSRF-TOKEN')
  if (token) return token

  const response = await fetch('/api/v1/auth/csrf', {
    credentials: 'include',
  })

  if (!response.ok) {
    return null
  }

  const body = (await response.json()) as { token?: string }
  token = body.token ?? cookie('XSRF-TOKEN')
  return token
}

async function refreshAccess() {
  if (!refreshPromise) {
    refreshPromise = (async () => {
      const csrf = await ensureCsrf()

      const response = await fetch('/api/v1/auth/refresh', {
        method: 'POST',
        credentials: 'include',
        headers: csrf ? { 'X-XSRF-TOKEN': csrf } : {},
      })

      return response.ok
    })().finally(() => {
      refreshPromise = null
    })
  }

  return refreshPromise
}

async function rawRequest(
  path: string,
  init: RequestInit,
  retryAfterRefresh: boolean,
) {
  const method = (init.method ?? 'GET').toUpperCase()
  const isFormData = init.body instanceof FormData
  const headers = new Headers(init.headers ?? {})

  if (!isFormData && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  if (unsafe(method) && !path.startsWith('/api/v1/auth/mobile/')) {
    const csrf = await ensureCsrf()
    if (csrf) {
      headers.set('X-XSRF-TOKEN', csrf)
    }
  }

  let response = await fetch(path, {
    ...init,
    credentials: 'include',
    headers,
  })

  const authEndpoint =
    path.startsWith('/api/v1/auth/login') ||
    path.startsWith('/api/v1/auth/register') ||
    path.startsWith('/api/v1/auth/refresh') ||
    path.startsWith('/api/v1/auth/mobile/')

  if (
    response.status === 401 &&
    retryAfterRefresh &&
    !authEndpoint
  ) {
    const refreshed = await refreshAccess()
    if (refreshed) {
      response = await fetch(path, {
        ...init,
        credentials: 'include',
        headers,
      })
    }
  }

  return response
}

export async function api<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const response = await rawRequest(path, init, true)

  if (response.status === 204) {
    return undefined as T
  }

  if (!response.ok) {
    let message = `خطای ${response.status}`

    try {
      const body = (await response.json()) as {
        message?: string
        detail?: string
        title?: string
      }

      message =
        body.message ??
        body.detail ??
        body.title ??
        message
    } catch {
      // Keep fallback.
    }

    throw new ApiError(message, response.status)
  }

  const contentType = response.headers.get('content-type') ?? ''

  if (!contentType.includes('application/json')) {
    return response as T
  }

  return response.json() as Promise<T>
}
