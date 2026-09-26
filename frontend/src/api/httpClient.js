const UNEXPECTED_MESSAGE = 'Unexpected server response'

let clientConfig = {
  getToken: () => null,
  onUnauthorized: () => {},
}

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
    getToken: config.getToken ?? (() => null),
    onUnauthorized: config.onUnauthorized ?? (() => {}),
  }
  const registered = clientConfig
  return () => {
    if (clientConfig === registered) {
      clientConfig = { getToken: () => null, onUnauthorized: () => {} }
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

export async function request(path, options = {}) {
  const {
    method = 'GET',
    body,
    publicRequest = false,
    expectJson = true,
    headers: extraHeaders = {},
  } = options
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), 10000)
  const headers = { ...extraHeaders }
  const token = clientConfig.getToken()

  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (token) headers.Authorization = `Bearer ${token}`

  try {
    const response = await fetch(buildUrl(path), {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: controller.signal,
    })
    const payload = parseBody(await response.text())

    if (!response.ok) {
      if (response.status === 401 && !publicRequest) clientConfig.onUnauthorized()
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
    return payload
  } catch (error) {
    if (error instanceof ApiError) throw error
    throw new NetworkError()
  } finally {
    clearTimeout(timeout)
  }
}
