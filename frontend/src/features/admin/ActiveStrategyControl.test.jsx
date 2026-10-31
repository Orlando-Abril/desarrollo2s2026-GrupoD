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

const ERROR_MESSAGES = { 403: /No tenés permisos/, 400: /no es válida/, network: /problema de red/ }

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

  it('explains why the change button is disabled while the active strategy is selected', async () => {
    getCurrentAccount.mockResolvedValue({ role: 'ADMIN' })
    getActiveStrategy.mockResolvedValue(activeOfficial)
    render(<StrategyViewProvider store={createStrategyViewStore()}><ActiveStrategyControl /></StrategyViewProvider>)
    await waitFor(() => expect(screen.getByText('Elegí otra estrategia para cambiarla')).toBeTruthy())
    expect(screen.getByRole('button', { name: 'Cambiar estrategia activa' }).disabled).toBe(true)
    fireEvent.change(screen.getByLabelText('Estrategia activa'), { target: { value: 'MOMENTUM' } })
    expect(screen.queryByText('Elegí otra estrategia para cambiarla')).toBeNull()
    expect(screen.getByRole('button', { name: 'Cambiar estrategia activa' }).disabled).toBe(false)
  })

  it('offers to recalculate right away after a confirmed change', async () => {
    getCurrentAccount.mockResolvedValue({ role: 'ADMIN' })
    getActiveStrategy.mockResolvedValue(activeOfficial)
    changeActiveStrategy.mockResolvedValue({ ...pendingMomentum, changed: true })
    vi.stubGlobal('confirm', vi.fn(() => true))
    const onRecalculate = vi.fn()
    render(<StrategyViewProvider store={createStrategyViewStore()}><ActiveStrategyControl onRecalculate={onRecalculate} /></StrategyViewProvider>)
    await waitFor(() => expect(screen.getByLabelText('Estrategia activa').value).toBe('OFFICIAL'))
    fireEvent.change(screen.getByLabelText('Estrategia activa'), { target: { value: 'MOMENTUM' } })
    fireEvent.click(screen.getByRole('button', { name: 'Cambiar estrategia activa' }))
    fireEvent.click(await screen.findByRole('button', { name: 'Recalcular ahora' }))
    expect(onRecalculate).toHaveBeenCalledTimes(1)
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
    await waitFor(() => expect(document.body.textContent).toMatch(ERROR_MESSAGES[kind]))
    if (kind !== 403) expect(getActiveStrategy.mock.calls.length).toBeGreaterThanOrEqual(2)
  })
})
