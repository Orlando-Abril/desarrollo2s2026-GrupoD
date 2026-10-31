import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getOpportunities } from '../../api/comparisonApi.js'
import { LEAGUES, POSITIONS } from '../../domain/catalogo.js'
import { formatCredits, formatResult } from '../portfolio/portfolioFormat.js'
import './OpportunitiesSection.css'

const percentFormatter = new Intl.NumberFormat('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const percent = (item) => Number(item.marketValue) > 0 && item.differencePercent != null
  ? `${Number(item.differencePercent) >= 0 ? '+' : ''}${percentFormatter.format(Number(item.differencePercent))} %`
  : 'No disponible'

function OpportunityList({ title, items, marketCode, alternativeCode }) {
  return <section><h3>{title}</h3>{items.length ? <ul className="opportunities-list">{items.map((item) => <li key={item.playerId}>
    <strong>{item.name}</strong>
    <span>Mercado actual ({marketCode}): {formatCredits(item.marketValue)}</span>
    <span>Cotización alternativa ({alternativeCode}): {formatCredits(item.alternativeQuote)}</span>
    <span>Diferencia: {formatResult(item.difference)} ({percent(item)})</span>
    <Link to={`/jugadores/${item.playerId}#operar`}>Operar</Link>
  </li>)}</ul> : <p>No hay oportunidades en esta lista.</p>}</section>
}

export default function OpportunitiesSection({ refreshKey = 0 }) {
  const [filters, setFilters] = useState({ league: '', position: '' })
  const [retry, setRetry] = useState(0)
  const [state, setState] = useState({ status: 'loading', data: null, key: '' })
  const key = `${filters.league}|${filters.position}|${retry}|${refreshKey}`
  const visible = state.key === key ? state : { status: 'loading', data: null }
  useEffect(() => {
    const controller = new AbortController()
    getOpportunities(filters, { signal: controller.signal })
      .then((data) => { if (!controller.signal.aborted) setState({ status: 'ready', data, key }) })
      .catch(() => { if (!controller.signal.aborted) setState({ status: 'error', data: null, key }) })
    return () => controller.abort()
  }, [filters, retry, refreshKey, key])
  return <section className="opportunities" aria-labelledby="opportunities-title">
    <h2 id="opportunities-title">Oportunidades</h2>
    <p>El precio para operar es siempre el de Mercado actual.</p>
    <div className="opportunities-filters">
      <label>Liga de oportunidades <select value={filters.league} onChange={(event) => setFilters((value) => ({ ...value, league: event.target.value }))}><option value="">Todas</option>{Object.entries(LEAGUES).map(([code, item]) => <option key={code} value={code}>{item.label}</option>)}</select></label>
      <label>Posición de oportunidades <select value={filters.position} onChange={(event) => setFilters((value) => ({ ...value, position: event.target.value }))}><option value="">Todas</option>{Object.entries(POSITIONS).map(([code, item]) => <option key={code} value={code}>{item.label}</option>)}</select></label>
    </div>
    {visible.status === 'loading' ? <p role="status">Cargando oportunidades…</p> : null}
    {visible.status === 'error' ? <div role="alert">No pudimos cargar las oportunidades. <button type="button" onClick={() => setRetry((value) => value + 1)}>Reintentar</button></div> : null}
    {visible.data ? <>
      <p>Mercado: {visible.data.marketStrategy?.code ?? 'sin informar'} · Estrategia activa: {visible.data.activeStrategy?.code ?? 'sin informar'} · Alternativa: {visible.data.alternativeStrategy?.code ?? 'sin informar'}</p>
      {visible.data.pendingRecalculation ? <p role="status">Hay un recálculo pendiente. Las oportunidades se actualizarán después del próximo recálculo.</p> : null}
      <div className="opportunities-columns"><OpportunityList title="Subvaluados" items={visible.data.undervalued ?? []} marketCode={visible.data.marketStrategy?.code ?? 'sin informar'} alternativeCode={visible.data.alternativeStrategy?.code ?? 'sin informar'} /><OpportunityList title="Sobrevaluados" items={visible.data.overvalued ?? []} marketCode={visible.data.marketStrategy?.code ?? 'sin informar'} alternativeCode={visible.data.alternativeStrategy?.code ?? 'sin informar'} /></div>
    </> : null}
  </section>
}
