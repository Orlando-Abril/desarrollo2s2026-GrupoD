import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getOpportunities } from '../../api/comparisonApi.js'
import EstadoPanel from '../../components/EstadoPanel.jsx'
import Field from '../../components/Field.jsx'
import { LEAGUES, POSITIONS } from '../../domain/catalogo.js'
import { differenceTone, formatSignedCredits, formatSignedPercent, strategyName } from '../../utils/comparison.js'
import { formatCredits } from '../../utils/format.js'
import './RankingFilters.css'
import './OpportunitiesSection.css'

const percent = (item) => (Number(item.marketValue) > 0 ? formatSignedPercent(item.differencePercent) : 'No disponible')

function OpportunityList({ title, items, marketName, alternativeName }) {
  return <section className="opportunities-group"><h3>{title}</h3>{items.length ? <ul className="opportunities-list">{items.map((item) => <li key={item.playerId} className="opportunity-card">
    <strong>{item.name}</strong>
    <span>Mercado actual ({marketName}): {formatCredits(item.marketValue)}</span>
    <span>Cotización alternativa ({alternativeName}): {formatCredits(item.alternativeQuote)}</span>
    <span>Diferencia: <span className={`opportunity-card__difference opportunity-card__difference--${differenceTone(item.difference)}`}>{formatSignedCredits(item.difference)} <span>({percent(item)})</span></span></span>
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
  const marketName = strategyName(visible.data?.marketStrategy?.code)
  const alternativeName = strategyName(visible.data?.alternativeStrategy?.code)
  return <section className="opportunities" aria-labelledby="opportunities-title">
    <h2 id="opportunities-title">Oportunidades</h2>
    <p className="opportunities__note">El precio para operar es siempre el de Mercado actual.</p>
    <section className="ranking-filters" aria-label="Filtros de oportunidades">
      <Field id="opportunities-league" as="select" label="Liga de oportunidades" value={filters.league} onChange={(event) => setFilters((value) => ({ ...value, league: event.target.value }))}>
        <option value="">Todas</option>
        {Object.entries(LEAGUES).map(([code, item]) => <option key={code} value={code}>{item.label}</option>)}
      </Field>
      <Field id="opportunities-position" as="select" label="Posición de oportunidades" value={filters.position} onChange={(event) => setFilters((value) => ({ ...value, position: event.target.value }))}>
        <option value="">Todas</option>
        {Object.entries(POSITIONS).map(([code, item]) => <option key={code} value={code}>{item.label}</option>)}
      </Field>
    </section>
    {visible.status === 'loading' ? <p role="status">Cargando oportunidades…</p> : null}
    {visible.status === 'error' ? <EstadoPanel stamp="Sin conexión" stampVariant="error" title="No pudimos cargar las oportunidades" action={{ label: 'Reintentar', onClick: () => setRetry((value) => value + 1) }}>Intentá de nuevo en unos segundos.</EstadoPanel> : null}
    {visible.data ? <>
      <p className="opportunities__note">Mercado: {marketName} · Estrategia activa: {strategyName(visible.data.activeStrategy?.code)} · Alternativa: {alternativeName}</p>
      {visible.data.pendingRecalculation ? <p role="status" className="opportunities__note">Hay un recálculo pendiente. Las oportunidades se actualizarán después del próximo recálculo.</p> : null}
      <div className="opportunities-columns"><OpportunityList title="Subvaluados" items={visible.data.undervalued ?? []} marketName={marketName} alternativeName={alternativeName} /><OpportunityList title="Sobrevaluados" items={visible.data.overvalued ?? []} marketName={marketName} alternativeName={alternativeName} /></div>
    </> : null}
  </section>
}
