import { createContext, useContext, useSyncExternalStore } from 'react'
import { getActiveStrategy } from '../api/strategyApi.js'

// El valor interno del modo se conserva; la estrategia que viaja al back es la alternativa de GET /strategies/active.
export const ALTERNATIVE_VIEW = 'MOMENTUM'

const initial = { viewStrategy: 'MARKET_CURRENT', activeConfig: null, configStatus: 'idle', configError: null }

export function createStrategyViewStore(seed = {}) {
  let state = { ...initial, ...seed }
  let identity = null
  let generation = 0
  let pending = null
  const listeners = new Set()
  const emit = (patch) => {
    state = { ...state, ...patch }
    listeners.forEach((listener) => listener())
  }
  const store = {
    subscribe(listener) { listeners.add(listener); return () => listeners.delete(listener) },
    getSnapshot() { return state },
    setViewStrategy(value) {
      if (!['MARKET_CURRENT', ALTERNATIVE_VIEW].includes(value)) throw new TypeError('Modo de vista inválido')
      if (state.viewStrategy !== value) emit({ viewStrategy: value })
    },
    setIdentity(value) {
      if (identity === value) return
      identity = value
      generation += 1
      pending = null
      emit({ ...initial })
    },
    ensureConfig() { return pending ?? (state.configStatus === 'ready' ? Promise.resolve(state.activeConfig) : store.refreshConfig()) },
    async refreshConfig() {
      const current = ++generation
      emit({ configStatus: 'loading', configError: null })
      const promise = getActiveStrategy().then((config) => {
        if (generation === current) emit({ activeConfig: config, configStatus: 'ready' })
        return config
      }).catch((error) => {
        if (generation === current) emit({ configStatus: 'error', configError: error })
        return null
      }).finally(() => { if (pending === promise) pending = null })
      pending = promise
      return promise
    },
  }
  return store
}

const defaultStore = createStrategyViewStore()
const StrategyViewContext = createContext(defaultStore)

export function StrategyViewProvider({ store = defaultStore, children }) {
  return <StrategyViewContext.Provider value={store}>{children}</StrategyViewContext.Provider>
}

export function useStrategyView() {
  const store = useContext(StrategyViewContext)
  const state = useSyncExternalStore(store.subscribe, store.getSnapshot, store.getSnapshot)
  const alternativeCode = state.activeConfig?.alternative?.code ?? null
  const comparisonStrategy = state.viewStrategy === ALTERNATIVE_VIEW ? alternativeCode : null
  return { ...state, alternativeCode, comparisonStrategy, setViewStrategy: store.setViewStrategy, setIdentity: store.setIdentity, ensureConfig: store.ensureConfig, refreshConfig: store.refreshConfig }
}
