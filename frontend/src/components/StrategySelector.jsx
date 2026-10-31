import { useEffect } from 'react'
import { ALTERNATIVE_VIEW, useStrategyView } from '../context/StrategyViewContext.jsx'
import { strategyName } from '../utils/comparison.js'
import './StrategySelector.css'

export default function StrategySelector({ identity }) {
  const { viewStrategy, setViewStrategy, setIdentity, activeConfig, alternativeCode, configStatus, ensureConfig, refreshConfig } = useStrategyView()
  useEffect(() => {
    if (identity !== undefined) {
      setIdentity(identity)
    }
    void ensureConfig()
  }, [identity, setIdentity, ensureConfig])
  const pendingTitle = `La estrategia activa (${strategyName(activeConfig?.active?.code)}) todavía no fijó el precio de mercado, que sigue con ${strategyName(activeConfig?.marketStrategy?.code)} hasta el próximo recálculo.`
  return (
    <div className="strategy-selector">
      <label htmlFor="strategy-view">Ver con:</label>
      <select id="strategy-view" value={viewStrategy} onChange={(event) => setViewStrategy(event.target.value)}>
        <option value="MARKET_CURRENT">Mercado actual</option>
        <option value={ALTERNATIVE_VIEW} disabled={!alternativeCode}>{alternativeCode ? strategyName(alternativeCode) : 'Alternativa'}</option>
      </select>
      {configStatus === 'loading' ? <small role="status">Cargando…</small> : null}
      {configStatus === 'error' ? <button type="button" title="No se pudo cargar la estrategia" onClick={() => { void refreshConfig() }}>Reintentar</button> : null}
      {activeConfig?.pendingRecalculation ? <small className="strategy-selector__pending" title={pendingTitle}><span aria-hidden="true">⟳</span> <span className="strategy-selector__pending-text">Recálculo pendiente</span></small> : null}
    </div>
  )
}
