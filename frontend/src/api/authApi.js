import { request } from './httpClient.js'

export async function register(data) {
  const result = await request('/auth/register', {
    method: 'POST',
    body: data,
    publicRequest: true,
  })
  return {
    id: result.id,
    username: result.username,
    email: result.email,
    balance: result.balance,
  }
}

// credentials: 'include' hace que el navegador guarde y mande la cookie HttpOnly refresh_token,
// que el back usa para renovar la sesión. JavaScript nunca ve su valor.
export function login(data) {
  return request('/auth/login', {
    method: 'POST',
    body: data,
    publicRequest: true,
    credentials: 'include',
  })
}

export function refreshSession() {
  return request('/auth/refresh', {
    method: 'POST',
    publicRequest: true,
    credentials: 'include',
  })
}

export function logout() {
  return request('/auth/logout', {
    method: 'POST',
    publicRequest: true,
    expectJson: false,
    credentials: 'include',
  })
}
