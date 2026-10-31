import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { activeOfficial, pendingMomentum } from '../../test/strategyFixtures.js'

const getCurrentAccount = vi.fn()
const changeActiveStrategy = vi.fn()
const getActiveStrategy = vi.fn()
vi.mock('../../api/portfolioApi.js', () => ({ getCurrentAccount }))
vi.mock('../../api/strategyApi.js', () => ({ changeActiveStrategy, getActiveStrategy }))
const { StrategyViewProvider, createStrategyViewStore } = await import('../../context/StrategyViewContext.jsx')
const { default: ActiveStrategyControl } = await import('./ActiveStrategyControl.jsx')

afterEach(() => { cleanup(); vi.unstubAllGlobals(); getCurrentAccount.mockReset(); changeActiveStrategy.mockReset(); getActiveStrategy.mockReset() })

describe('ActiveStrategyControl', () => {
  it('hides control for USER', async () => {
    getCurrentAccount.mockResolvedValue({ role: 'USER' })
    render(<StrategyViewProvider store={createStrategyViewStore()}><ActiveStrategyControl /></StrategyViewProvider>)
    await waitFor(() => expect(getCurrentAccount).toHaveBeenCalled())
    expect(screen.queryByLabelText('Estrategia activa')).toBeNull()
  })

  it('asks once, changes and explains delayed market update', async () => {
    getCurrentAccount.mockResolvedValue({ role: 'ADMIN' })
    getActiveStrategy.mockResolvedValue(activeOfficial)
    changeActiveStrategy.mockResolvedValue({ ...pendingMomentum, changed: true })
    vi.stubGlobal('confirm', vi.fn(() => true))
    const onChanged = vi.fn()
    render(<StrategyViewProvider store={createStrategyViewStore()}><ActiveStrategyControl onChanged={onChanged} /></StrategyViewProvider>)
    await waitFor(() => expect(screen.getByLabelText('Estrategia activa')).toBeTruthy())
    fireEvent.change(screen.getByLabelText('Estrategia activa'), { target: { value: 'MOMENTUM' } })
    fireEvent.click(screen.getByRole('button', { name: 'Cambiar estrategia activa' }))
    await waitFor(() => expect(changeActiveStrategy).toHaveBeenCalledWith('MOMENTUM'))
    expect(changeActiveStrategy).toHaveBeenCalledTimes(1)
    expect(document.body.textContent).toContain('próximo recálculo')
    expect(onChanged).toHaveBeenCalled()
    expect(getActiveStrategy.mock.calls.length).toBeGreaterThanOrEqual(2)
  })

  it('does not send PUT when confirmation is cancelled', async () => {
    getCurrentAccount.mockResolvedValue({ role: 'ADMIN' })
    getActiveStrategy.mockResolvedValue(activeOfficial)
    vi.stubGlobal('confirm', vi.fn(() => false))
    render(<StrategyViewProvider store={createStrategyViewStore()}><ActiveStrategyControl /></StrategyViewProvider>)
    await waitFor(() => expect(screen.getByLabelText('Estrategia activa').value).toBe('OFFICIAL'))
    fireEvent.change(screen.getByLabelText('Estrategia activa'), { target: { value: 'MOMENTUM' } })
    fireEvent.click(screen.getByRole('button', { name: 'Cambiar estrategia activa' }))
    expect(changeActiveStrategy).not.toHaveBeenCalled()
  })
  it('reports an idempotent change without starting recalculation', async () => {
    getCurrentAccount.mockResolvedValue({ role: 'ADMIN' })
    getActiveStrategy.mockResolvedValue(activeOfficial)
    changeActiveStrategy.mockResolvedValue({ ...activeOfficial, changed: false })
    vi.stubGlobal('confirm', vi.fn(() => true))
    render(<StrategyViewProvider store={createStrategyViewStore()}><ActiveStrategyControl /></StrategyViewProvider>)
    await waitFor(() => expect(screen.getByLabelText('Estrategia activa').value).toBe('OFFICIAL'))
    fireEvent.change(screen.getByLabelText('Estrategia activa'), { target: { value: 'MOMENTUM' } })
    fireEvent.click(screen.getByRole('button', { name: 'Cambiar estrategia activa' }))
    await waitFor(() => expect(document.body.textContent).toContain('ya tenía ese valor'))
  })

  it.each([403, 400, 'network'])('explains and reconciles error %s', async (kind) => {
    getCurrentAccount.mockResolvedValue({ role: 'ADMIN' })
    getActiveStrategy.mockResolvedValue(activeOfficial)
    changeActiveStrategy.mockRejectedValue(Object.assign(new Error('failed'), { status: kind }))
    vi.stubGlobal('confirm', vi.fn(() => true))
    render(<StrategyViewProvider store={createStrategyViewStore()}><ActiveStrategyControl /></StrategyViewProvider>)
    await waitFor(() => expect(screen.getByLabelText('Estrategia activa').value).toBe('OFFICIAL'))
    fireEvent.change(screen.getByLabelText('Estrategia activa'), { target: { value: 'MOMENTUM' } })
    fireEvent.click(screen.getByRole('button', { name: 'Cambiar estrategia activa' }))
    await waitFor(() => expect(changeActiveStrategy).toHaveBeenCalledTimes(1))
    await waitFor(() => expect(document.body.textContent).toMatch(kind === 403 ? /No tenés permisos/ : kind === 400 ? /no es válida/ : /problema de red/))
    if (kind !== 403) expect(getActiveStrategy.mock.calls.length).toBeGreaterThanOrEqual(2)
  })
})
