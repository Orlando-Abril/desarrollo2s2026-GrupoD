import { renderHook, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

const getCurrentAccountMock = vi.fn()
vi.mock('../../api/portfolioApi.js', () => ({ getCurrentAccount: (...args) => getCurrentAccountMock(...args) }))

const { default: useAccountRole } = await import('./useAccountRole.js')

describe('useAccountRole', () => {
  it('starts unknown and resolves the role from /auth/me', async () => {
    getCurrentAccountMock.mockResolvedValue({ role: 'ADMIN' })
    const { result } = renderHook(() => useAccountRole())
    expect(result.current).toBeNull()
    await waitFor(() => expect(result.current).toBe('ADMIN'))
  })

  it('reports UNKNOWN when the account cannot be read', async () => {
    getCurrentAccountMock.mockRejectedValue(new Error('down'))
    const { result } = renderHook(() => useAccountRole())
    await waitFor(() => expect(result.current).toBe('UNKNOWN'))
  })
})
