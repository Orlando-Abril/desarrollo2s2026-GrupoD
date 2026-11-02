const UNEXPECTED_MESSAGE = 'Unexpected server response'

const EMPTY_CONFIG = {
  getToken: () => null,
  onUnauthorized: () => {},
  refreshSession: async () => null,
}

let clientConfig = EMPTY_CONFIG

export class ApiError extends Error {
  constructor(status, code, message) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

export class NetworkError extends Error {
  constructor() {
    super('Network request failed')
    this.name = 'NetworkError'
  }
}

export function configureHttpClient(config) {
  clientConfig = {
    getToken: config.getToken ?? EMPTY_CONFIG.getToken,
    onUnauthorized: config.onUnauthorized ?? EMPTY_CONFIG.onUnauthorized,
    refreshSession: config.refreshSession ?? EMPTY_CONFIG.refreshSession,
  }
  const registered = clientConfig
  return () => {
    if (clientConfig === registered) {
      clientConfig = EMPTY_CONFIG
    }
  }
}

function buildUrl(path) {
  const baseUrl = import.meta.env.VITE_API_BASE_URL ?? ''
  return `${baseUrl.replace(/\/$/, '')}/${path.replace(/^\//, '')}`
}

function parseBody(text) {
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}

function buildHeaders(body, extraHeaders) {
  const headers = { ...extraHeaders }
  const token = clientConfig.getToken()
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (token) headers.Authorization = `Bearer ${token}`
  return headers
}

function validateResponse(response, payload, { expectJson }) {
  if (!response.ok) {
    const validError = payload && typeof payload.error === 'string' && typeof payload.message === 'string'
    throw new ApiError(
      response.status,
      validError ? payload.error : 'unexpected_error',
      validError ? payload.message : UNEXPECTED_MESSAGE,
    )
  }

  if (expectJson && payload === null) {
    throw new ApiError(response.status, 'unexpected_error', UNEXPECTED_MESSAGE)
  }
}

// Si un request protegido da 401 (por ejemplo, venció el JWT), se pide un JWT nuevo con la cookie
// de renovación y se reintenta una sola vez. Recién si eso falla se cierra la sesión.
export async function request(path, options = {}) {
  try {
    return await send(path, options)
  } catch (error) {
    if (!(error instanceof ApiError) || error.status !== 401 || options.publicRequest) throw error
    const renewedToken = await clientConfig.refreshSession()
    if (!renewedToken) {
      clientConfig.onUnauthorized()
      throw error
    }
    try {
      return await send(path, options)
    } catch (retryError) {
      if (retryError instanceof ApiError && retryError.status === 401) clientConfig.onUnauthorized()
      throw retryError
    }
  }
}

async function send(path, options) {
  const {
    method = 'GET',
    body,
    expectJson = true,
    headers: extraHeaders = {},
    signal,
    timeoutMs = 10_000,
    credentials,
  } = options
  const controller = new AbortController()
  const abortFromCaller = () => controller.abort()
  if (signal?.aborted) {
    controller.abort()
  } else {
    signal?.addEventListener('abort', abortFromCaller, { once: true })
  }
  const timeout = setTimeout(() => controller.abort(), timeoutMs)

  try {
    const response = await fetch(buildUrl(path), {
      method,
      headers: buildHeaders(body, extraHeaders),
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: controller.signal,
      ...(credentials ? { credentials } : {}),
    })
    const payload = parseBody(await response.text())
    validateResponse(response, payload, { expectJson })
    return payload
  } catch (error) {
    if (error instanceof ApiError) throw error
    throw new NetworkError()
  } finally {
    clearTimeout(timeout)
    signal?.removeEventListener('abort', abortFromCaller)
  }
}
