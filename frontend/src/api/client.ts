export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
  ) {
    super(message)
  }
}

export async function api<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const isFormData = init.body instanceof FormData

  const response = await fetch(path, {
    ...init,
    credentials: 'include',
    headers: {
      ...(isFormData ? {} : { 'Content-Type': 'application/json' }),
      ...(init.headers ?? {}),
    },
  })

  if (response.status === 204) {
    return undefined as T
  }

  if (!response.ok) {
    let message = `خطای ${response.status}`
    try {
      const body = (await response.json()) as { message?: string }
      message = body.message ?? message
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
