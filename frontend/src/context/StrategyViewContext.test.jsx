import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { activeOfficial } from '../test/strategyFixtures.js'

const getActiveStrategy = vi.fn()
vi.mock('../api/strategyApi.js', () => ({ getActiveStrategy }))
const { StrategyViewProvider, createStrategyViewStore, useStrategyView } = await import('./StrategyViewContext.jsx')

function Consumer({ name }) {
  const state = useStrategyView()
  return <div><span>{name}: {state.viewStrategy} {state.configStatus}</span><button onClick={() => state.setViewStrategy('MOMENTUM')}>Momentum {name}</button><button onClick={() => state.refreshConfig()}>Recargar {name}</button></div>
}

describe('StrategyViewContext', () => {
  it('comparte la vista sin cambiar la estrategia activa', () => {
    const store = createStrategyViewStore()
    render(<StrategyViewProvider store={store}><Consumer name="A" /><Consumer name="B" /></StrategyViewProvider>)
    expect(screen.getAllByText(/MARKET_CURRENT idle/)).toHaveLength(2)
    fireEvent.click(screen.getByRole('button', { name: 'Momentum A' }))
    expect(screen.getAllByText(/MOMENTUM idle/)).toHaveLength(2)
    expect(getActiveStrategy).not.toHaveBeenCalled()
  })

  it('carga, refresca y reinicia por identidad', async () => {
    getActiveStrategy.mockResolvedValue(activeOfficial)
    const store = createStrategyViewStore()
    store.setIdentity('token-a')
    await store.refreshConfig()
    expect(store.getSnapshot().activeConfig).toBe(activeOfficial)
    store.setViewStrategy('MOMENTUM')
    store.setIdentity('token-b')
    expect(store.getSnapshot()).toMatchObject({ viewStrategy: 'MARKET_CURRENT', configStatus: 'idle', activeConfig: null })
  })

  it('permite reintentar tras error', async () => {
    const store = createStrategyViewStore()
    getActiveStrategy.mockRejectedValueOnce(new Error('red')).mockResolvedValueOnce(activeOfficial)
    await store.refreshConfig()
    expect(store.getSnapshot().configStatus).toBe('error')
    await store.refreshConfig()
    await waitFor(() => expect(store.getSnapshot().configStatus).toBe('ready'))
  })
})
