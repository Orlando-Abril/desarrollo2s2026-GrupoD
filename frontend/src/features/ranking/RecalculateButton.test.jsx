import { createRef } from 'react'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const hookState = { running: false, forbidden: false, notification: null, recalculate: vi.fn(), dismissNotification: vi.fn() }
vi.mock('./useRecalculate.js', () => ({ default: () => hookState }))
const { default: RecalculateButton } = await import('./RecalculateButton.jsx')

afterEach(cleanup)

describe('RecalculateButton', () => {
  beforeEach(() => {
    Object.assign(hookState, { running: false, forbidden: false, notification: null })
    hookState.recalculate.mockReset()
    hookState.dismissNotification.mockReset()
    vi.restoreAllMocks()
  })
  it('exposes the existing button for keyboard focus without running recalculation', () => {
    const buttonRef = createRef()
    render(<RecalculateButton buttonRef={buttonRef} />)
    buttonRef.current.focus()
    expect(document.activeElement).toBe(buttonRef.current)
    expect(hookState.recalculate).not.toHaveBeenCalled()
  })

  it('cancels without sending and confirms once', () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true)
    const { rerender } = render(<RecalculateButton onRecalculated={vi.fn()} />)
    fireEvent.click(screen.getByRole('button', { name: 'Recalcular cotizaciones' }))
    expect(hookState.recalculate).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'Recalcular cotizaciones' }))
    expect(hookState.recalculate).toHaveBeenCalledTimes(1)
    expect(confirm).toHaveBeenCalledWith('Se recalcularán todas las cotizaciones. ¿Querés continuar?')
    rerender(<RecalculateButton onRecalculated={vi.fn()} />)
  })

  it('shows accessible progress', () => {
    hookState.running = true
    render(<RecalculateButton onRecalculated={vi.fn()} />)
    const button = screen.getByRole('button', { name: 'Recalculando…' })
    expect(button.disabled).toBe(true)
    expect(button.getAttribute('aria-busy')).toBe('true')
  })

  it('renders every success field including zeroes and empty lists', () => {
    hookState.notification = { type: 'success', summary: {
      runId: 'run-123', strategies: [], skippedStrategies: [], playersProcessed: 0,
      playersFailed: 0, activeStrategy: 'OFFICIAL', marketValueUpdated: false,
    } }
    render(<RecalculateButton onRecalculated={vi.fn()} />)
    expect(screen.getByText(/run-123/)).toBeTruthy()
    expect(screen.getAllByText('Ninguna')).toHaveLength(2)
    expect(screen.getByText('Jugadores procesados: 0')).toBeTruthy()
    expect(screen.getByText('Jugadores fallidos: 0')).toBeTruthy()
    expect(screen.getByText('Valor de mercado actualizado: No')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Cerrar resumen' }))
    expect(hookState.dismissNotification).toHaveBeenCalled()
  })

  it('shows strategy counts with readable names instead of status codes', () => {
    hookState.notification = { type: 'success', summary: {
      runId: 'run-9', strategies: [{ code: 'MOMENTUM', version: 1, calculated: 3, noStats: 1, insufficientHistory: 2 }],
      skippedStrategies: [], playersProcessed: 6, playersFailed: 0, activeStrategy: 'OFFICIAL', marketValueUpdated: true,
    } }
    render(<RecalculateButton onRecalculated={vi.fn()} />)
    expect(screen.getByText('Momentum v1')).toBeTruthy()
    expect(screen.getByText('Calculadas: 3')).toBeTruthy()
    expect(screen.getByText('Sin estadísticas: 1')).toBeTruthy()
    expect(screen.getByText('Historial insuficiente: 2')).toBeTruthy()
    expect(screen.getByText('Estrategia activa: Oficial')).toBeTruthy()
    expect(document.body.textContent).not.toMatch(/INSUFFICIENT_HISTORY|NO_STATS|CALCULATED/)
  })

  it.each([
    ['in_progress', 'Ya hay un recálculo en curso'],
    ['network_error', 'No se pudo conectar con el servidor. Intentá de nuevo.'],
    ['unexpected_error', 'No se pudo recalcular las cotizaciones. Intentá de nuevo.'],
    ['forbidden', 'No tenés permisos para recalcular cotizaciones.'],
  ])('shows the safe message for %s', (type, message) => {
    hookState.notification = { type }
    hookState.forbidden = type === 'forbidden'
    render(<RecalculateButton onRecalculated={vi.fn()} />)
    expect(screen.getByText(message)).toBeTruthy()
    if (type === 'forbidden') expect(screen.queryByRole('button', { name: 'Recalcular cotizaciones' })).toBeNull()
  })
})
