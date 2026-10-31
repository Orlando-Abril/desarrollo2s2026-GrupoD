import { useEffect } from 'react'
import { useStrategyView } from '../context/StrategyViewContext.jsx'
import './StrategySelector.css'

export default function StrategySelector({ identity }) {
  const { viewStrategy, setViewStrategy, setIdentity, activeConfig, configStatus, ensureConfig, refreshConfig } = useStrategyView()
  useEffect(() => { if (identity !== undefined) setIdentity(identity); void ensureConfig() }, [identity, setIdentity, ensureConfig])
  const marketCode = activeConfig?.marketStrategy?.code
  const activeCode = activeConfig?.active?.code
  const momentumLabel = [activeConfig?.active, activeConfig?.alternative].find((strategy) => strategy?.code === 'MOMENTUM')?.label
  return (
    <div className="strategy-selector">
      <label htmlFor="strategy-view">Ver con:</label>
      <select id="strategy-view" value={viewStrategy} onChange={(event) => setViewStrategy(event.target.value)}>
        <option value="MARKET_CURRENT">Mercado actual</option>
        <option value="MOMENTUM">Momentum{momentumLabel ? ` (${momentumLabel})` : ''}</option>
      </select>
      {configStatus === 'loading' ? <small role="status">Cargando estrategia…</small> : null}
      {configStatus === 'error' ? <button type="button" onClick={() => { void refreshConfig() }}>No se pudo cargar la estrategia. Reintentar</button> : null}
      {activeConfig ? <small>Mercado actual: {marketCode ?? 'sin origen informado'}. Estrategia activa: {activeCode ?? 'sin informar'}{activeConfig.pendingRecalculation ? ' · recálculo pendiente' : ''}.</small> : null}
    </div>
  )
}
