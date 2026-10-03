export type ApiErrorDetail = {
  path: string
  status: number
  code?: string
  message: string
  fieldErrors?: Record<string, string>
}

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly code?: string,
    public readonly fieldErrors?: Record<string, string>,
    public readonly path?: string,
  ) {
    super(message)
  }
}

let refreshPromise: Promise<boolean> | null = null

function unsafe(method: string) {
  return !['GET', 'HEAD', 'OPTIONS'].includes(method.toUpperCase())
}

type CsrfHeader = {
  headerName: string
  token: string
}

function readCookie(name: string) {
  const prefix = `${encodeURIComponent(name)}=`
  const item = document.cookie
    .split(';')
    .map((value) => value.trim())
    .find((value) => value.startsWith(prefix))

  if (!item) return null

  return decodeURIComponent(item.substring(prefix.length))
}

async function csrfToken(): Promise<CsrfHeader | null> {
  // This GET forces Spring Security SPA CSRF support to create/refresh
  // the browser-readable XSRF-TOKEN cookie.
  const response = await fetch('/api/v1/auth/csrf', {
    method: 'GET',
    credentials: 'include',
    cache: 'no-store',
  })

  if (!response.ok) return null

  const body = (await response.json()) as {
    headerName?: string
    token?: string
  }

  // IMPORTANT:
  // With Spring Security SPA/BREACH support, the token returned through
  // CsrfToken#getToken() can be encoded for response rendering, while
  // JavaScript has access to the plain token stored in XSRF-TOKEN.
  // Unsafe requests must therefore echo the cookie token in the header.
  const rawCookieToken = readCookie('XSRF-TOKEN')

  if (!rawCookieToken) {
    console.error(
      '[SakhtYar CSRF] XSRF-TOKEN cookie was not available after /auth/csrf',
    )
    return null
  }

  return {
    headerName: body.headerName ?? 'X-XSRF-TOKEN',
    token: rawCookieToken,
  }
}

async function verifyAuthenticated() {
  const response = await fetch('/api/v1/auth/me', {
    method: 'GET',
    credentials: 'include',
    cache: 'no-store',
  })
  return response.ok
}

async function refreshAccess() {
  if (!refreshPromise) {
    refreshPromise = (async () => {
      const response = await fetch('/api/v1/auth/refresh', {
        method: 'POST',
        credentials: 'include',
        cache: 'no-store',
      })
      if (!response.ok) return false
      return verifyAuthenticated()
    })().finally(() => {
      refreshPromise = null
    })
  }
  return refreshPromise
}

async function performFetch(path: string, init: RequestInit) {
  const method = (init.method ?? 'GET').toUpperCase()
  const headers = new Headers(init.headers ?? {})
  const isFormData = init.body instanceof FormData

  if (!isFormData && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const csrfExempt =
    path.startsWith('/api/v1/auth/login') ||
    path.startsWith('/api/v1/auth/register') ||
    path.startsWith('/api/v1/auth/refresh') ||
    path.startsWith('/api/v1/auth/mobile/')

  if (unsafe(method) && !csrfExempt) {
    const csrf = await csrfToken()
    if (csrf) headers.set(csrf.headerName, csrf.token)
  }

  return fetch(path, {
    ...init,
    credentials: 'include',
    cache: 'no-store',
    headers,
  })
}

async function rawRequest(
  path: string,
  init: RequestInit,
  retryAfterRefresh: boolean,
) {
  const method = (init.method ?? 'GET').toUpperCase()
  const authEndpoint =
    path.startsWith('/api/v1/auth/login') ||
    path.startsWith('/api/v1/auth/register') ||
    path.startsWith('/api/v1/auth/refresh') ||
    path.startsWith('/api/v1/auth/mobile/')

  let response = await performFetch(path, init)

  if (response.status === 401 && retryAfterRefresh && !authEndpoint) {
    const refreshed = await refreshAccess()
    if (refreshed) response = await performFetch(path, init)
  }

  if (response.status === 403 && unsafe(method) && !authEndpoint) {
    response = await performFetch(path, init)
  }

  return response
}

function emitMutationError(
  path: string,
  init: RequestInit,
  error: ApiError,
) {
  const method = (init.method ?? 'GET').toUpperCase()
  if (!unsafe(method)) return

  if (
    path.startsWith('/api/v1/auth/login') ||
    path.startsWith('/api/v1/auth/register')
  ) return

  window.dispatchEvent(
    new CustomEvent<ApiErrorDetail>('sakhtyar:api-error', {
      detail: {
        path,
        status: error.status,
        code: error.code,
        message: error.message,
        fieldErrors: error.fieldErrors,
      },
    }),
  )
}

export async function api<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const response = await rawRequest(path, init, true)

  if (response.status === 204) return undefined as T

  if (!response.ok) {
    let message =
      response.status === 401
        ? 'Authentication is required or the session is not valid.'
        : response.status === 403
          ? 'Access denied or CSRF validation failed.'
          : `HTTP ${response.status}`

    let code: string | undefined
    let fieldErrors: Record<string, string> | undefined

    try {
      const body = (await response.json()) as {
        message?: string
        detail?: string
        title?: string
        code?: string
        fieldErrors?: Record<string, string>
      }
      message = body.message ?? body.detail ?? body.title ?? message
      code = body.code
      fieldErrors = body.fieldErrors
    } catch {
      // Keep fallback.
    }

    const error = new ApiError(
      message,
      response.status,
      code,
      fieldErrors,
      path,
    )

    emitMutationError(path, init, error)
    throw error
  }

  const contentType = response.headers.get('content-type') ?? ''
  if (!contentType.includes('application/json')) return response as T
  return response.json() as Promise<T>
}