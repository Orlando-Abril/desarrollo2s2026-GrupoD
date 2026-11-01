import { useCallback, useRef, useState } from 'react'
import EstadoPanel from '../components/EstadoPanel.jsx'
import Pager from '../features/album/Pager.jsx'
import RankingFilters from '../features/ranking/RankingFilters.jsx'
import RecalculateButton from '../features/ranking/RecalculateButton.jsx'
import RankingTable from '../features/ranking/RankingTable.jsx'
import useRanking from '../features/ranking/useRanking.js'
import OpportunitiesSection from '../features/ranking/OpportunitiesSection.jsx'
import ActiveStrategyControl from '../features/admin/ActiveStrategyControl.jsx'
import { useStrategyView } from '../context/StrategyViewContext.jsx'
import './RankingPage.css'

export default function RankingPage() {
  const ranking = useRanking()
  const resultsRef = useRef(null)
  const recalculateRef = useRef(null)
  const [opportunityRefresh, setOpportunityRefresh] = useState(0)
  const { refreshConfig } = useStrategyView()
  const refreshOpportunities = useCallback(() => setOpportunityRefresh((value) => value + 1), [])
  const afterRecalculate = useCallback(() => {
    ranking.reload()
    refreshOpportunities()
    void refreshConfig()
  }, [ranking, refreshOpportunities, refreshConfig])

  const changePage = useCallback((nextPage) => {
    if (nextPage === ranking.page || nextPage < 1 || nextPage > ranking.totalPages) return
    ranking.setPage(nextPage)
    resultsRef.current?.scrollIntoView({ block: 'start' })
  }, [ranking])

  let content
  if (ranking.status === 'loading') {
    content = <div className="ranking-loading" aria-busy="true">Cargando ranking…</div>
  } else if (ranking.status === 'error') {
    content = (
      <EstadoPanel
        stamp="Sin conexión"
        stampVariant="error"
        title="No pudimos abrir el ranking"
        action={{ label: 'Reintentar', onClick: ranking.retry }}
      >
        No se pudo conectar con el servidor. Intentá de nuevo en unos segundos.
      </EstadoPanel>
    )
  } else if (ranking.total === 0) {
    content = (
      <EstadoPanel stamp="Ranking vacío" title="Todavía no hay cotizaciones">
        Aún no hay valores disponibles para estos criterios. Probá de nuevo más tarde.
      </EstadoPanel>
    )
  } else {
    content = <RankingTable items={ranking.items} />
  }

  return (
    <div className="page-content ranking-page">
      <header className="page-heading ranking-page__heading">
        <div>
          <h1>Ranking</h1>
          <p>Jugadores ordenados por su cotización actual</p>
        </div>
        <div className="ranking-page__admin-actions">
          <ActiveStrategyControl onChanged={refreshOpportunities} onRecalculate={() => recalculateRef.current?.click()} />
          <RecalculateButton buttonRef={recalculateRef} onRecalculated={afterRecalculate} />
        </div>
      </header>
      <RankingFilters
        value={ranking.filters}
        onChange={ranking.changeFilter}
        effectiveStrategy={ranking.effectiveStrategy}
      />
      <div className="ranking-results" ref={resultsRef} aria-live="polite">{content}</div>
      {ranking.status === 'ready' && ranking.total > 0 ? (
        <Pager
          page={ranking.page}
          totalPages={ranking.totalPages}
          from={ranking.from}
          to={ranking.to}
          total={ranking.total}
          window={ranking.pageWindow}
          onPageChange={changePage}
        />
      ) : null}
      <OpportunitiesSection refreshKey={opportunityRefresh} />
    </div>
  )
}
