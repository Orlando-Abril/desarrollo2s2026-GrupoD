import { useCallback, useRef } from 'react'
import EstadoPanel from '../components/EstadoPanel.jsx'
import Pager from '../features/album/Pager.jsx'
import RankingFilters from '../features/ranking/RankingFilters.jsx'
import RankingTable from '../features/ranking/RankingTable.jsx'
import useRanking from '../features/ranking/useRanking.js'
import './RankingPage.css'

export default function RankingPage() {
  const ranking = useRanking()
  const resultsRef = useRef(null)

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
      <header className="page-heading">
        <h1>Ranking</h1>
        <p>Jugadores ordenados por su cotización actual</p>
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
    </div>
  )
}
