export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
  ) {
    super(message)
  }
}

let refreshPromise: Promise<boolean> | null = null

function unsafe(method: string) {
  return !['GET', 'HEAD', 'OPTIONS'].includes(method.toUpperCase())
}

async function csrfToken() {
  const response = await fetch('/api/v1/auth/csrf', {
    method: 'GET',
    credentials: 'include',
    cache: 'no-store',
  })

  if (!response.ok) {
    return null
  }

  const body = (await response.json()) as { token?: string }
  return body.token ?? null
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
    const token = await csrfToken()
    if (token) {
      headers.set('X-XSRF-TOKEN', token)
    }
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

  // After the backend ordering fix, 401 genuinely means authentication failed.
  if (
    response.status === 401 &&
    retryAfterRefresh &&
    !authEndpoint
  ) {
    const refreshed = await refreshAccess()
    if (refreshed) {
      response = await performFetch(path, init)
    }
  }

  // 403 now represents authorization/CSRF. One fresh-token retry is safe.
  if (
    response.status === 403 &&
    unsafe(method) &&
    !authEndpoint
  ) {
    response = await performFetch(path, init)
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
    let message =
      response.status === 401
        ? 'Authentication failed or the session expired.'
        : response.status === 403
          ? 'The request was rejected by authorization or CSRF protection.'
          : `HTTP ${response.status}`

    try {
      const body = (await response.json()) as {
        message?: string
        detail?: string
        title?: string
      }
      message = body.message ?? body.detail ?? body.title ?? message
    } catch {
      // Keep fallback message.
    }

    throw new ApiError(message, response.status)
  }

  const contentType = response.headers.get('content-type') ?? ''
  if (!contentType.includes('application/json')) {
    return response as T
  }

  return response.json() as Promise<T>
}