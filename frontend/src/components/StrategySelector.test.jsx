import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { marketMomentum, pendingMomentum } from '../test/strategyFixtures.js'
import { strategyStore } from '../test/strategyStore.js'
import { StrategyViewProvider, createStrategyViewStore } from '../context/StrategyViewContext.jsx'
import StrategySelector from './StrategySelector.jsx'
afterEach(cleanup)

describe('StrategySelector', () => {
  it('muestra Mercado actual y la alternativa con su nombre legible, sin sufijos de versión', () => {
    const store = strategyStore()
    render(<StrategyViewProvider store={store}><StrategySelector /></StrategyViewProvider>)
    const select = screen.getByRole('combobox', { name: /Ver con/i })
    expect(select.value).toBe('MARKET_CURRENT')
    expect(within(select).getAllByRole('option').map((option) => option.textContent)).toEqual(['Mercado actual', 'Momentum'])
    fireEvent.change(select, { target: { value: 'MOMENTUM' } })
    expect(store.getSnapshot().viewStrategy).toBe('MOMENTUM')
    expect(document.body.textContent).not.toMatch(/OFFICIAL|v1|pendiente/)
  })

  it('usa como segunda opción la alternativa informada por la configuración', () => {
    render(<StrategyViewProvider store={strategyStore({ activeConfig: marketMomentum })}><StrategySelector /></StrategyViewProvider>)
    expect(within(screen.getByRole('combobox', { name: /Ver con/i })).getAllByRole('option').map((option) => option.textContent)).toEqual(['Mercado actual', 'Oficial'])
  })

  it('indica el recálculo pendiente con un texto corto y la explicación en el title', () => {
    render(<StrategyViewProvider store={strategyStore({ activeConfig: pendingMomentum })}><StrategySelector /></StrategyViewProvider>)
    const pending = screen.getByText('Recálculo pendiente').closest('small')
    expect(pending.getAttribute('title')).toBe('La estrategia activa (Momentum) todavía no fijó el precio de mercado, que sigue con Oficial hasta el próximo recálculo.')
    expect(document.body.textContent).not.toContain('Estrategia activa')
  })

  it('deshabilita la alternativa mientras no se conoce la configuración', () => {
    const store = createStrategyViewStore()
    store.ensureConfig = vi.fn()
    render(<StrategyViewProvider store={store}><StrategySelector /></StrategyViewProvider>)
    expect(screen.getByRole('option', { name: 'Alternativa' }).disabled).toBe(true)
  })

  it('offers a retry when configuration cannot load', () => {
    const store = createStrategyViewStore({ configStatus: 'error', configError: new Error('offline') })
    store.ensureConfig = vi.fn()
    store.refreshConfig = vi.fn()
    render(<StrategyViewProvider store={store}><StrategySelector /></StrategyViewProvider>)
    fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(store.refreshConfig).toHaveBeenCalledTimes(1)
  })
})
