import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { activeOfficial, pendingMomentum } from '../test/strategyFixtures.js'
import { StrategyViewProvider, createStrategyViewStore } from '../context/StrategyViewContext.jsx'
import StrategySelector from './StrategySelector.jsx'
afterEach(cleanup)

describe('StrategySelector', () => {
  it('muestra Mercado actual y el origen real, y permite comparar sin cambiar el mercado', () => {
    const store = createStrategyViewStore()
    store.setIdentity('a')
    store.refreshConfig = async () => activeOfficial
    render(<StrategyViewProvider store={store}><StrategySelector /></StrategyViewProvider>)
    expect(screen.getByRole('combobox', { name: /Ver con/i }).value).toBe('MARKET_CURRENT')
    fireEvent.change(screen.getByRole('combobox', { name: /Ver con/i }), { target: { value: 'MOMENTUM' } })
    expect(store.getSnapshot().viewStrategy).toBe('MOMENTUM')
  })

  it('distingue configuración pendiente del origen del mercado', () => {
    const store = createStrategyViewStore()
    const snapshot = { viewStrategy: 'MARKET_CURRENT', activeConfig: pendingMomentum, configStatus: 'ready', configError: null }
    store.getSnapshot = () => snapshot
    render(<StrategyViewProvider store={store}><StrategySelector /></StrategyViewProvider>)
    expect(screen.getByText(/OFFICIAL/).textContent).toContain('OFFICIAL')
    expect(screen.getByText(/pendiente/i).textContent).toContain('pendiente')
  })
  it('offers a retry when configuration cannot load', () => {
    const store = createStrategyViewStore()
    const snapshot = { viewStrategy: 'MARKET_CURRENT', activeConfig: null, configStatus: 'error', configError: new Error('offline') }
    store.getSnapshot = () => snapshot
    store.ensureConfig = vi.fn()
    store.refreshConfig = vi.fn()
    render(<StrategyViewProvider store={store}><StrategySelector /></StrategyViewProvider>)
    fireEvent.click(screen.getByRole('button', { name: /Reintentar/ }))
    expect(store.refreshConfig).toHaveBeenCalledTimes(1)
  })
})
